# Estratégia de branches e comparação de contratos

Este repositório contém duas implementações deliberadamente distintas do mesmo
exercício. Esta branch reproduz o contrato do README literalmente; a `main`
mantém o contrato REST recomendado para evolução e produção.

| Ref | Finalidade | Uso recomendado |
| --- | --- | --- |
| `main` | Implementação robusta e contrato canônico | Desenvolvimento e produção |
| `reference-good-practices` | Ponteiro estável para a referência robusta | Comparação explícita |
| `feature/api-following-good-practices` | Histórico da solução robusta | Auditoria de commits |
| `reference-readme-literal` | Ponteiro estável para este contrato literal | Demonstração e estudo |
| `feature/api-following-readme-literal` | Histórico desta solução literal | Auditoria de commits |

## Diferenças principais

| Tema | `main` / boas práticas | Implementação literal |
| --- | --- | --- |
| Rotas | `/api/v1/cards` e `/api/v1/transactions` | `/cartoes` e `/transacoes` |
| Identificador externo | UUID opaco | Número do cartão |
| Respostas | JSON consistente e Problem Details | JSON/texto descritos no README |
| Transação aprovada | `200 OK` | `201 Created` com `OK` em texto |
| Dados sensíveis | PAN mascarado; password não é exposta | Criação devolve número e password, conforme o README |
| Segurança | API key, papéis, rate limiting e TLS | Não adicionada, pois não foi exigida pelo README |
| Contrato | OpenAPI e Swagger UI | Ausentes, pois não foram exigidos pelo README |

As duas versões mantêm as regras de negócio que não conflitam com o contrato
externo: saldo inicial, hash de password, migrações Flyway e débito atômico.

## Como comparar

```bash
git diff main..reference-readme-literal
git switch main
git switch feature/api-following-readme-literal
```

A implementação literal existe exclusivamente para estudo e comparação. Não a
utilize como base para produção.
