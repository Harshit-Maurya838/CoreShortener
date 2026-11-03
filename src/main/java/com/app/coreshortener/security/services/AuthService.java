package com.app.coreshortener.security.services;

import com.app.coreshortener.Models.Role;
import com.app.coreshortener.Models.User;
import com.app.coreshortener.Repository.TenantRepository;
import com.app.coreshortener.Repository.UserRepository;
import com.app.coreshortener.auth.AuthenticationRequest;
import com.app.coreshortener.auth.AuthenticationResponse;
import com.app.coreshortener.auth.RefreshTokenRequest;
import com.app.coreshortener.auth.RegisterRequest;
import com.app.coreshortener.security.domain.UserAdapter;
import com.app.coreshortener.security.jwt.JwtService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;

    public AuthenticationResponse register(RegisterRequest req) {
        if (!tenantRepository.existsById(req.tenantId())) {
            throw new IllegalArgumentException("Tenant does not exist with ID: " + req.tenantId());
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .tenantId(req.tenantId())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.MEMBER)
                .build();

        userRepository.save(user);
        UserAdapter userAdapter = new UserAdapter(user);
        String accessToken = jwtService.generateAccessToken(userAdapter);
        String refreshToken = jwtService.generateRefreshToken(userAdapter);

        return new AuthenticationResponse(accessToken, refreshToken, 900);
    }

    public AuthenticationResponse authenticate(AuthenticationRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        UserAdapter userAdapter = new UserAdapter(user);
        String accessToken = jwtService.generateAccessToken(userAdapter);
        String refreshToken = jwtService.generateRefreshToken(userAdapter);

        return new AuthenticationResponse(accessToken, refreshToken, 900);
    }

    public AuthenticationResponse refreshToken(RefreshTokenRequest req) {
        String token = req.refreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
        }
        String userEmail = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserAdapter userAdapter = new UserAdapter(user);
        if (!jwtService.isTokenValid(token, userAdapter)) {
            throw new IllegalArgumentException("Refresh token is expired or invalid");
        }

        String newAccessToken = jwtService.generateAccessToken(userAdapter);
        return new AuthenticationResponse(newAccessToken, token, 900);
    }
}
