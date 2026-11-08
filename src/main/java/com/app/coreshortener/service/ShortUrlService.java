package com.app.coreshortener.service;

import com.app.coreshortener.Models.ShortUrl;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private final ShortURLRepository shortURLRepository;
    private final ShortCodeGenerator shortCodeGenerator;

    @Value("${application.shortener.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public ShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
        UUID tenantId = getRequiredTenantId();

        UserAdapter currentUser = getCurrentUser();
        UUID userId = currentUser != null ? currentUser.getId() : null;

        String shortCode;
        if (request.getCustomCode() != null && !request.getCustomCode().trim().isEmpty()) {
            String customCode = request.getCustomCode().trim();
            if (shortCodeGenerator.isReserved(customCode)) {
                throw new IllegalArgumentException("Short code '" + customCode + "' is a reserved keyword");
            }
            if (shortURLRepository.existsByShortCode(customCode)) {
                throw new ShortCodeConflictException("Short code '" + customCode + "' is already in use");
            }
            shortCode = customCode;
        } else {
            shortCode = generateUniqueShortCode();
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .tenantId(tenantId)
                .originalUrl(request.getOriginalUrl().trim())
                .shortCode(shortCode)
                .createdBy(userId)
                .expiresAt(request.getExpiresAt())
                .isActive(true)
                .build();

        try {
            ShortUrl saved = shortURLRepository.saveAndFlush(shortUrl);
            return mapToResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ShortCodeConflictException("Short code '" + shortCode + "' is already in use");
        }
    }

    @Transactional(readOnly = true)
    public ShortUrlResponse getShortUrlById(UUID id) {
        UUID tenantId = getRequiredTenantId();
        UserAdapter currentUser = getCurrentUser();

        ShortUrl shortUrl = shortURLRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found with ID: " + id));

        validateOwnershipOrAdmin(shortUrl, currentUser);
        return mapToResponse(shortUrl);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ShortUrlResponse> listShortUrls(Pageable pageable) {
        UUID tenantId = getRequiredTenantId();
        UserAdapter currentUser = getCurrentUser();

        Page<ShortUrl> page;
        if (isAdmin(currentUser)) {
            page = shortURLRepository.findAllByTenantId(tenantId, pageable);
        } else {
            UUID userId = currentUser != null ? currentUser.getId() : null;
            page = shortURLRepository.findAllByTenantIdAndCreatedBy(tenantId, userId, pageable);
        }

        return PagedResponse.from(page.map(this::mapToResponse));
    }

    @Transactional
    public ShortUrlResponse updateShortUrl(UUID id, UpdateShortUrlRequest request) {
        UUID tenantId = getRequiredTenantId();
        UserAdapter currentUser = getCurrentUser();

        ShortUrl shortUrl = shortURLRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found with ID: " + id));

        validateOwnershipOrAdmin(shortUrl, currentUser);

        if (request.getOriginalUrl() != null && !request.getOriginalUrl().trim().isEmpty()) {
            shortUrl.setOriginalUrl(request.getOriginalUrl().trim());
        }
        if (request.getIsActive() != null) {
            shortUrl.setIsActive(request.getIsActive());
        }
        if (request.getExpiresAt() != null) {
            shortUrl.setExpiresAt(request.getExpiresAt());
        }

        ShortUrl updated = shortURLRepository.save(shortUrl);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteShortUrl(UUID id) {
        UUID tenantId = getRequiredTenantId();
        UserAdapter currentUser = getCurrentUser();

        ShortUrl shortUrl = shortURLRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found with ID: " + id));

        validateOwnershipOrAdmin(shortUrl, currentUser);

        // Soft delete according to Day 5-6 requirements
        shortUrl.setIsActive(false);
        shortURLRepository.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public String resolveRedirect(String shortCode) {
        ShortUrl shortUrl = shortURLRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found with code: " + shortCode));

        if (Boolean.FALSE.equals(shortUrl.getIsActive())) {
            throw new ResourceNotFoundException("Short URL is inactive or has been disabled");
        }

        if (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlExpiredException("Short URL has expired");
        }

        return shortUrl.getOriginalUrl();
    }

    private String generateUniqueShortCode() {
        int maxAttempts = 5;
        for (int i = 0; i < maxAttempts; i++) {
            String code = shortCodeGenerator.generateRandomCode();
            if (!shortCodeGenerator.isReserved(code) && !shortURLRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code after " + maxAttempts + " attempts");
    }

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing");
        }
        return tenantId;
    }

    private UserAdapter getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserAdapter ua) {
            return ua;
        }
        return null;
    }

    private boolean isAdmin(UserAdapter user) {
        if (user == null) {
            return false;
        }
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void validateOwnershipOrAdmin(ShortUrl shortUrl, UserAdapter currentUser) {
        if (isAdmin(currentUser)) {
            return;
        }
        if (currentUser == null || shortUrl.getCreatedBy() == null || !shortUrl.getCreatedBy().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to access or modify this URL");
        }
    }

    private ShortUrlResponse mapToResponse(ShortUrl shortUrl) {
        String fullUrl = baseUrl.replaceAll("/+$", "") + "/" + shortUrl.getShortCode();
        return ShortUrlResponse.builder()
                .id(shortUrl.getId())
                .tenantId(shortUrl.getTenantId())
                .originalUrl(shortUrl.getOriginalUrl())
                .shortCode(shortUrl.getShortCode())
                .shortUrl(fullUrl)
                .createdBy(shortUrl.getCreatedBy())
                .expiresAt(shortUrl.getExpiresAt())
                .isActive(shortUrl.getIsActive())
                .createdAt(shortUrl.getCreatedAt())
                .build();
    }
}
