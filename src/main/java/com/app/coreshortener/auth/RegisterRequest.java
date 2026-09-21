package com.app.coreshortener.auth;

import com.app.coreshortener.Models.Role;

import java.util.UUID;

public record RegisterRequest(UUID tenantId, String email, String password, Role role) {
}
