CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_short_urls_tenant_id ON short_urls(tenant_id);
CREATE INDEX idx_short_urls_tenant_short_code ON short_urls(tenant_id, short_code);