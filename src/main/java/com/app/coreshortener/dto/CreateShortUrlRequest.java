package com.app.coreshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
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
public class CreateShortUrlRequest {

    @NotBlank(message = "Original URL is required")
    @Size(max = 2048, message = "Original URL cannot exceed 2048 characters")
    @Pattern(regexp = "^(https?://)[^\\s/$.?#].[^\\s]*$", message = "Original URL must be a valid HTTP or HTTPS URL")
    private String originalUrl;

    @Pattern(regexp = "^$|^[a-zA-Z0-9_-]{3,20}$", message = "Custom code must be between 3 and 20 alphanumeric characters, hyphens, or underscores")
    private String customCode;

    @Future(message = "Expiration time must be in the future")
    private LocalDateTime expiresAt;
}

