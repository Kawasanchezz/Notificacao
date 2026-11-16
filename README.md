# Task Scheduler BFF

Backend for a task scheduling app, built as a single Spring Boot entry point (a backend-for-frontend) with
centralized JWT security. It also serves a small web page so the API can be tried without extra tooling.

- **PostgreSQL**: users, addresses and phone numbers (Spring Data JPA)
- **MongoDB**: tasks (Spring Data MongoDB)
- **ViaCEP**: fills in an address from a Brazilian postal code (CEP)
- **Swagger UI**: interactive API docs

## Tech stack

Java 21, Spring Boot 3.5 (Web, Security, Validation, Data JPA, Data MongoDB, Actuator), jjwt, springdoc-openapi,
Docker Compose, JUnit 5 and MockMvc.

## Architecture

```
Browser / client ──> Spring Boot BFF ──> PostgreSQL   (users, addresses, phones)
                      │   JWT security   ──> MongoDB      (tasks)
                      └─────────────────> ViaCEP        (CEP lookup)
```

```
src/main/java/com/taskscheduler
├── auth        sign up and log in
├── user        profile, addresses, phones (JPA entities, service, controller)
├── task        task documents and endpoints (MongoDB)
├── viacep      ViaCEP client and CEP lookup endpoint
├── security    JWT service and servlet filters (auth, rate limit, body size, CSP)
├── config      security chain, OpenAPI, typed properties
└── exception   problem+json error handling
```

## Run with Docker (no Java needed)

```bash
cp .env.example .env          # then edit .env and replace every value
docker compose --profile app up --build
```

Open http://localhost:8080/ for the web page or http://localhost:8080/swagger-ui.html for the API docs
(register, copy the `accessToken`, click **Authorize**, paste it).

If port 5432 or 27017 is taken on your machine, set `POSTGRES_PORT` / `MONGO_PORT` in `.env`.

Stop with `docker compose --profile app down` (add `-v` to also delete the data).

## Run locally (JDK 21 and Maven)

```bash
docker compose up -d          # databases only
export POSTGRES_PASSWORD=... JWT_SECRET=...      # same values as in .env
export MONGODB_URI="mongodb://taskscheduler:<MONGO_PASSWORD>@localhost:27017/taskscheduler?authSource=admin"
mvn spring-boot:run
```

## Tests

Unit tests need nothing; integration tests (`*IT`) use the databases above plus the same environment variables.

```bash
mvn verify
```

## Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public, return a JWT |
| GET, PUT | `/api/users/me` | profile |
| GET, POST | `/api/users/me/addresses` | send `cep` + `number`; the rest comes from ViaCEP |
| PUT, DELETE | `/api/users/me/addresses/{id}` | |
| GET, POST | `/api/users/me/phones` | |
| PUT, DELETE | `/api/users/me/phones/{id}` | |
| GET | `/api/cep/{cep}` | ViaCEP lookup |
| POST, GET | `/api/tasks` | list is paged: `?status=&page=&size=` |
| GET, DELETE | `/api/tasks/{id}` | |
| PATCH | `/api/tasks/{id}/status` | `PENDING`, `IN_PROGRESS`, `DONE`, `CANCELED` |

Errors use RFC 9457 `application/problem+json`. A user only ever sees their own data; other users' ids return 404.

## Configuration

All values come from environment variables; see [.env.example](.env.example). `JWT_SECRET`, `POSTGRES_PASSWORD`
and `MONGO_PASSWORD` are required and have no defaults.

## Security

See [SECURITY.md](SECURITY.md) for what is protected and the known limitations.

## License

[MIT](LICENSE)
