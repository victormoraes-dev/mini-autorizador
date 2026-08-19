# Implementação da branch `main`

Este documento descreve a implementação de referência da branch `main`. Essa
branch preserva as regras do Mini Autorizador e aplica decisões de design para
oferecer uma API RESTful segura, consistente e preparada para evolução.

## Objetivos e premissas

A implementação atende à criação de Cartão com Saldo inicial de R$ 500,00, à
consulta de Saldo e à Autorização de Transação. As decisões foram guiadas pelas
seguintes premissas:

- manter as regras de Cartão, Saldo e Autorização independentes de HTTP,
  Spring, JPA e MySQL;
- impedir Saldo negativo mesmo quando múltiplas instâncias autorizam
  Transações concorrentes;
- não persistir Transações;
- nunca expor Número do Cartão completo, Senha do Cartão ou Hash da Senha em
  responses, erros ou logs;
- identificar o Cartão externamente por um UUID opaco;
- representar erros HTTP de modo uniforme com Problem Details (RFC 9457);
- separar o ambiente de avaliação das exigências do ambiente de produção.

O contrato seguro da `main` é intencionalmente diferente do contrato literal
do enunciado. A comparação entre as duas implementações está em
[`README_BRANCHES.md`](README_BRANCHES.md).

## Processo de implementação

A branch `main` foi construída em etapas pequenas, cada uma introduzindo uma
capacidade verificável:

1. preparação da aplicação Maven com Java 21 e Spring Boot;
2. modelagem de Cartão, Saldo, Senha do Cartão, Transação e Autorização;
3. definição dos Use Cases e da persistência de Cartão no MySQL;
4. exposição da API REST versionada e padronização dos erros;
5. proteção das operações com API keys, papéis, rate limiting e TLS;
6. cobertura das regras, do contrato HTTP e dos cenários concorrentes;
7. criação de uma branch de referência para comparar o contrato seguro com o
   contrato literal do desafio;
8. empacotamento da aplicação em imagem Docker e integração ao Docker Compose.

## Arquitetura

A solução combina Domain-Driven Design (DDD) e Hexagonal Architecture, também
conhecida como Ports and Adapters. As dependências apontam para o domínio: API
e infraestrutura dependem dos contratos internos, enquanto as regras do Cartão
não conhecem servidor web, framework ou banco de dados.

| Área | Pacotes | Responsabilidade no Mini Autorizador |
| --- | --- | --- |
| Domínio | `domain.model`, `domain.service` | Proteger as invariantes do Cartão e decidir a Autorização |
| Aplicação | `application.port`, `application.service` | Executar os Use Cases e delimitar transações de banco |
| Adapter de entrada | `api`, `api.dto` | Converter requests HTTP em conceitos do domínio e produzir responses |
| Adapter de saída | `infrastructure.persistence` | Persistir e recuperar o Cartão no MySQL |
| Segurança | `infrastructure.security` | Proteger Senha do Cartão e controlar acesso às operações |


Essa organização do código permite testar a Autorização e os Use Cases sem iniciar HTTP ou
MySQL. Também permite substituir um Adapter sem reescrever as regras do
Cartão.

## Domain Model e Design Patterns

### `Card` como Aggregate Root

`Card` é o Aggregate Root. Ele concentra as invariantes que precisam continuar
verdadeiras durante todo o ciclo de vida do Cartão.

Uma regra que pertence ao Cartão permanece no Aggregate Root, em vez de ser
espalhada pelo controller ou pelo Adapter de persistência.

### Value Objects

`CardId`, `CardNumber`, `CardPassword`, `PasswordHash`, `Money` e `Balance` são
Value Objects. Cada um valida seu próprio significado no momento da criação.

Com essas validações, estados inválidos são rejeitados antes de alcançar os
Use Cases.

### Domain Service e Dependency Inversion Principle

O Cartão precisa criar e verificar o Hash da Senha, mas o modelo de hashing (PBKDF2) é uma decisão
técnica. `PasswordHasher` funciona como Domain Service e Port; a implementação
`Pbkdf2PasswordHasher` fica na infraestrutura. Assim, o Aggregate Root solicita
a capacidade sem depender do algoritmo criptográfico ou do Spring.

## Use Cases

### Criar Cartão

`CreateCardUseCase` descreve a operação e `CardApplicationService` a executa.

A consulta inicial melhora a resposta comum de duplicidade, mas não é a
garantia final. A restrição `UNIQUE` de `card_number` e o `INSERT IGNORE` no
MySQL impedem dois Cartões com o mesmo Número do Cartão quando as requisições
são concorrentes.

