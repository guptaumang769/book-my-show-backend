package com.umang.bookmyshow.service;

import com.umang.bookmyshow.dto.request.LoginRequest;
import com.umang.bookmyshow.dto.request.RegisterRequest;
import com.umang.bookmyshow.dto.response.AuthResponse;
import com.umang.bookmyshow.exception.InvalidRequestException;
import com.umang.bookmyshow.model.entity.User;
import com.umang.bookmyshow.repository.UserRepository;
import com.umang.bookmyshow.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidRequestException("Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .build();
        user = userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Invalid credentials");
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public User loadByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + email));
    }

    private AuthResponse toAuthResponse(User user) {
        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(token)
                .tokenType("Bearer")
                .build();
    }
}
