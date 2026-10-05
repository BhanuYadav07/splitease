package com.splitease.security;

import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
    }

    /**
     * Used by JwtFilter for the id claim in a token. Looking the user up by id
     * (instead of by the email the token was issued for) keeps a session alive
     * when the user edits their profile and changes the address.
     */
    public AuthenticatedUser loadUserById(UUID userId) throws UsernameNotFoundException {
        return userRepository.findById(userId)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with id: " + userId));
    }
}
