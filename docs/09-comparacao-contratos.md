# Comparação entre os contratos

## Intenção das branches

- `feature/api-following-good-practices` é a referência técnica: contrato REST versionado, representações seguras, erros estruturados, autenticação/autorização, rate limiting e OpenAPI.
- `feature/api-following-readme-literal` deriva da referência e reproduz os exemplos do README como especificação autoritativa, inclusive quando conflitam com essas práticas.

## Divergências intencionais

| Instrução literal do README | Branch literal | Decisão na branch de boas práticas | Motivo técnico da referência |
| --- | --- | --- | --- |
| URLs `/cartoes` e `/transacoes` | Mantém paths em português, sem versão | Usa `/api/v1/cards` e `/api/v1/transactions` | Versionamento e nomenclatura consistente permitem evolução do contrato |
| Payloads `numeroCartao`, `senha`, `senhaCartao`, `valor` | Mantém os nomes exatamente | Usa `cardNumber`, `password`, `cardId`, `amount` | Linguagem uniforme e UUID opaco evitam usar PAN como identidade pública |
| Criação devolve número e senha | Retorna `201` com ambos em JSON | Retorna UUID, PAN mascarado e saldo; nunca retorna senha | Credenciais e PAN completo não devem aparecer em responses |
| Cartão duplicado devolve `422` e repete número/senha | Reproduz status e body | Retorna `409` em RFC 9457 | Duplicidade é conflito com o estado atual e o erro não deve ecoar segredos |
| Saldo é consultado pelo número do cartão | Usa `GET /cartoes/{numeroCartao}` | Consulta por `cardId` UUID | PAN em URL vaza em logs, traces, caches e histórico de navegação |
| Saldo de sucesso é apenas `495.15` | Retorna texto puro | Retorna um recurso JSON com id, PAN mascarado e saldo | Representação extensível e consistente evita primitivo isolado |
| Cartão ausente na consulta tem `404` sem body | Retorna exatamente sem body | Retorna `404 application/problem+json` | Erro estruturado oferece código estável e contexto uniforme |
| Autorização aprovada devolve `201 OK` | Retorna texto puro e status `201` | Retorna `200 {"status":"AUTHORIZED"}` | Nenhum recurso de transação é persistido; portanto nada foi criado |
| Toda recusa de transação usa `422` | Inclusive cartão inexistente | Usa `404` para cartão ausente e `422` para senha/saldo | Status diferencia recurso inexistente de regra de negócio não processável |
| Motivos `SALDO_INSUFICIENTE`, `SENHA_INVALIDA`, `CARTAO_INEXISTENTE` | Retorna strings literais | Retorna Problem Details com códigos em inglês | JSON padronizado preserva estabilidade sem bodies ad hoc |
| README não define erros adicionais ou validação | Depende apenas das invariantes internas e defaults do framework | Valida DTOs e trata `400`, `404`, `409`, `422` e `500` | Fronteira HTTP deve rejeitar input inválido e não vazar falhas internas |
| README não exige segurança da API | Não exige credencial, papel, TLS nem rate limit | Exige API keys, separa `READER`/`WRITER`, limita requisições e exige TLS em produção | Defesa em profundidade e defaults seguros reduzem abuso e acesso indevido |
| README não exige OpenAPI | Não inclui SpringDoc nem expõe Swagger/OpenAPI | Publica contrato completo com schemas, responses e security scheme | Documentação executável reduz divergência entre código e consumidores |

## Comportamentos preservados

As duas branches mantêm saldo inicial de `500.00`, unicidade do cartão, ordem das regras de autorização, persistência do cartão, atualização somente após autorização, senha com PBKDF2/salt/pepper, Flyway/MySQL e débito condicional atômico. Esses pontos não contradizem nenhuma instrução explícita do README e mantêm a versão literal funcional sob concorrência.

## Como comparar

```bash
git diff feature/api-following-good-practices..feature/api-following-readme-literal
git switch feature/api-following-good-practices
git switch feature/api-following-readme-literal
```
