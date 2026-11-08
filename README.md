# CoreShortener — Multi-Tenant URL Shortener

A production-style URL shortener built with **Spring Boot 3**, where every tenant's data is isolated and access is controlled by role.

> Status: **Week 1 complete** (setup, JWT auth, multi-tenant RBAC, short URL CRUD, unit tests).
> Coming next: async click tracking, Redis caching, analytics, scheduled jobs, observability, Docker + CI.

## Tech Stack

| Layer        | Technology                                   |
|--------------|----------------------------------------------|
| Language     | Java 21, Spring Boot 3.4                     |
| Security     | Spring Security 6, JWT (access + refresh)    |
| Persistence  | Spring Data JPA, PostgreSQL 16, Flyway       |
| Caching      | Redis 7 *(wired, used from Week 2)*          |
| Testing      | JUnit 5, Mockito, AssertJ                    |

## Getting Started

```bash
# 1. Start Postgres + Redis (+ Redis Commander on :8081)
docker compose up -d

# 2. Run the app (dev profile; Flyway applies migrations on startup)
./mvnw spring-boot:run

# 3. Run unit tests
./mvnw test -Dtest='*ServiceTest,ShortCodeGeneratorTest'
```

Profiles: `dev` (default), `test`, `prod`. In `prod`, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `JWT_SECRET` and `BASE_URL`.

## API

### Auth (public)

| Method | Path                            | Description                                             |
|--------|---------------------------------|---------------------------------------------------------|
| POST   | `/api/v1/auth/register-tenant`  | Create a tenant and its first user as **ADMIN**         |
| POST   | `/api/v1/auth/register`         | Join an existing tenant as **MEMBER** (`tenantId` required) |
| POST   | `/api/v1/auth/login`            | Get an access token and a refresh token                 |
| POST   | `/api/v1/auth/refresh`          | Swap a refresh token for a new access token             |

### Short URLs (need `Authorization: Bearer <accessToken>`)

Also available under `/api/v1/urls`.

| Method | Path          | Description                                                   |
|--------|---------------|---------------------------------------------------------------|
| POST   | `/urls`       | Create a short URL (optional `customCode`, `expiresAt`)       |
| GET    | `/urls`       | List URLs with pagination (ADMIN: whole tenant, MEMBER: own)  |
| GET    | `/urls/{id}`  | Get one URL                                                   |
| PUT    | `/urls/{id}`  | Partial update (`originalUrl`, `isActive`, `expiresAt`)       |
| DELETE | `/urls/{id}`  | Soft delete (sets `isActive = false`)                         |

### Redirect (public)

| Method | Path           | Description                                                   |
|--------|----------------|---------------------------------------------------------------|
| GET    | `/{shortCode}` | `302` to the original URL · `404` if unknown/inactive · `410` if expired |

### Example

```bash
# Register a tenant admin
curl -s -X POST localhost:8080/api/v1/auth/register-tenant \
  -H 'Content-Type: application/json' \
  -d '{"tenantName":"Acme","email":"admin@acme.io","password":"password123"}'

# Create a short URL
curl -s -X POST localhost:8080/urls \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://spring.io","customCode":"spring"}'

# Follow it
curl -i localhost:8080/spring
```

## Design Decisions (so far)

- **The tenant comes from the JWT, never from a request header.** `JwtAuthenticationFilter` reads `tenantId` from the
  signed access token and puts it in a request-scoped `TenantContext` (a ThreadLocal), which is cleared in `finally`.
- **Every query is filtered by tenant.** Management endpoints look rows up with `findByTenantIdAndId(...)`, so a URL that
  belongs to another tenant returns `404`, which doesn't even confirm it exists.
- **Role-based access control.** ADMIN can manage every URL in their tenant. MEMBER can manage only the URLs they created
  (`403` otherwise).
- **Short codes.** Generated codes are 7 random base62 characters from `SecureRandom` (62⁷ ≈ 3.5 trillion possible codes).
  On a collision the service retries up to 5 times. Custom codes are checked against a list of reserved words (`urls`,
  `api`, `auth`, …).
- **Two requests racing for the same custom code.** The `existsByShortCode` pre-check gives a friendly error in the normal
  case. The `UNIQUE` constraint on `short_code` is the real guarantee: the insert uses `saveAndFlush`, so if two requests
  race, the losing one hits the constraint inside the transaction and gets a `409 Conflict` instead of a `500`.
- **Soft delete.** Links are deactivated, not removed. This keeps history for click analytics later on.
- **Consistent errors.** A `@RestControllerAdvice` returns every error in the shape
  `{timestamp, status, error, message}`, and validation errors come back as a `details` map. Internal exception messages
  are logged on the server and never sent to clients.

## Testing

Unit tests (JUnit 5 + Mockito, no Spring context):

- `ShortUrlServiceTest`: code generation and collision retry, custom and reserved codes, the race-to-409 mapping,
  tenant isolation, MEMBER vs ADMIN access, soft delete, and redirect resolution (active, inactive, expired).
- `AuthServiceTest`: tenant + admin signup, member signup, login, refresh-token rules, BCrypt hashing.
- `JwtServiceTest`: token claims, access vs refresh token type, rejection of tampered and expired tokens.
- `ShortCodeGeneratorTest`: base62 encoding, code format, reserved words.

`CoreShortenerApplicationTests` needs a running Postgres. In Week 2 it moves to Testcontainers.

## Roadmap

- [x] Week 1: setup, auth, RBAC, CRUD, unit tests
- [ ] Week 2: async click logging, Redis caching, transactional integrity, Testcontainers
- [ ] Week 3: analytics aggregation, scheduled jobs, observability
- [ ] Week 4: rate limiting, load testing, Docker + CI, deployment
