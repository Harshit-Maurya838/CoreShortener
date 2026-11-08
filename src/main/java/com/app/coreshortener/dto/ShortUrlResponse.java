package com.app.coreshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrlResponse {
    private UUID id;
    private UUID tenantId;
    private String originalUrl;
    private String shortCode;
    private String shortUrl;
    private UUID createdBy;
    private LocalDateTime expiresAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

