# Backlog de implementação

## T01 — Consolidar contrato REST

Entregáveis:

- base `/api/v1`;
- recursos e campos em inglês;
- respostas JSON;
- status semânticos;
- Problem Details.

Aceite: nenhum controller retorna senha, PAN completo, `String` textual de negócio ou primitivo monetário isolado.

## T02 — Introduzir identidade pública do cartão

Entregáveis:

- value object `CardId` UUID;
- `Card` identificado por `CardId`;
- transação referencia `CardId`;
- repositório consulta e debita por `CardId`.

Aceite:

- PAN não aparece em URL;
- UUID inválido retorna `400` estruturado;
- ID não é derivado do PAN.

## T03 — Migrar banco

Entregáveis:

- migração Flyway V2 para `public_id`;
- backfill de registros existentes;
- unique constraint;
- JPA atualizado.

Aceite:

- banco vazio migra V1→V2;
- banco com cartões V1 recebe IDs sem perda de saldo/hash;
- Hibernate valida o schema.

## T04 — Criar cartão

Entregáveis:

- `POST /api/v1/cards`;
- request seguro;
- `CardResponse` mascarado;
- header `Location`;
- conflito estruturado.

Aceite:

- novo: `201`;
- duplicado: `409 CARD_ALREADY_EXISTS`;
- resposta nunca contém password/hash/PAN;
- saldo inicial `500.00`.

## T05 — Consultar cartão

Entregáveis:

- `GET /api/v1/cards/{cardId}`;
- projeção segura.

Aceite:

- existente: `200 CardResponse`;
- ausente: `404 CARD_NOT_FOUND`;
- PAN mascarado;
- body sempre JSON.

## T06 — Autorizar transação

Entregáveis:

- `POST /api/v1/transactions`;
- request por `cardId`;
- resposta `AUTHORIZED`;
- erros de negócio estruturados.

Aceite:

- aprovada: `200` e débito;
- cartão ausente: `404 CARD_NOT_FOUND`;
- senha inválida: `422 INVALID_PASSWORD`;
- saldo insuficiente: `422 INSUFFICIENT_BALANCE`;
- nenhuma transação persistida.

## T07 — Padronizar erros

Entregáveis:

- `HttpErrorHandler` com RFC 9457;
- códigos estáveis;
- violações de validação;
- tratamento de JSON malformado e UUID inválido.

Aceite:

- content type `application/problem+json`;
- sem stack trace/dados recebidos;
- `status` do body igual ao HTTP;
- `instance` corresponde ao path.

## T08 — Garantir concorrência

Entregáveis:

- criação protegida por unique constraints;
- débito condicional atômico por UUID;
- integração concorrente.

Aceite: duas transações disputando `10.00` produzem uma autorização, uma recusa e saldo `0.00`.

## T09 — Garantir segurança

Entregáveis:

- hash, salt e pepper;
- TLS produtivo;
- masking/redaction;
- DTOs sem segredos em responses;
- OpenAPI com password `writeOnly`.

Aceite: varredura de responses/logs de teste não encontra senha nem PAN completo.

## T10 — Atualizar OpenAPI

Entregáveis:

- três endpoints canônicos;
- exemplos de sucesso e erro;
- schemas de DTOs e Problem Details;
- Swagger UI.

Aceite: `/v3/api-docs` não lista endpoints legados e descreve todos os status usados.

## T11 — Testar

Entregáveis:

- unitários de domínio e aplicação;
- testes de controller/handler;
- integração MySQL 5.7 para fluxo, migração, concorrência e segurança;
- contextos `avaliacao` e `producao`.

Aceite: `./mvnw verify` passa integralmente com Docker.

## T12 — Documentar e versionar

Entregáveis:

- `docs/00` a `docs/08` consistentes;
- README com contrato implementado;
- commits por domínio/persistência, API e testes/documentação.

Aceite:

- descrições dos commits em PT-BR;
- `sdd/` ausente do índice;
- alteração do usuário em `docker/docker-compose.yml` preservada;
- handoff registra resultado final e riscos residuais.
