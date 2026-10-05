package com.splitease.group;

import com.splitease.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<GroupDtos.GroupSummaryResponse> create(@Valid @RequestBody GroupDtos.CreateGroupRequest request) {
        Group group = groupService.createGroup(currentUserProvider.getCurrentUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GroupDtos.GroupSummaryResponse(group.getId(), group.getName(), 1));
    }

    @GetMapping
    public List<GroupDtos.GroupSummaryResponse> listMine() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return groupService.listGroupsForUser(userId).stream()
                .map(g -> new GroupDtos.GroupSummaryResponse(
                        g.getId(), g.getName(), groupService.getMembers(g.getId()).size()))
                .toList();
    }

    @GetMapping("/{groupId}")
    public GroupDtos.GroupDetailResponse getDetail(@PathVariable UUID groupId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        groupService.requireMembership(groupId, userId);

        Group group = groupService.getGroup(groupId);
        List<GroupDtos.MemberResponse> members = groupService.getMembers(groupId).stream()
                .map(m -> new GroupDtos.MemberResponse(
                        m.getUser().getId(), m.getUser().getName(), m.getUser().getEmail(), m.getRole().name()))
                .toList();
        return new GroupDtos.GroupDetailResponse(group.getId(), group.getName(), members);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupDtos.MemberResponse> addMember(
            @PathVariable UUID groupId, @Valid @RequestBody GroupDtos.AddMemberRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        GroupMember member = groupService.addMember(groupId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new GroupDtos.MemberResponse(
                member.getUser().getId(), member.getUser().getName(), member.getUser().getEmail(), member.getRole().name()));
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId, @PathVariable UUID memberUserId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        groupService.removeMember(groupId, userId, memberUserId);
        return ResponseEntity.noContent().build();
    }
}
