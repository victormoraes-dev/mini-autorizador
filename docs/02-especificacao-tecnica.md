# Especificação técnica

## Visão arquitetural

```text
HTTP / OpenAPI
      │
      ▼
Controllers + DTOs + Problem Details
      │
      ▼
Input ports / Application services
      │
      ▼
Rich domain model
      │
      ▼
Repository port ──► JPA adapter ──► MySQL
```

Dependências apontam para dentro. Domínio não conhece Spring, HTTP, Jackson ou JPA.

## Modelo de domínio

### `Card`

Aggregate root com:

- `CardId id` — UUID público opaco;
- `CardNumber number` — PAN/identificador sensível;
- `PasswordHash passwordHash`;
- `Balance balance`.

Métodos:

- `issue(...)` cria ID, hash e saldo inicial;
- `restore(...)` reconstitui persistência;
- `authorize(Transaction, PasswordHasher)` aplica senha e saldo;
- `debit(Money)` mantém invariantes do saldo.

### `Transaction`

Representa uma tentativa e contém `CardId`, senha fornecida e valor. Não é persistida.

### Value objects

- `CardId`: gera e valida UUID;
- `CardNumber`: valida e mascara;
- `CardPassword`: segredo transitório;
- `PasswordHash`: formato persistido;
- `Money`: valor positivo com escala válida;
- `Balance`: valor não negativo e operação de débito.

## Casos de uso

### Criação

1. Validar DTO.
2. Criar value objects.
3. Verificar duplicidade por número.
4. Emitir aggregate com UUID e hash.
5. Inserir usando restrição única.
6. Em corrida, converter falha em `CardAlreadyExistsException`.
7. Retornar `CardDetails` sem hash.

### Consulta

1. Converter path para `CardId`.
2. Buscar aggregate por ID público.
3. Ausência gera `CardNotFoundException`.
4. Retornar projeção segura com número mascarado e saldo.

### Autorização

1. Validar DTO e criar `Transaction`.
2. Buscar cartão por `CardId`.
3. Se ausente, `CARD_NOT_FOUND`.
4. Entidade verifica senha.
5. Entidade verifica saldo.
6. Aplicação executa débito condicional atômico por `cardId`.
7. Zero linhas após autorização em memória significa saldo perdido por concorrência.

## Persistência

Tabela `cards`:

| Coluna | Tipo | Regra |
| --- | --- | --- |
| `id` | `BIGINT` | PK interna |
| `public_id` | `CHAR(36)` | UUID público, único, obrigatório |
| `card_number` | `VARCHAR(32)` | único, sensível |
| `password_hash` | `VARCHAR(255)` | PBKDF2 codificado |
| `balance` | `DECIMAL(19,2)` | não negativo pela aplicação |
| `created_at` | `TIMESTAMP(6)` | gerenciado pelo banco |
| `updated_at` | `TIMESTAMP(6)` | gerenciado pelo banco |

Débito:

```sql
UPDATE cards
   SET balance = balance - :amount,
       updated_at = CURRENT_TIMESTAMP(6)
 WHERE public_id = :cardId
   AND balance >= :amount;
```

`rowsAffected == 1` aprova; `0` recusa por concorrência/saldo.

## Contrato REST

### `POST /api/v1/cards`

Request `CreateCardRequest`:

- `cardNumber`: obrigatório, máximo 32;
- `password`: obrigatório, máximo 72, OpenAPI `writeOnly`.

Response `201 CardResponse`:

- `id`: UUID;
- `cardNumber`: mascarado;
- `balance`: decimal.

Inclui `Location: /api/v1/cards/{id}`.

### `GET /api/v1/cards/{cardId}`

Response `200 CardResponse`. O path usa UUID e o body usa somente PAN mascarado.

### `POST /api/v1/transactions`

Request `AuthorizeTransactionRequest`:

- `cardId`: UUID obrigatório;
- `password`: obrigatório e `writeOnly`;
- `amount`: decimal positivo com duas casas.

Response aprovada: `200 {"status":"AUTHORIZED"}`.

## Erros RFC 9457

`HttpErrorHandler` centraliza tradução. O domínio não conhece HTTP.

Propriedades padrão: `type`, `title`, `status`, `detail`, `instance`. Extensões:

- `code`: código estável;
- `violations`: lista sem valores recebidos, apenas campo e mensagem.

Não incluir stack trace, exception class, senha, PAN ou hash.

## Segurança

- PBKDF2-HMAC-SHA256, salt aleatório, 210.000 iterações, chave de 256 bits e pepper.
- TLS obrigatório no perfil de produção.
- API key obrigatória, com papéis separados de leitura e escrita.
- Rate limiting local por impressão SHA-256 da credencial ou IP.
- `X-Forwarded-Proto` aceito apenas com proxy confiável configurado.
- DTOs nunca expõem aggregate/JPA diretamente.
- `toString` mascara PAN e redacta hash/senha.
- PAN permanece em texto claro no banco apenas por limitação do desafio; tokenização é recomendada para produção.

## OpenAPI

- `@OpenAPIDefinition` para metadados.
- `@Tag`, `@Operation` e `@ApiResponse` nos controllers.
- `@Schema` nos DTOs.
- Passwords como `WRITE_ONLY`.
- Schemas de Problem Details para `400`, `401`, `403`, `404`, `409`, `422`, `429` e `500`.
- Security scheme `apiKey` no header `X-API-Key`.
- Swagger UI em `/swagger-ui/index.html`.

## Perfis

O contrato é idêntico em `avaliacao` e `producao`.

| Configuração | avaliação | produção |
| --- | --- | --- |
| HTTP local | permitido | bloqueado por padrão |
| Pepper | fallback local | variável obrigatória |
| API keys | variáveis obrigatórias | variáveis obrigatórias |
| Responses | seguros | seguros |
| URLs | UUID | UUID |

## Plataforma

- Java 21;
- Spring Boot 4.1.0;
- Maven Wrapper 3.9.11;
- Spring Data JPA;
- Flyway;
- MySQL 5.7;
- SpringDoc OpenAPI;
- JUnit 5, AssertJ e Testcontainers 2.
