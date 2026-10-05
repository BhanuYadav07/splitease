package com.splitease.group;

import com.splitease.balance.BalanceService;
import com.splitease.balance.BalanceDtos;
import com.splitease.exception.AccessDeniedException;
import com.splitease.exception.ConflictException;
import com.splitease.exception.ResourceNotFoundException;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;

    @Transactional
    public Group createGroup(User creator, GroupDtos.CreateGroupRequest request) {
        Group group = groupRepository.save(new Group(request.name(), creator));
        groupMemberRepository.save(new GroupMember(group, creator, GroupMember.Role.ADMIN));
        return group;
    }

    public List<Group> listGroupsForUser(UUID userId) {
        return groupMemberRepository.findByUserId(userId).stream()
                .map(GroupMember::getGroup)
                .toList();
    }

    public Group getGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    public List<GroupMember> getMembers(UUID groupId) {
        return groupMemberRepository.findByGroupId(groupId);
    }

    /**
     * Enforces functional requirement 10: users can only access groups they
     * belong to, and cannot reach a group by editing the ID in the URL.
     * Every group/expense/balance/settlement endpoint calls this first.
     */
    public GroupMember requireMembership(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group."));
    }

    public void requireAdmin(UUID groupId, UUID userId) {
        GroupMember member = requireMembership(groupId, userId);
        if (member.getRole() != GroupMember.Role.ADMIN) {
            throw new AccessDeniedException("Only group admins can perform this action.");
        }
    }

    @Transactional
    public GroupMember addMember(UUID groupId, UUID requestingUserId, GroupDtos.AddMemberRequest request) {
        requireMembership(groupId, requestingUserId);
        Group group = getGroup(groupId);
        User newMember = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + request.email()));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, newMember.getId())) {
            throw new ConflictException("User is already a member of this group.");
        }

        return groupMemberRepository.save(new GroupMember(group, newMember, GroupMember.Role.MEMBER));
    }

    /**
     * Functional requirement 2: members can be removed only if they have no
     * outstanding balance in the group (nothing owed, nothing to receive).
     */
    @Transactional
    public void removeMember(UUID groupId, UUID requestingUserId, UUID memberUserId) {
        requireMembership(groupId, requestingUserId);
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this group."));

        BalanceDtos.MemberBalance balance = balanceService.getBalanceForUser(groupId, memberUserId);
        if (balance.netBalance().abs().compareTo(new java.math.BigDecimal("0.01")) >= 0) {
            throw new ConflictException(
                    "Cannot remove a member with an outstanding balance. Settle up first.");
        }

        groupMemberRepository.delete(membership);
    }
}
