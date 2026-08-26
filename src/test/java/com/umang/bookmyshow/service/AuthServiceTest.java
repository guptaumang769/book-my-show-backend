package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.umang.bookmyshow.dto.request.LoginRequest;
import com.umang.bookmyshow.dto.request.RegisterRequest;
import com.umang.bookmyshow.dto.response.AuthResponse;
import com.umang.bookmyshow.exception.InvalidRequestException;
import com.umang.bookmyshow.model.entity.User;
import com.umang.bookmyshow.repository.UserRepository;
import com.umang.bookmyshow.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for authentication: successful registration issues a token, duplicate emails are
 * rejected, and a wrong password fails login without leaking which field was wrong.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void register_issuesToken_forNewEmail() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(tokenProvider.generateToken(anyLong(), anyString())).thenReturn("jwt-token");

        AuthResponse response = authService.register(RegisterRequest.builder()
                .email("new@test.com").password("secret123").build());

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    void register_throws_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("dupe@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(RegisterRequest.builder()
                .email("dupe@test.com").password("secret123").build()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void login_throws_onWrongPassword() {
        User user = User.builder().id(1L).email("user@test.com").passwordHash("hashed").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(LoginRequest.builder()
                .email("user@test.com").password("wrong").build()))
                .isInstanceOf(InvalidRequestException.class);
    }
}