### Consultar Cartão e Saldo

`GetCardUseCase` recebe o Identificador do Cartão, recupera o Cartão pelo
`CardRepository` e devolve uma representação com Identificador do Cartão,
Número do Cartão mascarado e Saldo. Um Identificador desconhecido resulta em
`CARD_NOT_FOUND`.

### Autorizar Transação

`AuthorizeTransactionUseCase` é implementado por `TransactionAuthorizer`. 

O Cartão decide as regras do domínio. O resultado do débito condicional decide
se o Saldo ainda estava disponível no instante da persistência.

## Concorrência e consistência do Saldo

A consistência do Saldo não depende de um lock mantido na memória de uma única
JVM. O Adapter MySQL executa o débito como uma operação atômica:

```sql
UPDATE cards
   SET balance = balance - :amount
 WHERE public_id = :publicId
   AND balance >= :amount
```

O retorno é o número de linhas alteradas. Se duas instâncias tentarem consumir
simultaneamente o último Saldo disponível, somente uma encontrará
`balance >= amount`. Essa Transação será aprovada; a outra alterará zero linhas
e será negada por Saldo insuficiente.

A criação concorrente segue o mesmo princípio. A unicidade do Número do Cartão
é protegida pelo schema, e `INSERT IGNORE` converte a disputa em um resultado
determinístico: um Cartão criado e uma resposta de conflito.

A solução não utiliza Optimistic Locking com coluna de versão nem Event Store.
A garantia de concorrência vem das operações condicionais e das constraints do
MySQL, que são compartilhadas por todas as instâncias da aplicação.

## Persistência do Cartão

O MySQL foi escolhido entre os bancos oferecidos pelo desafio porque o Cartão
exige constraints de unicidade e débito condicional atômico. 

`CardRepository` é a Output Port. `MySqlCardRepository` é o Adapter que conecta
essa Port ao Spring Data JPA e ao MySQL. O domínio continua recebendo e
devolvendo apenas seus próprios tipos.

## Design Patterns

| Pattern | Aplicação no projeto | Decisão atendida |
| --- | --- | --- |
| Hexagonal Architecture (Ports and Adapters) | Input Ports para os Use Cases, `CardRepository` como Output Port e Adapters HTTP/MySQL | Manter o Domain Model independente de tecnologia |
| Repository | `CardRepository` e `MySqlCardRepository` | Persistir e recuperar o Aggregate Root usando o vocabulário do domínio |
| Aggregate Root | `Card` | Centralizar as invariantes de Senha do Cartão, Saldo e Autorização |
| Value Object | `CardId`, `CardNumber`, `CardPassword`, `PasswordHash`, `Money` e `Balance` | Validar significado e impedir estados inválidos |
| Factory Method | `Card.issue`, `Card.restore`, `Transaction.request` e métodos `from` | Tornar explícita a intenção de cada forma de criação |
| Strategy | `PasswordHasher` com `Pbkdf2PasswordHasher` | Permitir trocar o mecanismo de Hash da Senha sem alterar o Cartão |
| Data Mapper | `CardPersistenceMapper` | Separar `CardJpaEntity` do Aggregate Root |
| Data Transfer Object | Tipos de request e response em `api.dto` | Separar o contrato HTTP do Domain Model |
| Application Service | `CardApplicationService` e `TransactionAuthorizer` | Orquestrar os Use Cases e seus limites transacionais |
| Exception Translator | `HttpErrorHandler` | Traduzir falhas da aplicação em Problem Details consistentes |

## Contrato HTTP da `main`

O contrato seguro e versionado oferece:

- `POST /api/v1/cards` — criar Cartão;
- `GET /api/v1/cards/{cardId}` — consultar Cartão e Saldo;
- `POST /api/v1/transactions` — autorizar Transação e debitar Saldo.

Responses são JSON. Erros usam `application/problem+json`, status HTTP
semânticos e um campo `code` estável. A criação do Cartão retorna
`201 Created` com header `Location`. Uma Autorização aprovada retorna `200 OK`
porque nenhuma Transação é persistida como novo recurso.

O Número do Cartão é mascarado, a Senha do Cartão nunca é devolvida e o Cartão
é endereçado por seu UUID. O contrato pode ser consultado em:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`;
- OpenAPI: `http://localhost:8080/v3/api-docs`.

## Segurança

Os controles de segurança protegem o Cartão e suas operações:

