package com.app.coreshortener.Repository;

import com.app.coreshortener.Models.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShortURLRepository extends JpaRepository<ShortUrl, UUID> {
}
