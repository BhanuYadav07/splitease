package com.splitease.balance;

import com.splitease.group.GroupService;
import com.splitease.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;
    private final GroupService groupService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<BalanceDtos.MemberBalance> groupBalances(@PathVariable UUID groupId) {
        groupService.requireMembership(groupId, currentUserProvider.getCurrentUserId());
        return balanceService.getGroupBalances(groupId);
    }

    @GetMapping("/me")
    public BalanceDtos.MemberBalance myBalance(@PathVariable UUID groupId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        groupService.requireMembership(groupId, userId);
        return balanceService.getBalanceForUser(groupId, userId);
    }
}
