package com.splitease.group;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class GroupDtos {

    public record CreateGroupRequest(@NotBlank(message = "Group name is required") String name) {}

    public record AddMemberRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email
    ) {}

    public record MemberResponse(UUID id, String name, String email, String role) {}

    public record GroupSummaryResponse(UUID id, String name, int memberCount) {}

    public record GroupDetailResponse(UUID id, String name, List<MemberResponse> members) {}
}
