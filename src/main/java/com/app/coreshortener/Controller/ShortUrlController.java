package com.app.coreshortener.Controller;

import com.app.coreshortener.dto.CreateShortUrlRequest;
import com.app.coreshortener.dto.PagedResponse;
import com.app.coreshortener.dto.ShortUrlResponse;
import com.app.coreshortener.dto.UpdateShortUrlRequest;
import com.app.coreshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/urls", "/urls"})
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    @PostMapping
    public ResponseEntity<ShortUrlResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrlResponse response = shortUrlService.createShortUrl(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ShortUrlResponse>> listShortUrls(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shortUrlService.listShortUrls(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> getShortUrlById(@PathVariable UUID id) {
        return ResponseEntity.ok(shortUrlService.getShortUrlById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> updateShortUrl(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShortUrlRequest request) {
        return ResponseEntity.ok(shortUrlService.updateShortUrl(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable UUID id) {
        shortUrlService.deleteShortUrl(id);
        return ResponseEntity.noContent().build();
    }
}

