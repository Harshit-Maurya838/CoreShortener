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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "s3cretPass";
    private static final String HASH = "$2a$10$hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authManager;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------- tenant signup

    @Nested
    @DisplayName("registerTenant")
    class RegisterTenant {

        @Test
        @DisplayName("creates the tenant and its first user as ADMIN")
        void createsTenantAndAdmin() {
            UUID tenantId = UUID.randomUUID();
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
                Tenant t = inv.getArgument(0);
                t.setId(tenantId);
                return t;
            });
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            stubTokens();

            AuthenticationResponse response = authService.registerTenant(
                    new RegisterTenantRequest("  Acme  ", EMAIL, PASSWORD));

            ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(tenantCaptor.capture());
            Tenant tenant = tenantCaptor.getValue();
            assertThat(tenant.getName()).isEqualTo("Acme");
            assertThat(tenant.getPlan()).isEqualTo("FREE");
            assertThat(tenant.getApiKey()).isNotBlank();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User admin = userCaptor.getValue();
            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(admin.getTenantId()).isEqualTo(tenantId);
            assertThat(admin.getPasswordHash()).isEqualTo(HASH);

            assertTokens(response);
        }

        @Test
        @DisplayName("rejects an email that is already registered without creating a tenant")
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.registerTenant(new RegisterTenantRequest("Acme", EMAIL, PASSWORD)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already exists");
            verify(tenantRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------- member signup

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("creates a MEMBER inside the given tenant with a BCrypt hash, never the raw password")
        void createsMember() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            stubTokens();

            AuthenticationResponse response = authService.register(new RegisterRequest(tenantId, EMAIL, PASSWORD));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getRole()).isEqualTo(Role.MEMBER);
            assertThat(saved.getTenantId()).isEqualTo(tenantId);
            assertThat(saved.getPasswordHash()).isEqualTo(HASH).isNotEqualTo(PASSWORD);

            assertTokens(response);
        }

        @Test
        @DisplayName("rejects an unknown tenant")
        void rejectsUnknownTenant() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(false);

            assertThatThrownBy(() -> authService.register(new RegisterRequest(tenantId, EMAIL, PASSWORD)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tenant does not exist");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void rejectsDuplicateEmail() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(new RegisterRequest(tenantId, EMAIL, PASSWORD)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already exists");
            verify(userRepository, never()).save(any());
        }
    }

    // ----------------------------------------------------------------- login

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("returns tokens for valid credentials")
        void validCredentials() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            stubTokens();

            AuthenticationResponse response = authService.authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

            verify(authManager).authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));
            assertTokens(response);
        }

        @Test
        @DisplayName("propagates bad credentials and issues no tokens")
        void badCredentials() {
            when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.authenticate(new AuthenticationRequest(EMAIL, "wrong")))
                    .isInstanceOf(BadCredentialsException.class);
            verify(jwtService, never()).generateAccessToken(any());
            verify(jwtService, never()).generateRefreshToken(any());
        }
    }

    // --------------------------------------------------------------- refresh

    @Nested
    @DisplayName("refreshToken")
    class Refresh {

        @Test
        @DisplayName("issues a new access token and keeps the same refresh token")
        void refreshesAccessToken() {
            when(jwtService.isRefreshToken("refresh-jwt")).thenReturn(true);
            when(jwtService.extractUsername("refresh-jwt")).thenReturn(EMAIL);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(jwtService.isTokenValid(any(), any(UserAdapter.class))).thenReturn(true);
            when(jwtService.generateAccessToken(any(UserAdapter.class))).thenReturn("new-access-jwt");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

            AuthenticationResponse response = authService.refreshToken(new RefreshTokenRequest("refresh-jwt"));

            assertThat(response.accessToken()).isEqualTo("new-access-jwt");
            assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            verify(jwtService, never()).generateRefreshToken(any());
        }

        @Test
        @DisplayName("rejects an access token used as a refresh token")
        void rejectsAccessToken() {
            when(jwtService.isRefreshToken("access-jwt")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("access-jwt")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected refresh token");
            verify(jwtService, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("rejects a refresh token for a user that no longer exists")
        void rejectsUnknownUser() {
            when(jwtService.isRefreshToken("refresh-jwt")).thenReturn(true);
            when(jwtService.extractUsername("refresh-jwt")).thenReturn(EMAIL);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("refresh-jwt")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("rejects an invalid refresh token")
        void rejectsInvalidToken() {
            when(jwtService.isRefreshToken("refresh-jwt")).thenReturn(true);
            when(jwtService.extractUsername("refresh-jwt")).thenReturn(EMAIL);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(jwtService.isTokenValid(any(), any(UserAdapter.class))).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("refresh-jwt")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired or invalid");
            verify(jwtService, never()).generateAccessToken(any());
        }
    }

    // ---------------------------------------------------------------- helpers

    private void stubTokens() {
        when(jwtService.generateAccessToken(any(UserAdapter.class))).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(any(UserAdapter.class))).thenReturn("refresh-jwt");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }

    private static void assertTokens(AuthenticationResponse response) {
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    private static User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash(HASH)
                .role(Role.MEMBER)
                .build();
    }
}
