package com.splitease.user;

import com.splitease.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public UserDtos.UserResponse me() {
        return UserDtos.UserResponse.from(currentUserProvider.getCurrentUser());
    }

    @PutMapping("/me")
    public UserDtos.UserResponse updateMe(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        User updated = userService.updateProfile(currentUserProvider.getCurrentUserId(), request);
        return UserDtos.UserResponse.from(updated);
    }
}
