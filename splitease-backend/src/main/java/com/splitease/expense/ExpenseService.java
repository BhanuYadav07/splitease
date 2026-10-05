package com.splitease.expense;

import com.splitease.exception.AccessDeniedException;
import com.splitease.exception.InvalidRequestException;
import com.splitease.exception.ResourceNotFoundException;
import com.splitease.group.Group;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupMemberRepository;
import com.splitease.group.GroupService;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;

    public List<Expense> listExpenses(UUID groupId, UUID requestingUserId) {
        groupService.requireMembership(groupId, requestingUserId);
        return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    public Expense getExpense(UUID groupId, UUID expenseId, UUID requestingUserId) {
        groupService.requireMembership(groupId, requestingUserId);
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        if (!expense.getGroup().getId().equals(groupId)) {
            // Prevents accessing another group's expense by swapping the expense ID in the URL.
            throw new AccessDeniedException("This expense does not belong to the specified group.");
        }
        return expense;
    }

    /**
     * Functional requirements 3-6 and 11: validates the payer/participants
     * belong to the group, no duplicates, amount > 0, and that the chosen
     * split type's shares reconcile exactly to the total. The whole
     * operation is transactional (requirement 12) - if validation or save
     * fails partway through, nothing is persisted.
     */
    @Transactional
    public Expense createExpense(UUID groupId, UUID requestingUserId, ExpenseDtos.CreateExpenseRequest request) {
        groupService.requireMembership(groupId, requestingUserId);
        Group group = groupService.getGroup(groupId);

        Set<UUID> groupMemberIds = groupService.getMembers(groupId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        validateParticipants(request.payerId(), request.shares(), groupMemberIds);

        User payer = userRepository.findById(request.payerId())
                .orElseThrow(() -> new ResourceNotFoundException("Payer not found."));
        User creator = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Expense expense = new Expense(group, request.description(), request.amount(), payer, request.splitType(), creator);

        Map<UUID, BigDecimal> resolvedShares = resolveShares(request);
        for (ExpenseDtos.ShareInput share : request.shares()) {
            User participant = userRepository.findById(share.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + share.userId()));
            expense.addSplit(new ExpenseSplit(participant, resolvedShares.get(share.userId())));
        }

        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense updateExpense(UUID groupId, UUID expenseId, UUID requestingUserId, ExpenseDtos.CreateExpenseRequest request) {
        Expense existing = getExpense(groupId, expenseId, requestingUserId);
        requireEditPermission(existing, requestingUserId);

        // Simplest correct approach: recreate the splits under the same transaction,
        // so a mid-way validation failure rolls the whole update back.
        Set<UUID> groupMemberIds = groupService.getMembers(groupId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        validateParticipants(request.payerId(), request.shares(), groupMemberIds);

        User payer = userRepository.findById(request.payerId())
                .orElseThrow(() -> new ResourceNotFoundException("Payer not found."));

        existing.setDescription(request.description());
        existing.setAmount(request.amount());
        existing.setPayer(payer);
        existing.setSplitType(request.splitType());
        existing.getSplits().clear();

        Map<UUID, BigDecimal> resolvedShares = resolveShares(request);
        for (ExpenseDtos.ShareInput share : request.shares()) {
            User participant = userRepository.findById(share.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + share.userId()));
            existing.addSplit(new ExpenseSplit(participant, resolvedShares.get(share.userId())));
        }

        return expenseRepository.save(existing);
    }

    @Transactional
    public void deleteExpense(UUID groupId, UUID expenseId, UUID requestingUserId) {
        Expense expense = getExpense(groupId, expenseId, requestingUserId);
        requireEditPermission(expense, requestingUserId);
        expenseRepository.delete(expense);
    }

    /** Only the expense creator or a group admin may edit/delete it. */
    private void requireEditPermission(Expense expense, UUID requestingUserId) {
        if (expense.getCreatedBy().getId().equals(requestingUserId)) {
            return;
        }
        GroupMember membership = groupService.requireMembership(expense.getGroup().getId(), requestingUserId);
        if (membership.getRole() != GroupMember.Role.ADMIN) {
            throw new AccessDeniedException("Only the expense creator or a group admin can modify this expense.");
        }
    }

    private void validateParticipants(UUID payerId, List<ExpenseDtos.ShareInput> shares, Set<UUID> groupMemberIds) {
        if (!groupMemberIds.contains(payerId)) {
            throw new InvalidRequestException("Payer must be a member of the group.");
        }

        List<UUID> participantIds = shares.stream().map(ExpenseDtos.ShareInput::userId).toList();
        if (new HashSet<>(participantIds).size() != participantIds.size()) {
            throw new InvalidRequestException("Duplicate participants are not allowed.");
        }
        for (UUID participantId : participantIds) {
            if (!groupMemberIds.contains(participantId)) {
                throw new InvalidRequestException("All participants must be members of the group.");
            }
        }
    }

    /**
     * Core split-calculation logic (functional requirements 4-6):
     * - EQUAL: divide evenly; any leftover minor unit (paise/cents) goes to
     *   the first participants in sorted-ID order, so the sum always equals
     *   the total exactly.
     * - UNEQUAL: caller-supplied amounts must already sum to the total.
     * - PERCENTAGE: percentages must sum to 100; monetary shares are rounded
     *   down to the cent and the leftover cents are distributed to the
     *   shares with the largest fractional remainder (largest-remainder
     *   method), guaranteeing the sum equals the total exactly.
     */
    private Map<UUID, BigDecimal> resolveShares(ExpenseDtos.CreateExpenseRequest request) {
        BigDecimal total = request.amount().setScale(2, RoundingMode.HALF_UP);
        List<ExpenseDtos.ShareInput> shares = request.shares();

        return switch (request.splitType()) {
            case EQUAL -> resolveEqual(total, shares);
            case UNEQUAL -> resolveUnequal(total, shares);
            case PERCENTAGE -> resolvePercentage(total, shares);
        };
    }

    private Map<UUID, BigDecimal> resolveEqual(BigDecimal total, List<ExpenseDtos.ShareInput> shares) {
        List<UUID> ids = shares.stream().map(ExpenseDtos.ShareInput::userId)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        int n = ids.size();

        long totalMinor = total.movePointRight(2).longValueExact();
        long base = totalMinor / n;
        long remainder = totalMinor % n;

        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long minor = base + (i < remainder ? 1 : 0);
            result.put(ids.get(i), BigDecimal.valueOf(minor).movePointLeft(2));
        }
        return result;
    }

    private Map<UUID, BigDecimal> resolveUnequal(BigDecimal total, List<ExpenseDtos.ShareInput> shares) {
        BigDecimal sum = shares.stream()
                .map(ExpenseDtos.ShareInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (sum.subtract(total).abs().compareTo(TOLERANCE) > 0) {
            throw new InvalidRequestException(
                    "Unequal shares must sum exactly to the total expense amount (got " + sum + ", expected " + total + ").");
        }

        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (ExpenseDtos.ShareInput s : shares) {
            result.put(s.userId(), s.amount().setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private Map<UUID, BigDecimal> resolvePercentage(BigDecimal total, List<ExpenseDtos.ShareInput> shares) {
        BigDecimal percentSum = shares.stream()
                .map(ExpenseDtos.ShareInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (percentSum.subtract(HUNDRED).abs().compareTo(new BigDecimal("0.1")) > 0) {
            throw new InvalidRequestException(
                    "Percentage shares must total 100% (got " + percentSum + "%).");
        }

        long totalMinor = total.movePointRight(2).longValueExact();

        record Raw(UUID id, long floorMinor, BigDecimal fraction) {}
        List<Raw> raws = new ArrayList<>();
        for (ExpenseDtos.ShareInput s : shares) {
            BigDecimal exactMinor = total.movePointRight(2)
                    .multiply(s.amount())
                    .divide(HUNDRED, 6, RoundingMode.HALF_UP);
            long floor = exactMinor.setScale(0, RoundingMode.DOWN).longValueExact();
            BigDecimal fraction = exactMinor.subtract(BigDecimal.valueOf(floor));
            raws.add(new Raw(s.userId(), floor, fraction));
        }

        long allocated = raws.stream().mapToLong(Raw::floorMinor).sum();
        long remainder = totalMinor - allocated;

        List<Raw> byFractionDesc = new ArrayList<>(raws);
        byFractionDesc.sort((a, b) -> b.fraction().compareTo(a.fraction()));

        Map<UUID, Long> minorByUser = new LinkedHashMap<>();
        raws.forEach(r -> minorByUser.put(r.id(), r.floorMinor()));
        for (int i = 0; i < remainder; i++) {
            UUID id = byFractionDesc.get(i % byFractionDesc.size()).id();
            minorByUser.merge(id, 1L, Long::sum);
        }

        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        minorByUser.forEach((id, minor) -> result.put(id, BigDecimal.valueOf(minor).movePointLeft(2)));
        return result;
    }
}
