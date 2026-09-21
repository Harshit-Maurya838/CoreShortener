package com.app.coreshortener.Repository;

import com.app.coreshortener.Models.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShortURLRepository extends JpaRepository<ShortUrl, UUID> {
    Optional<ShortUrl> findByTenantIdAndShortCode(UUID tenantId, String shortCode);
    Optional<ShortUrl> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<ShortUrl> findAllByTenantId(UUID tenantId, Pageable pageable);
    List<ShortUrl> findAllByTenantId(UUID tenantId);
    boolean existsByTenantIdAndShortCode(UUID tenantId, String shortCode);
    void deleteByTenantIdAndId(UUID tenantId, UUID id);
}
