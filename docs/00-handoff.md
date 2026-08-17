# Handoff entre agentes — Mini Authorizer

## Diretriz vigente

O `README.md` define o comportamento de negócio: criar cartão com saldo inicial de `500.00`, consultar saldo e autorizar transações verificando existência, senha e saldo. Seus exemplos de URLs, payloads, bodies textuais e status não são o contrato HTTP da implementação quando conflitarem com segurança, semântica REST ou consistência da API.

Esta decisão substitui qualquer orientação anterior de compatibilidade literal. Nenhum agente deve reintroduzir senha em response, PAN em URL, respostas primitivas/textuais ou status inadequados apenas para copiar os exemplos do README.

## Contrato REST canônico

Base path: `/api/v1`.

| Operação | Endpoint | Resultado principal |
| --- | --- | --- |
| Criar cartão | `POST /api/v1/cards` | `201 Created`, `Location` e `CardResponse` |
| Consultar cartão e saldo | `GET /api/v1/cards/{cardId}` | `200 OK` e `CardResponse` |
| Autorizar transação | `POST /api/v1/transactions` | `200 OK` e `TransactionAuthorizationResponse` |

`cardId` é UUID opaco. O número real do cartão nunca deve aparecer em URL, response, log ou erro.

### Resposta de cartão

```json
{
  "id": "7c97bca5-3c85-4a2d-aab8-2d06112b56e4",
  "cardNumber": "************4501",
  "balance": 500.00
}
```

### Resposta de autorização

```json
{
  "status": "AUTHORIZED"
}
```

### Erros

Todos os erros usam `application/problem+json` seguindo RFC 9457 e incluem `code` estável em inglês.

| Condição | HTTP | `code` |
| --- | --- | --- |
| Payload inválido | `400` | `VALIDATION_ERROR` ou `MALFORMED_REQUEST` |
| Cartão não encontrado | `404` | `CARD_NOT_FOUND` |
| Cartão duplicado | `409` | `CARD_ALREADY_EXISTS` |
| Senha inválida | `422` | `INVALID_PASSWORD` |
| Saldo insuficiente | `422` | `INSUFFICIENT_BALANCE` |

Uma autorização aprovada retorna `200`, não `201`, porque a transação não é persistida como recurso.

## Segurança obrigatória

- Senha é aceita somente em request body e marcada como `writeOnly` no OpenAPI.
- Tráfego externo exige TLS; HTTP local é permitido apenas no perfil `avaliacao`.
- Senha é persistida com PBKDF2-HMAC-SHA256, salt aleatório e pepper.
- Responses nunca contêm senha ou hash.
- PAN é mascarado nos responses e não aparece em paths.
- Produção exige `APP_SECURITY_PASSWORD_PEPPER`, API keys externas e TLS.
- A API exige `X-API-Key`, separa os papéis `READER` e `WRITER` e aplica rate limiting por cliente.

## Arquitetura

- Java 21, Spring Boot 4.1.0 e Maven Wrapper.
- DDD com `Card` como aggregate root e regras nos métodos de domínio.
- Código, JSON e códigos de erro em inglês.
- `CardId`, `CardNumber`, `CardPassword`, `PasswordHash`, `Balance` e `Money` como value objects.
- Ports and Adapters entre domínio, aplicação, API e MySQL.
- MySQL 5.7 por ser a imagem fornecida; atualização para MySQL 8+ é necessária antes de produção.
- Flyway para migrações e Testcontainers para integração.
- OpenAPI/Swagger como documentação executável.

## Perfis

Os perfis possuem o mesmo contrato REST. A diferença é operacional:

- `avaliacao`: permite HTTP apenas em localhost e usa pepper local substituível;
- `producao`: exige pepper externo e TLS, inclusive quando TLS é sinalizado pelo proxy.

## Estado atual

- `CardId`, migração V2, API `/api/v1` e Problem Details estão implementados.
- Endpoints legados foram removidos.
- Banco existente em V1 foi migrado manualmente com sucesso para V2.
- Validação final em 2026-08-17: `./mvnw verify` concluído com sucesso.
- 50 testes unitários e 6 testes de integração cobrem os dois perfis.
- Os dois perfis usam o mesmo contrato seguro; produção também foi validada em contexto HTTP isolado com o filtro TLS desligado somente pelo teste.
- OpenAPI lista apenas os endpoints canônicos.
- Concorrência de criação e débito foi validada com MySQL 5.7 real.
- Não há tarefa de implementação pendente neste handoff.

## Regras de handoff

- Ler este arquivo antes dos demais documentos.
- Preservar alterações locais do usuário e não agrupar mudanças sem relação com o pacote ativo.
- Registrar decisões duráveis em `docs/`; `sdd/` é local, ignorado e não deve ser commitado.
- Usar Conventional Commits com tipo/escopo em inglês e descrição humana em português do Brasil.
- Não declarar conclusão sem suíte completa aprovada e árvore limpa, exceto pelas mudanças do usuário já identificadas.
