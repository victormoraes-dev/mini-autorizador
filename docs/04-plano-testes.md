# Plano de testes

## Pirâmide

- Domínio: testes rápidos sem Spring.
- Aplicação: ports com fakes, sem banco.
- API: controllers e handler isolados.
- Integração: aplicação HTTP real e MySQL 5.7 por Testcontainers.

## Unitários de domínio

### `CardId`

- gera UUIDs diferentes;
- aceita UUID válido;
- recusa texto inválido;
- não contém PAN.

### `Card`

- emissão cria saldo `500.00` e hash;
- senha correta e saldo suficiente aprovam;
- senha incorreta tem precedência sobre saldo;
- saldo insuficiente recusa;
- débito reduz saldo sem permitir negativo;
- `toString` não contém PAN, senha ou hash.

### `Money`, `Balance` e credenciais

- zero, negativos e escala inválida;
- limites decimais;
- salt produz hashes diferentes;
- comparação correta/incorreta e hash malformado;
- masking para comprimentos variados.

## Unitários de aplicação

### Criação

- cria e retorna projeção segura;
- duplicidade prévia gera conflito;
- corrida na inserção gera o mesmo conflito;
- senha é hasheada uma única vez.

### Consulta

- busca por `CardId`;
- ausência gera `CardNotFoundException`;
- projeção não contém hash.

### Autorização

- cartão inexistente;
- senha inválida;
- saldo insuficiente;
- aprovação;
- falha concorrente no update;
- repositório recebe UUID, não PAN.

## Unitários da API

- criação retorna `201`, `Location` e DTO mascarado;
- consulta retorna DTO, nunca decimal primitivo;
- aprovação retorna `200` JSON;
- cada erro mapeia status, code, type e content type;
- validações listam campos sem valores;
- DTOs usam campos em inglês;
- OpenAPI marca password como `writeOnly`.

## Integração com Docker

Imagem: `mysql:5.7` — a mesma fornecida pelo cenário.

### IT-001 — Migrações

- iniciar schema vazio;
- aplicar V1 e V2;
- confirmar `public_id` único e obrigatório;
- iniciar contexto com `ddl-auto=validate`.

### IT-002 — Fluxo completo

1. criar cartão;
2. capturar UUID do body/Location;
3. consultar pelo UUID;
4. autorizar débito;
5. consultar saldo atualizado;
6. recusar saldo insuficiente;
7. recusar senha inválida;
8. recusar UUID inexistente.

Verificar status, JSON, Problem Details e estado no banco.

### IT-003 — Dados sensíveis

- responses não contêm PAN completo ou senha;
- banco contém hash, não senha;
- URL/Location contêm UUID, não PAN;
- erros de validação não ecoam payload;
- logs da aplicação usam cartão mascarado ou UUID.

### IT-004 — Duplicidade concorrente

Duas criações simultâneas do mesmo PAN: uma `201`, uma `409`, uma linha no banco.

### IT-005 — Débito concorrente

Saldo `10.00`, duas autorizações de `10.00`: uma `200`, uma `422 INSUFFICIENT_BALANCE`, saldo final `0.00`.

### IT-006 — API description

- `/v3/api-docs` lista somente `/api/v1/cards`, `/api/v1/cards/{cardId}` e `/api/v1/transactions`;
- Swagger UI responde;
- schemas e status estão presentes.

### IT-007 — Perfis

- `avaliacao`: mesmo contrato seguro, HTTP local permitido;
- `producao`: mesmo contrato, pepper obrigatório e HTTP bloqueado quando TLS não estiver indicado;
- teste produtivo pode desligar TLS apenas como override isolado para verificar serialização.

## Comandos

```bash
./mvnw test
./mvnw verify
```

## Critério final

- zero falhas/erros;
- nenhuma dependência de ordem entre testes;
- nenhum `sleep` para concorrência;
- Testcontainers encerra recursos;
- testes falham se PAN/senha reaparecerem em responses;
- árvore de trabalho contém apenas mudanças intencionais.
