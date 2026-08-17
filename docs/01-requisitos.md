# Requisitos

## Regra de precedência

1. O README define as capacidades e regras de negócio.
2. Este documento define o contrato de API e os requisitos não funcionais.
3. Quando um exemplo HTTP do README conflitar com segurança ou boas práticas, este documento prevalece.

## Requisitos funcionais

### RF-001 — Criar cartão

Criar um cartão único com número e senha válidos, saldo inicial de `500.00` e identificador público UUID opaco.

Critérios de aceite:

- cartão novo retorna `201 Created`;
- response contém `id`, `cardNumber` mascarado e `balance`;
- header `Location` aponta para `/api/v1/cards/{cardId}`;
- senha não aparece no response;
- duplicidade não altera o registro e retorna `409 CARD_ALREADY_EXISTS`.

### RF-002 — Consultar cartão

Consultar o cartão pelo `cardId`, nunca pelo PAN.

Critérios de aceite:

- cartão existente retorna `200` e `CardResponse`;
- cartão inexistente retorna `404 CARD_NOT_FOUND` em Problem Details;
- número completo, senha e hash não aparecem no body.

### RF-003 — Autorizar transação

Autorizar quando o cartão existir, a senha estiver correta e houver saldo suficiente.

Ordem determinística:

1. cartão inexistente;
2. senha inválida;
3. saldo insuficiente.

Critérios de aceite:

- aprovação debita exatamente uma vez e retorna `200 {"status":"AUTHORIZED"}`;
- cartão inexistente retorna `404 CARD_NOT_FOUND`;
- senha inválida retorna `422 INVALID_PASSWORD`;
- saldo insuficiente retorna `422 INSUFFICIENT_BALANCE`;
- recusas não alteram saldo;
- transações não precisam ser persistidas.

### RF-004 — Concorrência

Débitos concorrentes não podem produzir saldo negativo ou dupla autorização sobre o mesmo saldo.

Critério: com saldo `10.00` e duas requisições simultâneas de `10.00`, uma é autorizada, outra recebe `422 INSUFFICIENT_BALANCE` e o saldo final é `0.00`.

## Contrato HTTP

### Criar cartão

```http
POST /api/v1/cards
Content-Type: application/json
```

```json
{
  "cardNumber": "6549873025634501",
  "password": "1234"
}
```

### Consultar cartão

```http
GET /api/v1/cards/{cardId}
```

### Autorizar transação

```http
POST /api/v1/transactions
Content-Type: application/json
```

```json
{
  "cardId": "7c97bca5-3c85-4a2d-aab8-2d06112b56e4",
  "password": "1234",
  "amount": 10.00
}
```

### Problem Details

```json
{
  "type": "urn:problem:insufficient-balance",
  "title": "Transaction denied",
  "status": 422,
  "detail": "The card does not have enough balance",
  "instance": "/api/v1/transactions",
  "code": "INSUFFICIENT_BALANCE"
}
```

Validações devem acrescentar uma propriedade `violations` com `field` e `message` sem reproduzir valores sensíveis.

## Requisitos não funcionais

### RNF-001 — DDD

Regras de senha, saldo e autorização ficam em entidades/value objects. Controllers apenas validam transporte, chamam casos de uso e mapeiam respostas.

### RNF-002 — Segurança

- TLS obrigatório fora do ambiente local.
- PAN ausente de URLs e mascarado em responses/logs.
- Senha somente em body protegido por TLS, nunca em URL, response ou log.
- Hash PBKDF2-HMAC-SHA256 com salt e pepper.
- Comparação de hash em tempo constante.
- Nenhum segredo hardcoded em produção.

### RNF-003 — Persistência

- MySQL 5.7 para compatibilidade com a imagem fornecida.
- Migrações Flyway versionadas.
- `BigDecimal`/`DECIMAL(19,2)` para valores.
- Restrição única para PAN e `cardId`.
- Débito condicional atômico no banco.

### RNF-004 — API

- Base versionada `/api/v1`.
- Substantivos plurais e nomes externos em inglês.
- JSON para respostas de sucesso.
- RFC 9457 para erros.
- Status HTTP semânticos.
- `application/json` e `application/problem+json` declarados.
- Sem envelopes genéricos como `data` quando não agregarem metadados.

### RNF-005 — Documentação

OpenAPI deve listar os três endpoints, schemas, exemplos, campos `writeOnly`, status e Problem Details. Swagger UI deve estar disponível localmente.

### RNF-006 — Testes

Cobrir domínio, aplicação, API, segurança, persistência, migração, concorrência e os dois perfis. Integrações usam a imagem `mysql:5.7` por Testcontainers.

### RNF-007 — Plataforma

- Java 21.
- Spring Boot 4.1.0.
- Maven e Maven Wrapper; Gradle não é permitido.

### RNF-008 — Linguagem

Classes, métodos, pacotes, campos JSON, códigos de erro e documentação OpenAPI da implementação devem usar inglês. Os documentos de decisão podem permanecer em PT-BR.

### RNF-009 — Git

Cada incremento lógico gera commit Conventional Commits. Tipo/escopo permanecem em inglês e a descrição após os dois-pontos é humana, imperativa e em português do Brasil. `sdd/` não é versionado.

## Decisões fora do README

| Decisão | Justificativa |
| --- | --- |
| UUID público `cardId` | Evita PAN em URL e desacopla identidade pública do número sensível |
| `409` para duplicidade | Representa conflito com estado atual do recurso |
| `200` para autorização aprovada | Nenhum recurso de transação é criado/persistido |
| Problem Details | Padroniza erros de forma legível por máquinas |
| Campos externos em inglês | Mantém contrato consistente com o código e padrões da solução |
| MySQL 5.7 | Imagem imposta pelo cenário; produção deve migrar para versão suportada |
| Transação não persistida | Permitido pelo README e suficiente para o escopo |
| Mesma API em ambos os perfis | Segurança e semântica não devem depender do ambiente |

## Fora do escopo

- emissão de PAN real;
- PCI DSS completo;
- autenticação OAuth2 do consumidor (a implementação local usa API keys com papéis);
- tokenização por HSM/vault externo;
- ledger/auditoria persistente de transações;
- idempotency keys e rate limiting distribuído no gateway (a aplicação possui limite local por cliente).

Esses itens são necessários em produção real, mas exigem infraestrutura e limites de negócio não fornecidos pelo desafio.
