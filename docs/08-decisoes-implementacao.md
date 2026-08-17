# Decisões efetivamente implementadas

## Precedência

O README fornece regras de negócio, não o contrato HTTP. Formatos inseguros ou semanticamente inadequados foram substituídos pelo contrato canônico documentado em `00-handoff.md`.

## Plataforma

- Java 21.
- Spring Boot 4.1.0.
- Maven Wrapper 3.9.11.
- SpringDoc OpenAPI 3.0.3.
- Flyway pelo starter modular do Spring Boot 4 e `flyway-mysql`.
- MySQL 5.7 pela imagem do desafio.

## API

- Base path `/api/v1`.
- `POST /api/v1/cards` retorna `201`, `Location` e `CardResponse`.
- `GET /api/v1/cards/{cardId}` retorna `CardResponse`.
- `POST /api/v1/transactions` retorna `200 {"status":"AUTHORIZED"}` quando aprovada.
- Endpoints legados em português não são expostos.
- Campos JSON e códigos usam inglês.
- Erros usam RFC 9457 com `application/problem+json`, `code` e, em validação, `violations`.
- Duplicidade retorna `409`; cartão ausente `404`; recusas por senha/saldo `422`.

## DDD

- `Card` é aggregate root e concentra emissão, autorização e débito.
- `CardId` UUID é a identidade pública opaca.
- `CardNumber`, `CardPassword`, `PasswordHash`, `Balance` e `Money` são value objects.
- `Transaction` referencia `CardId`, não PAN.
- Aplicação retorna `CardDetails`, uma projeção sem hash.
- DTOs e entidades JPA não são usados como domínio.

## Persistência e concorrência

- V1 cria `cards`; V2 adiciona/backfilla `public_id` e cria unicidade.
- A V2 foi validada tanto em schema vazio quanto sobre banco existente em V1.
- Criação usa `INSERT IGNORE` com unicidade em PAN e UUID.
- Consulta e débito usam `public_id`.
- Débito atômico condiciona `balance >= amount`.
- Transações não são persistidas.

## Segurança

- Responses contêm somente UUID, PAN mascarado e saldo quando aplicável.
- PAN não aparece em URL ou `Location`.
- Password existe apenas nos request bodies e é `writeOnly` no OpenAPI.
- Persistência usa PBKDF2-HMAC-SHA256, salt aleatório, 210.000 iterações, chave de 256 bits e pepper.
- Logs da aplicação usam PAN mascarado ou UUID.
- Avaliação escuta em `127.0.0.1` e permite HTTP local.
- Produção exige pepper externo e TLS por padrão.
- PAN em texto claro no banco é risco residual do assessment; produção real deve tokenizar.

## Testes

- Unitários cobrem domínio, aplicação, API, Problem Details, mapeamento e segurança.
- Integrações usam Testcontainers 2 com `mysql:5.7`.
- Fluxo completo, schemas OpenAPI, endpoints legados ausentes, dados sensíveis, migração e concorrência são verificados.
- Perfis de avaliação e produção usam o mesmo contrato seguro.

## Riscos conhecidos

- Hibernate 7.4 informa que MySQL 5.7 está fora da faixa suportada. A suíte confirma o comportamento necessário ao desafio, mas produção deve usar MySQL 8+.
- OAuth2/OIDC, token vault, rate limiting distribuído e idempotency keys dependem de infraestrutura não fornecida. A API implementa API keys com papéis e limite local por cliente.
