package com.app.coreshortener.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShortUrlRequest {

    @Size(max = 2048, message = "Original URL cannot exceed 2048 characters")
    @Pattern(regexp = "^$|^https?://[^\\s/$.?#].[^\\s]*$", message = "Original URL must be a valid HTTP or HTTPS URL")
    private String originalUrl;

    private Boolean isActive;

    private LocalDateTime expiresAt;
}

