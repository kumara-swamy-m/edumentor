package com.edumentor.auth.service;

import com.edumentor.auth.dto.AuthResponse;
import com.edumentor.auth.dto.LoginRequest;
import com.edumentor.auth.dto.RegisterRequest;
import com.edumentor.auth.dto.UserResponse;
import com.edumentor.auth.entity.Role;
import com.edumentor.auth.entity.User;
import com.edumentor.auth.exception.DuplicateEmailException;
import com.edumentor.auth.exception.RoleNotAllowedException;
import com.edumentor.auth.exception.UserNotFoundException;
import com.edumentor.auth.repository.UserRepository;
import com.edumentor.auth.security.JwtService;
import com.edumentor.auth.security.UserPrincipal;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor

public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new RoleNotAllowedException("Admin accounts cannot be self-registered");
        }
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .build();
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent registration with the same email won the race
            throw new DuplicateEmailException("An account with this email already exists");
        }
        log.info("User registered: id={}, role={}", user.getId(), user.getRole());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalize(request.email()), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        String token = jwtService.generateToken(principal);
        log.info("Authentication successful: userId={}", user.getId());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs() / 1000, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}