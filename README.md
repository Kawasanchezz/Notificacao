# Task Scheduler BFF

Single Spring Boot entry point (Java 21, Spring Boot 3.5) with centralized JWT security.

- **PostgreSQL**: users, addresses, phones (JPA)
- **MongoDB**: tasks
- **ViaCEP**: address lookup by CEP

## Run

```bash
docker compose up -d                     # databases only; then:
mvn spring-boot:run                      # needs JDK 21 + Maven
# or everything in Docker:
docker compose --profile app up --build
```

Swagger UI: http://localhost:8080/swagger-ui.html (use **Authorize** with the token from login/register).

## Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | public, return a JWT |
| GET/PUT | /api/users/me | profile |
| GET/POST | /api/users/me/addresses | send `cep` + `number`; the rest comes from ViaCEP |
| PUT/DELETE | /api/users/me/addresses/{id} | |
| GET/POST | /api/users/me/phones | |
| PUT/DELETE | /api/users/me/phones/{id} | |
| GET | /api/cep/{cep} | ViaCEP lookup |
| POST/GET | /api/tasks | list is paged, `?status=&page=&size=` |
| GET/DELETE | /api/tasks/{id} | |
| PATCH | /api/tasks/{id}/status | PENDING, IN_PROGRESS, DONE, CANCELED |

Errors are RFC 9457 `application/problem+json`. Users can only see their own tasks (others return 404).

## Config (env vars)

`JWT_SECRET` (>= 32 chars, **change it**), `JWT_EXPIRATION_MINUTES`, `POSTGRES_URL/USER/PASSWORD`, `MONGODB_URI`, `VIACEP_URL`.

Note: the schema uses `ddl-auto: update` for convenience; adopt Flyway/Liquibase before production.
