package com.splitease.settlement;

import com.splitease.balance.BalanceDtos;
import com.splitease.balance.BalanceService;
import com.splitease.exception.InvalidRequestException;
import com.splitease.exception.ResourceNotFoundException;
import com.splitease.group.Group;
import com.splitease.group.GroupService;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final BalanceService balanceService;
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final CashFlowAlgorithm cashFlowAlgorithm = new CashFlowAlgorithm();

    public List<SettlementDtos.SuggestedSettlementResponse> getSuggestedSettlements(UUID groupId) {
        List<BalanceDtos.MemberBalance> balances = balanceService.getGroupBalances(groupId);
        return cashFlowAlgorithm.simplify(balances).stream()
                .map(t -> new SettlementDtos.SuggestedSettlementResponse(
                        t.fromUserId(), t.fromUserName(), t.toUserId(), t.toUserName(), t.amount()))
                .toList();
    }

    public List<SettlementDtos.SettlementHistoryResponse> getHistory(UUID groupId) {
        return settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId).stream()
                .map(s -> new SettlementDtos.SettlementHistoryResponse(
                        s.getId(), s.getFromUser().getId(), s.getFromUser().getName(),
                        s.getToUser().getId(), s.getToUser().getName(),
                        s.getAmount(), s.getStatus().name(), s.getSettledAt()))
                .toList();
    }

    /**
     * Records that a settlement has been paid (functional requirement 9),
     * enforcing requirement 11's validation: positive amount, both parties
     * in the group, and no self-settlement.
     */
    @Transactional
    public Settlement recordSettlement(UUID groupId, UUID requestingUserId, SettlementDtos.RecordSettlementRequest request) {
        groupService.requireMembership(groupId, requestingUserId);

        if (request.fromUserId().equals(request.toUserId())) {
            throw new InvalidRequestException("A user cannot settle with themselves.");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Settlement amount must be greater than zero.");
        }

        groupService.requireMembership(groupId, request.fromUserId());
        groupService.requireMembership(groupId, request.toUserId());

        Group group = groupService.getGroup(groupId);
        User fromUser = userRepository.findById(request.fromUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.fromUserId()));
        User toUser = userRepository.findById(request.toUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.toUserId()));

        Settlement settlement = new Settlement(group, fromUser, toUser, request.amount());
        return settlementRepository.save(settlement);
    }
}
