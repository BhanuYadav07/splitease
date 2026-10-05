package com.splitease.balance;

import com.splitease.expense.Expense;
import com.splitease.expense.ExpenseRepository;
import com.splitease.expense.ExpenseSplit;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupMemberRepository;
import com.splitease.settlement.Settlement;
import com.splitease.settlement.SettlementRepository;
import com.splitease.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementRepository settlementRepository;

    /**
     * Net Balance = Total Paid - Total Owed (functional requirement 7),
     * further adjusted by any settlements already recorded as paid: paying
     * down a debt raises the payer's balance and lowers the receiver's.
     */
    public List<BalanceDtos.MemberBalance> getGroupBalances(UUID groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        List<Settlement> settlements = settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId);

        Map<UUID, BigDecimal> paid = new LinkedHashMap<>();
        Map<UUID, BigDecimal> owed = new LinkedHashMap<>();
        Map<UUID, User> usersById = new LinkedHashMap<>();

        for (GroupMember m : members) {
            paid.put(m.getUser().getId(), BigDecimal.ZERO);
            owed.put(m.getUser().getId(), BigDecimal.ZERO);
            usersById.put(m.getUser().getId(), m.getUser());
        }

        for (Expense expense : expenses) {
            UUID payerId = expense.getPayer().getId();
            usersById.putIfAbsent(payerId, expense.getPayer());
            paid.merge(payerId, expense.getAmount(), BigDecimal::add);
            for (ExpenseSplit split : expense.getSplits()) {
                usersById.putIfAbsent(split.getUser().getId(), split.getUser());
                owed.merge(split.getUser().getId(), split.getShareAmount(), BigDecimal::add);
            }
        }

        // Settlements already paid reduce what's still outstanding: treat the
        // payment as though the payer "paid" that amount toward the receiver's
        // credit, and the receiver's "owed" position is reduced by the same amount.
        for (Settlement s : settlements) {
            usersById.putIfAbsent(s.getFromUser().getId(), s.getFromUser());
            usersById.putIfAbsent(s.getToUser().getId(), s.getToUser());
            paid.merge(s.getFromUser().getId(), s.getAmount(), BigDecimal::add);
            owed.merge(s.getToUser().getId(), s.getAmount(), BigDecimal::add);
        }

        List<BalanceDtos.MemberBalance> result = new java.util.ArrayList<>();
        for (UUID userId : paid.keySet()) {
            BigDecimal totalPaid = paid.get(userId).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalOwed = owed.get(userId).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = totalPaid.subtract(totalOwed).setScale(2, RoundingMode.HALF_UP);
            result.add(new BalanceDtos.MemberBalance(userId, usersById.get(userId).getName(), totalPaid, totalOwed, net));
        }
        return result;
    }

    public BalanceDtos.MemberBalance getBalanceForUser(UUID groupId, UUID userId) {
        return getGroupBalances(groupId).stream()
                .filter(b -> b.userId().equals(userId))
                .findFirst()
                .orElse(new BalanceDtos.MemberBalance(userId, "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
