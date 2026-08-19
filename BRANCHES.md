# Estratégia de branches e comparação de contratos

Este repositório mantém duas implementações deliberadamente diferentes do
mesmo exercício. A diferença é externa e intencional: ela permite comparar o
contrato descrito literalmente no enunciado com um contrato REST seguro e
evolutivo.

## Referências

| Ref | Finalidade | Uso recomendado |
| --- | --- | --- |
| `main` | Implementação robusta e contrato canônico | Desenvolvimento, revisão e produção |
| `reference-good-practices` | Ponteiro estável para a mesma referência robusta | Comparação explícita |
| `feature/api-following-good-practices` | Histórico de desenvolvimento da solução robusta | Auditoria de commits |
| `reference-readme-literal` | Ponteiro estável para a implementação literal | Demonstração e estudo |
| `feature/api-following-readme-literal` | Histórico de desenvolvimento do contrato literal | Auditoria de commits |

A branch literal não é candidata a produção. Ela existe para tornar visíveis
as consequências de seguir cada detalhe do README quando ele conflita com boas
práticas de API.

## Diferenças principais

| Tema | `main` / boas práticas | Implementação literal |
| --- | --- | --- |
| Rotas | `/api/v1/cards` e `/api/v1/transactions` | `/cartoes` e `/transacoes` |
| Identificador externo | UUID opaco | Número do cartão |
| Respostas | JSON consistente e Problem Details para erros | JSON/texto e bodies enumerados no README |
| Status de transação aprovada | `200 OK` | `201 Created` com `OK` em texto |
| Dados sensíveis | PAN mascarado; password e hash não são expostos | Criação devolve número e password, conforme o README |
| Segurança | API key, papéis, rate limiting e TLS em produção | Não adicionada, pois não foi exigida pelo README |
| Contrato | OpenAPI e Swagger UI | Ausentes, pois não foram exigidos pelo README |
| Validação de erros | Validação e erros estruturados | Somente os comportamentos explicitamente pedidos |

As duas implementações preservam as regras de negócio que não conflitam com o
contrato externo: saldo inicial, password persistida como hash, Flyway e débito
atômico para concorrência.

## Como explorar

Confira o diff entre os contratos:

```bash
git diff main..reference-readme-literal
```

Execute a referência robusta:

```bash
git switch main
```

Execute o contrato literal:

```bash
git switch feature/api-following-readme-literal
```

Depois de trocar de branch, siga as instruções de execução no README daquela
branch. As credenciais de API e o Swagger UI pertencem somente à implementação
robusta.
