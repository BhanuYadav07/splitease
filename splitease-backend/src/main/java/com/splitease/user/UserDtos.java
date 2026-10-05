package com.splitease.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class UserDtos {

    public record UserResponse(UUID id, String name, String email, boolean emailVerified) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.isEmailVerified());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") String name,
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email
    ) {}
}
