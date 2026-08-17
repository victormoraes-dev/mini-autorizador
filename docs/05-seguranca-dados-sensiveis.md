# Segurança de dados sensíveis

## Classificação

| Dado | Classificação | Política |
| --- | --- | --- |
| PAN/card number | sensível | body sob TLS, mascarado em response/log, ausente de URL |
| password | segredo | body `writeOnly` sob TLS, hash em repouso, nunca retornar/logar |
| password hash/pepper | segredo | somente infraestrutura, nunca DTO |
| balance/amount | financeiro | TLS, autorização do consumidor e exposição mínima |
| cardId UUID | identificador público opaco | permitido em URL; não concede autorização sozinho |

## Trânsito

- Produção aceita somente HTTPS/TLS 1.2+.
- TLS pode terminar em proxy confiável com `X-Forwarded-Proto: https`.
- HTTP é permitido somente em localhost no perfil de avaliação.
- Password e PAN nunca usam path, query string ou header de rastreamento.
- PAN e password são aceitos em JSON apenas porque criação/autorização precisam deles; body logging deve permanecer desabilitado.

## URLs

O contrato legado `GET /cartoes/{numeroCartao}` não é implementado. Toda referência após a criação usa `CardId` UUID:

```text
GET /api/v1/cards/7c97bca5-3c85-4a2d-aab8-2d06112b56e4
```

O `Location` de criação segue a mesma regra.

## Responses

Response de cartão:

```json
{
  "id": "7c97bca5-3c85-4a2d-aab8-2d06112b56e4",
  "cardNumber": "************4501",
  "balance": 500.00
}
```

Nunca serializar entidade JPA ou aggregate diretamente. Password, hash, ID interno e timestamps não fazem parte do DTO.

Errors seguem Problem Details, mas não ecoam valores rejeitados. `violations` informa apenas nome do campo e mensagem genérica.

## Armazenamento de senha

Formato:

```text
pbkdf2-sha256$iterations$saltBase64$hashBase64
```

Parâmetros:

- PBKDF2-HMAC-SHA256;
- salt aleatório de 16 bytes por cartão;
- 210.000 iterações;
- chave de 256 bits;
- pepper externo em produção;
- comparação com `MessageDigest.isEqual`.

O perfil produtivo falha ao iniciar sem `APP_SECURITY_PASSWORD_PEPPER`.

## Armazenamento do PAN

O desafio requer busca de duplicidade e fornece banco simples. A implementação mantém PAN em coluna restrita para suportar esse comportamento. Para produção real:

1. tokenizar PAN em serviço/vault PCI;
2. persistir token reversível somente no vault;
3. usar HMAC determinístico separado para busca de duplicidade;
4. criptografar backups e rotacionar chaves;
5. segregar permissões da aplicação e operação.

Texto claro no banco é risco residual explicitamente aceito apenas neste assessment.

## Logs e observabilidade

- `CardNumber.toString()` retorna máscara.
- `Card` e `Transaction` redactam credenciais.
- Não habilitar logs de body ou parâmetros JDBC sensíveis.
- Access logs veem apenas UUID nos endpoints de consulta/transação.
- Traces não recebem password/PAN como atributo.
- Problem Details não incluem stack trace em response.

## Autorização e enumeração

O UUID reduz enumeração baseada em PAN, mas não substitui autenticação. A aplicação exige API key, diferencia leitura de escrita e limita requisições por cliente. Em produção distribuída, OAuth2/OIDC, rate limiting no gateway e auditoria persistente devem substituir ou complementar esses controles locais.

A API preserva motivos de recusa distintos porque isso pertence ao comportamento funcional solicitado. Consumidores e controles antifraude devem limitar abuso dessa informação.

## Perfis

O formato seguro é igual em todos os perfis. Não existe mais response inseguro de “avaliação”. O perfil altera somente controles operacionais de TLS e origem do pepper.

## Checklist

- [x] password fora de responses;
- [x] PAN fora de URLs;
- [x] PAN mascarado em responses/logs;
- [x] hash com salt e pepper;
- [x] TLS produtivo;
- [x] DTOs dedicados;
- [x] erros sem eco de dados;
- [ ] tokenização/HSM real — fora do escopo;
- [x] autenticação por API key, autorização por papel e rate limiting local;
- [ ] OAuth2/OIDC e rate limiting distribuído — responsabilidade da implantação.