- API keys diferentes para leitura e escrita;
- a chave de escrita também pode consultar Cartão e Saldo;
- `MessageDigest.isEqual` compara API keys sem curto-circuito;
- PBKDF2-HMAC-SHA256 deriva o Hash da Senha com salt aleatório, iterações
  configuráveis e pepper externo em produção;
- Senha do Cartão, Hash da Senha e Número do Cartão completo são removidos de
  logs, responses e mensagens de erro;
- rate limiting usa uma impressão SHA-256 da API key ou do endereço remoto;
- TLS é obrigatório por padrão no perfil `producao`;
- healthcheck e documentação da API não exigem autenticação.

O rate limiting é local a cada instância. Para impor uma cota compartilhada
entre réplicas, o controle deve migrar para um API Gateway, Redis ou outro
armazenamento distribuído. API keys e pepper de produção devem vir de um
Secret Manager.

## Execução

### Aplicação e MySQL com Docker Compose

Pré-requisitos: Docker e Docker Compose.

```bash
docker compose -f docker/docker-compose.yml up --build -d
```

O Compose constrói a aplicação, aguarda o MySQL ficar saudável e publica o
Mini Autorizador em `http://localhost:8080`.

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f application
docker compose -f docker/docker-compose.yml down
```

As API keys locais podem ser substituídas sem modificar o Compose:

```bash
APP_SECURITY_READER_API_KEY='outra-chave-de-leitura-com-32-chars' \
APP_SECURITY_WRITER_API_KEY='outra-chave-de-escrita-com-32-chars' \
docker compose -f docker/docker-compose.yml up --build -d
```

O `Dockerfile` usa Multi-stage Build. A compilação ocorre com Maven e JDK 21; o
artefato executável é empacotado em uma imagem com JRE 21, usuário não-root e
healthcheck. A imagem contém a aplicação, não dados de Cartão.

### Aplicação no host

Pré-requisitos: Java 21 e Docker. O Maven Wrapper elimina a necessidade de uma
instalação global do Maven.

```bash
docker compose -f docker/docker-compose.yml up -d mysql

APP_SECURITY_READER_API_KEY='reader-local-key-with-at-least-32-chars' \
APP_SECURITY_WRITER_API_KEY='writer-local-key-with-at-least-32-chars' \
SPRING_PROFILES_ACTIVE=avaliacao \
./mvnw spring-boot:run
```

### Perfil de produção

```bash
APP_SECURITY_PASSWORD_PEPPER='obtenha-de-um-secret-manager' \
APP_SECURITY_READER_API_KEY='obtenha-de-um-secret-manager' \
APP_SECURITY_WRITER_API_KEY='obtenha-de-um-secret-manager' \
SPRING_PROFILES_ACTIVE=producao \
./mvnw spring-boot:run
```

Sem contexto seguro, o perfil `producao` responde `426 Upgrade Required`.
Quando o TLS termina em um proxy, `X-Forwarded-Proto` deve ser aceito somente
de proxies confiáveis.

## Estratégia de testes

A suíte verifica o vocabulário e as regras do Mini Autorizador em diferentes
níveis:

- testes unitários dos Value Objects, do Aggregate Root e dos Application
  Services;
- testes dos controllers, do Exception Translator e dos filtros de segurança;
- testes do Data Mapper e do Adapter de persistência;
- testes de integração com a aplicação real e MySQL 5.7 via Testcontainers;
- criação concorrente do mesmo Número do Cartão;
- duas Transações concorrentes disputando o mesmo Saldo;
- contrato OpenAPI e ausência de dados sensíveis;
- contrato seguro executado também no perfil `producao`.

```bash
./mvnw test
./mvnw clean verify
```

`test` executa os testes unitários. `verify` inclui os testes de integração e
exige um daemon Docker disponível para Testcontainers.

## Limites e possíveis evoluções

- O rate limiting em memória não oferece uma cota global entre réplicas.
- API keys atendem ao escopo do desafio; um ambiente real pode adotar OAuth
  2.0, rotação automática e auditoria centralizada.
- A Transação não é persistida. Auditoria, estorno e idempotência exigiriam um
  modelo próprio para o ciclo de vida da Transação.
- MySQL 5.7 foi mantido por ser a versão fornecida no desafio. Uma evolução
  deve adotar uma versão suportada e revalidar migrations e dialeto.
- métricas, Distributed Tracing e logs estruturados podem complementar os
  healthchecks em uma operação real.
