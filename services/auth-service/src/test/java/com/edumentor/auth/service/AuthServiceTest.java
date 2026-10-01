package com.edumentor.auth.service;

import com.edumentor.auth.dto.AuthResponse;
import com.edumentor.auth.dto.LoginRequest;
import com.edumentor.auth.dto.RegisterRequest;
import com.edumentor.auth.dto.UserResponse;
import com.edumentor.auth.entity.Role;
import com.edumentor.auth.entity.User;
import com.edumentor.auth.exception.DuplicateEmailException;
import com.edumentor.auth.exception.RoleNotAllowedException;
import com.edumentor.auth.repository.UserRepository;
import com.edumentor.auth.security.JwtService;
import com.edumentor.auth.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordNormalizesEmailAndNeverReturnsPassword() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rdX")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse response = authService.register(
                new RegisterRequest("Asha", "  A@B.com ", "Passw0rdX", Role.STUDENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getEmail()).isEqualTo("a@b.com");
        assertThat(response.email()).isEqualTo("a@b.com");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Asha", "a@b.com", "Passw0rdX", Role.STUDENT)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsAdminRole() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Eve", "eve@b.com", "Passw0rdX", Role.ADMIN)))
                .isInstanceOf(RoleNotAllowedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsTokenAndUser() {
        User user = User.builder().id(5L).name("Asha").email("a@b.com").passwordHash("hash")
                .role(Role.STUDENT).enabled(true).build();
        UserPrincipal principal = UserPrincipal.from(user);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(principal)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(new LoginRequest("a@b.com", "Passw0rdX"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.user().id()).isEqualTo(5L);
    }
}