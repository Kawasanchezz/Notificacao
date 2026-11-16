# Task Scheduler BFF

Backend de um app de agendamento de tarefas, construído como um único ponto de entrada em Spring Boot (um
backend-for-frontend) com segurança JWT centralizada. Também serve uma página web simples, para testar a API sem
ferramentas extras.

- **PostgreSQL**: usuários, endereços e telefones (Spring Data JPA)
- **MongoDB**: tarefas (Spring Data MongoDB)
- **ViaCEP**: preenche o endereço a partir de um CEP
- **Swagger UI**: documentação interativa da API

## Tecnologias

Java 21, Spring Boot 4.0 (Web MVC, Security, Validation, Data JPA, Data MongoDB, Actuator), jjwt, springdoc-openapi,
Docker Compose, JUnit 5 e MockMvc.

## Arquitetura

```
Navegador / cliente ──> BFF Spring Boot ──> PostgreSQL   (usuários, endereços, telefones)
                         │  segurança JWT ──> MongoDB      (tarefas)
                         └────────────────> ViaCEP        (consulta de CEP)
```

```
src/main/java/com/taskscheduler
├── auth        cadastro e login
├── user        perfil, endereços, telefones (entidades JPA, serviço, controller)
├── task        documentos e endpoints de tarefas (MongoDB)
├── viacep      cliente do ViaCEP e endpoint de consulta de CEP
├── security    serviço JWT e filtros (autenticação, limite de tentativas, tamanho do corpo, CSP)
├── config      cadeia de segurança, OpenAPI, propriedades tipadas
└── exception   tratamento de erros em problem+json
```

## Rodar com Docker (não precisa de Java)

```bash
cp .env.example .env          # depois edite o .env e troque todos os valores
docker compose --profile app up --build
```

Abra http://localhost:8080/ para a página web ou http://localhost:8080/swagger-ui.html para a documentação da API
(cadastre-se, copie o `accessToken`, clique em **Authorize** e cole o token).

Se a porta 5432 ou 27017 já estiver em uso na sua máquina, defina `POSTGRES_PORT` / `MONGO_PORT` no `.env`.

Para parar: `docker compose --profile app down` (acrescente `-v` para apagar também os dados).

## Rodar localmente (JDK 21 e Maven)

```bash
docker compose up -d          # só os bancos de dados
export POSTGRES_PASSWORD=... JWT_SECRET=...      # mesmos valores do .env
export MONGODB_URI="mongodb://taskscheduler:<MONGO_PASSWORD>@localhost:27017/taskscheduler?authSource=admin"
mvn spring-boot:run
```

## Testes

Os testes unitários não precisam de nada; os de integração (`*IT`) usam os bancos acima e as mesmas variáveis de
ambiente.

```bash
mvn verify
```

## Endpoints

| Método | Caminho | Observações |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | públicos, retornam um JWT |
| GET, PUT | `/api/users/me` | perfil |
| GET, POST | `/api/users/me/addresses` | envie `cep` + `number`; o resto vem do ViaCEP |
| PUT, DELETE | `/api/users/me/addresses/{id}` | |
| GET, POST | `/api/users/me/phones` | |
| PUT, DELETE | `/api/users/me/phones/{id}` | |
| GET | `/api/cep/{cep}` | consulta no ViaCEP |
| POST, GET | `/api/tasks` | a listagem é paginada: `?status=&page=&size=` |
| GET, DELETE | `/api/tasks/{id}` | |
| PATCH | `/api/tasks/{id}/status` | `PENDING`, `IN_PROGRESS`, `DONE`, `CANCELED` |

Os erros seguem o formato RFC 9457 `application/problem+json`. Cada usuário só enxerga os próprios dados; ids de outros
usuários retornam 404.

## Configuração

Todos os valores vêm de variáveis de ambiente; veja o [.env.example](.env.example). `JWT_SECRET`,
`POSTGRES_PASSWORD` e `MONGO_PASSWORD` são obrigatórios e não têm valor padrão.

## Segurança

Veja o [SECURITY.md](SECURITY.md) para saber o que é protegido e quais são as limitações conhecidas.

## Licença

[MIT](LICENSE)
