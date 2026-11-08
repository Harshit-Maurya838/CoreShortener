package com.app.coreshortener.service;

import com.app.coreshortener.Models.Role;
import com.app.coreshortener.Models.ShortUrl;
import com.app.coreshortener.Models.User;
import com.app.coreshortener.Repository.ShortURLRepository;
import com.app.coreshortener.dto.CreateShortUrlRequest;
import com.app.coreshortener.dto.PagedResponse;
import com.app.coreshortener.dto.ShortUrlResponse;
import com.app.coreshortener.dto.UpdateShortUrlRequest;
import com.app.coreshortener.exception.ResourceNotFoundException;
import com.app.coreshortener.exception.ShortCodeConflictException;
import com.app.coreshortener.exception.UrlExpiredException;
import com.app.coreshortener.security.domain.TenantContext;
import com.app.coreshortener.security.domain.UserAdapter;
import com.app.coreshortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String BASE_URL = "http://sho.rt/";
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Mock
    private ShortURLRepository shortURLRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @InjectMocks
    private ShortUrlService shortUrlService;

    private UserAdapter member;
    private UserAdapter otherMember;
    private UserAdapter admin;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortUrlService, "baseUrl", BASE_URL);
        member = userOf(TENANT_A, Role.MEMBER);
        otherMember = userOf(TENANT_A, Role.MEMBER);
        admin = userOf(TENANT_A, Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- create

    @Nested
    @DisplayName("createShortUrl")
    class Create {

        @Test
        @DisplayName("generates a code and scopes the URL to the caller's tenant and user")
        void createsWithGeneratedCode() {
            loginAs(member);
            when(shortCodeGenerator.generateRandomCode()).thenReturn("abc1234");
            when(shortCodeGenerator.isReserved("abc1234")).thenReturn(false);
            when(shortURLRepository.existsByShortCode("abc1234")).thenReturn(false);
            when(shortURLRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> persisted(inv.getArgument(0)));

            ShortUrlResponse response = shortUrlService.createShortUrl(request("  https://example.com/page  ", null));

            ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
            verify(shortURLRepository).saveAndFlush(captor.capture());
            ShortUrl saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
            assertThat(saved.getCreatedBy()).isEqualTo(member.getId());
            assertThat(saved.getOriginalUrl()).isEqualTo("https://example.com/page");
            assertThat(saved.getIsActive()).isTrue();

            assertThat(response.getShortCode()).isEqualTo("abc1234");
            assertThat(response.getShortUrl()).isEqualTo("http://sho.rt/abc1234");
            assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("retries when a generated code collides")
        void retriesOnCollision() {
            loginAs(member);
            when(shortCodeGenerator.generateRandomCode()).thenReturn("taken01", "fresh01");
            when(shortCodeGenerator.isReserved(anyString())).thenReturn(false);
            when(shortURLRepository.existsByShortCode("taken01")).thenReturn(true);
            when(shortURLRepository.existsByShortCode("fresh01")).thenReturn(false);
            when(shortURLRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> persisted(inv.getArgument(0)));

            ShortUrlResponse response = shortUrlService.createShortUrl(request("https://example.com", null));

            assertThat(response.getShortCode()).isEqualTo("fresh01");
            verify(shortCodeGenerator, times(2)).generateRandomCode();
        }

        @Test
        @DisplayName("gives up after max attempts when every generated code collides")
        void failsAfterMaxAttempts() {
            loginAs(member);
            when(shortCodeGenerator.generateRandomCode()).thenReturn("taken01");
            when(shortCodeGenerator.isReserved(anyString())).thenReturn(false);
            when(shortURLRepository.existsByShortCode("taken01")).thenReturn(true);

            assertThatThrownBy(() -> shortUrlService.createShortUrl(request("https://example.com", null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("5 attempts");
            verify(shortURLRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("uses a free custom code as-is")
        void usesCustomCode() {
            loginAs(member);
            when(shortCodeGenerator.isReserved("my-link")).thenReturn(false);
            when(shortURLRepository.existsByShortCode("my-link")).thenReturn(false);
            when(shortURLRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> persisted(inv.getArgument(0)));

            ShortUrlResponse response = shortUrlService.createShortUrl(request("https://example.com", " my-link "));

            assertThat(response.getShortCode()).isEqualTo("my-link");
            verify(shortCodeGenerator, never()).generateRandomCode();
        }

        @Test
        @DisplayName("rejects a reserved custom code")
        void rejectsReservedCustomCode() {
            loginAs(member);
            when(shortCodeGenerator.isReserved("urls")).thenReturn(true);

            assertThatThrownBy(() -> shortUrlService.createShortUrl(request("https://example.com", "urls")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
            verify(shortURLRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("rejects a custom code that is already taken")
        void rejectsTakenCustomCode() {
            loginAs(member);
            when(shortCodeGenerator.isReserved("promo")).thenReturn(false);
            when(shortURLRepository.existsByShortCode("promo")).thenReturn(true);

            assertThatThrownBy(() -> shortUrlService.createShortUrl(request("https://example.com", "promo")))
                    .isInstanceOf(ShortCodeConflictException.class);
            verify(shortURLRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("maps a unique-constraint race on insert to a conflict")
        void mapsConcurrentInsertToConflict() {
            loginAs(member);
            when(shortCodeGenerator.isReserved("promo")).thenReturn(false);
            when(shortURLRepository.existsByShortCode("promo")).thenReturn(false);
            when(shortURLRepository.saveAndFlush(any(ShortUrl.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> shortUrlService.createShortUrl(request("https://example.com", "promo")))
                    .isInstanceOf(ShortCodeConflictException.class)
                    .hasMessageContaining("promo");
        }

        @Test
        @DisplayName("fails when no tenant is bound to the request")
        void failsWithoutTenant() {
            assertThatThrownBy(() -> shortUrlService.createShortUrl(request("https://example.com", null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tenant context");
            verify(shortURLRepository, never()).saveAndFlush(any());
        }
    }

    // ------------------------------------------------------------ read / RBAC

    @Nested
    @DisplayName("getShortUrlById")
    class GetById {

        @Test
        @DisplayName("owner can read their own URL")
        void ownerCanRead() {
            loginAs(member);
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));

            assertThat(shortUrlService.getShortUrlById(url.getId()).getId()).isEqualTo(url.getId());
        }

        @Test
        @DisplayName("member cannot read another member's URL")
        void memberCannotReadOthers() {
            loginAs(otherMember);
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));

            assertThatThrownBy(() -> shortUrlService.getShortUrlById(url.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("admin can read any URL in their tenant")
        void adminCanReadAnyInTenant() {
            loginAs(admin);
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));

            assertThat(shortUrlService.getShortUrlById(url.getId()).getId()).isEqualTo(url.getId());
        }

        @Test
        @DisplayName("tenant B cannot see tenant A's URL (looked up by tenant, so it is not found)")
        void otherTenantGetsNotFound() {
            UserAdapter tenantBAdmin = userOf(TENANT_B, Role.ADMIN);
            loginAs(tenantBAdmin);
            UUID tenantAUrlId = UUID.randomUUID();
            when(shortURLRepository.findByTenantIdAndId(TENANT_B, tenantAUrlId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shortUrlService.getShortUrlById(tenantAUrlId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(shortURLRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("listShortUrls")
    class ListUrls {

        @Test
        @DisplayName("member only lists their own URLs")
        void memberListsOwn() {
            loginAs(member);
            Pageable pageable = PageRequest.of(0, 10);
            Page<ShortUrl> page = new PageImpl<>(List.of(existing(TENANT_A, member.getId(), "abc1234")), pageable, 1);
            when(shortURLRepository.findAllByTenantIdAndCreatedBy(TENANT_A, member.getId(), pageable)).thenReturn(page);

            PagedResponse<ShortUrlResponse> result = shortUrlService.listShortUrls(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(shortURLRepository, never()).findAllByTenantId(any(), any(Pageable.class));
        }

        @Test
        @DisplayName("admin lists every URL in the tenant")
        void adminListsTenant() {
            loginAs(admin);
            Pageable pageable = PageRequest.of(0, 10);
            Page<ShortUrl> page = new PageImpl<>(List.of(
                    existing(TENANT_A, member.getId(), "abc1234"),
                    existing(TENANT_A, otherMember.getId(), "xyz9876")), pageable, 2);
            when(shortURLRepository.findAllByTenantId(TENANT_A, pageable)).thenReturn(page);

            PagedResponse<ShortUrlResponse> result = shortUrlService.listShortUrls(pageable);

            assertThat(result.getContent()).hasSize(2);
            verify(shortURLRepository, never()).findAllByTenantIdAndCreatedBy(any(), any(), any());
        }
    }

    // --------------------------------------------------------- update/delete

    @Test
    @DisplayName("update only changes the fields that were sent")
    void updateIsPartial() {
        loginAs(member);
        ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
        LocalDateTime originalExpiry = url.getExpiresAt();
        when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));
        when(shortURLRepository.save(url)).thenReturn(url);

        UpdateShortUrlRequest update = UpdateShortUrlRequest.builder()
                .originalUrl(" https://new.example.com ")
                .build();
        ShortUrlResponse response = shortUrlService.updateShortUrl(url.getId(), update);

        assertThat(response.getOriginalUrl()).isEqualTo("https://new.example.com");
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getExpiresAt()).isEqualTo(originalExpiry);
    }

    @Test
    @DisplayName("member cannot update another member's URL")
    void memberCannotUpdateOthers() {
        loginAs(otherMember);
        ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
        when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> shortUrlService.updateShortUrl(url.getId(), new UpdateShortUrlRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(shortURLRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete is a soft delete (isActive = false)")
    void deleteIsSoft() {
        loginAs(member);
        ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
        when(shortURLRepository.findByTenantIdAndId(TENANT_A, url.getId())).thenReturn(Optional.of(url));

        shortUrlService.deleteShortUrl(url.getId());

        assertThat(url.getIsActive()).isFalse();
        verify(shortURLRepository).save(url);
        verify(shortURLRepository, never()).delete(any());
        verify(shortURLRepository, never()).deleteByTenantIdAndId(any(), any());
    }

    // --------------------------------------------------------------- redirect

    @Nested
    @DisplayName("resolveRedirect")
    class Resolve {

        @Test
        @DisplayName("returns the original URL for an active link")
        void resolvesActive() {
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            when(shortURLRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

            assertThat(shortUrlService.resolveRedirect("abc1234")).isEqualTo(url.getOriginalUrl());
        }

        @Test
        @DisplayName("unknown code is not found")
        void unknownCode() {
            when(shortURLRepository.findByShortCode("nope123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shortUrlService.resolveRedirect("nope123"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("inactive (soft-deleted) link is not found")
        void inactiveLink() {
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            url.setIsActive(false);
            when(shortURLRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

            assertThatThrownBy(() -> shortUrlService.resolveRedirect("abc1234"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("expired link is gone")
        void expiredLink() {
            ShortUrl url = existing(TENANT_A, member.getId(), "abc1234");
            url.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(shortURLRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

            assertThatThrownBy(() -> shortUrlService.resolveRedirect("abc1234"))
                    .isInstanceOf(UrlExpiredException.class);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static UserAdapter userOf(UUID tenantId, Role role) {
        return new UserAdapter(User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

    private static void loginAs(UserAdapter user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        TenantContext.setTenantId(user.getTenantId());
    }

    private static CreateShortUrlRequest request(String originalUrl, String customCode) {
        return CreateShortUrlRequest.builder()
                .originalUrl(originalUrl)
                .customCode(customCode)
                .build();
    }

    private static ShortUrl persisted(ShortUrl url) {
        url.setId(UUID.randomUUID());
        url.setCreatedAt(LocalDateTime.now());
        return url;
    }

    private static ShortUrl existing(UUID tenantId, UUID createdBy, String code) {
        return ShortUrl.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .originalUrl("https://example.com/" + code)
                .shortCode(code)
                .createdBy(createdBy)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
