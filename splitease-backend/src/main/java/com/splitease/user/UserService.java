package com.splitease.user;

import com.splitease.exception.ConflictException;
import com.splitease.exception.ResourceNotFoundException;
import com.splitease.otp.OtpServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OtpServiceImpl otpService;

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
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(email);

        if (emailChanged && userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already in use: " + email);
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        if (emailChanged) {
            // A new address is an unproven address: the account drops back to unverified,
            // so the next request to a protected endpoint is refused until the code now
            // on its way is entered.
            user.setEmailVerified(false);
        }
        User saved = userRepository.save(user);

        if (emailChanged) {
            // Send the confirmation code for the new address. Enumeration-safe helper, so
            // a mail hiccup cannot fail the profile update itself.
            otpService.resendVerification(email);
        }
        return saved;
    }
}
