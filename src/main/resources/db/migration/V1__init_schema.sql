-- Tenants Table
CREATE TABLE tenants (
                         id UUID PRIMARY KEY,
                         name VARCHAR(100) NOT NULL,
                         api_key VARCHAR(255) UNIQUE NOT NULL,
                         plan VARCHAR(50) NOT NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users Table
CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       tenant_id UUID NOT NULL REFERENCES tenants(id),
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(50) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Short URLs Table
CREATE TABLE short_urls (
                            id UUID PRIMARY KEY,
                            tenant_id UUID NOT NULL REFERENCES tenants(id),
                            original_url TEXT NOT NULL,
                            short_code VARCHAR(20) UNIQUE NOT NULL,
                            created_by UUID REFERENCES users(id),
                            expires_at TIMESTAMP,
                            is_active BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);