package com.app.coreshortener.security.jwt;

import com.app.coreshortener.Models.Role;
import com.app.coreshortener.Models.User;
import com.app.coreshortener.security.domain.UserAdapter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B59703D";

    private JwtService jwtService;
    private UserAdapter user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L);

        user = new UserAdapter(User.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .build());
    }

    @Test
    @DisplayName("access token carries subject, tenantId and ACCESS type")
    void accessTokenClaims() {
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("user@example.com");
        assertThat(jwtService.extractTenantId(token)).isEqualTo(user.getTenantId());
        assertThat(jwtService.isAccessToken(token)).isTrue();
        assertThat(jwtService.isRefreshToken(token)).isFalse();
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("refresh token is typed REFRESH and cannot pass as an access token")
    void refreshTokenType() {
        String token = jwtService.generateRefreshToken(user);

        assertThat(jwtService.isRefreshToken(token)).isTrue();
        assertThat(jwtService.isAccessToken(token)).isFalse();
        assertThat(jwtService.extractTenantId(token)).isEqualTo(user.getTenantId());
    }

    @Test
    @DisplayName("token is not valid for a different user")
    void invalidForOtherUser() {
        String token = jwtService.generateAccessToken(user);
        UserAdapter other = new UserAdapter(User.builder()
                .id(UUID.randomUUID())
                .tenantId(user.getTenantId())
                .email("other@example.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .build());

        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    @DisplayName("tampered token is rejected")
    void tamperedToken() {
        String token = jwtService.generateAccessToken(user);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("expired token is rejected")
    void expiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1_000L);
        String token = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.isTokenValid(token, user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("exposes access token lifetime in seconds")
    void expirationSeconds() {
        assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(900L);
    }
}
