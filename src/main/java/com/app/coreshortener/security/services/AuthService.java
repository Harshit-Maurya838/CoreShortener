package com.app.coreshortener.security.services;

import com.app.coreshortener.Models.Role;
import com.app.coreshortener.Models.Tenant;
import com.app.coreshortener.Models.User;
import com.app.coreshortener.Repository.TenantRepository;
import com.app.coreshortener.Repository.UserRepository;
import com.app.coreshortener.auth.AuthenticationRequest;
import com.app.coreshortener.auth.AuthenticationResponse;
import com.app.coreshortener.auth.RefreshTokenRequest;
import com.app.coreshortener.auth.RegisterRequest;
import com.app.coreshortener.auth.RegisterTenantRequest;
import com.app.coreshortener.security.domain.UserAdapter;
import com.app.coreshortener.security.jwt.JwtService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class AuthService {
    private static final String DEFAULT_PLAN = "FREE";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;

    // Creates a new tenant and its first user as ADMIN in a single transaction.
    @Transactional
    public AuthenticationResponse registerTenant(RegisterTenantRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(req.tenantName().trim())
                .apiKey(UUID.randomUUID().toString().replace("-", ""))
                .plan(DEFAULT_PLAN)
                .build();
        tenant = tenantRepository.save(tenant);

        User admin = User.builder()
                .tenantId(tenant.getId())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        return buildTokens(new UserAdapter(admin));
    }

    // Registers a MEMBER user inside an existing tenant.
    @Transactional
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
        return buildTokens(new UserAdapter(user));
    }

    public AuthenticationResponse authenticate(AuthenticationRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        return buildTokens(new UserAdapter(user));
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
        return new AuthenticationResponse(newAccessToken, token, jwtService.getAccessTokenExpirationSeconds());
    }

    private AuthenticationResponse buildTokens(UserAdapter userAdapter) {
        String accessToken = jwtService.generateAccessToken(userAdapter);
        String refreshToken = jwtService.generateRefreshToken(userAdapter);
        return new AuthenticationResponse(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }
}
