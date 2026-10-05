package com.splitease.user;

import com.splitease.exception.ConflictException;
import com.splitease.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Transactional
    public User updateProfile(UUID userId, UserDtos.UpdateProfileRequest request) {
        User user = getById(userId);
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.email());
        user.setName(request.name());
        user.setEmail(request.email());
        if (emailChanged) {
            // A new address is an unproven address: ask for a fresh 6-digit code.
            user.setEmailVerified(false);
        }
        return userRepository.save(user);
    }
}
