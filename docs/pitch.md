# Pitch técnico — Mini Authorizer

## Como usar este documento

Este documento é um roteiro para explicar a solução em uma entrevista técnica. Ele combina a história da implementação, o propósito do sistema, a estrutura do repositório, as decisões arquiteturais, os fluxos principais, os trade-offs e as perguntas que provavelmente surgirão.

Uma forma prática de estudá-lo é:

1. memorizar o pitch de 30 segundos;
2. entender o desenho das camadas e os três fluxos de negócio;
3. saber defender as decisões de segurança e concorrência;
4. praticar a demonstração sugerida;
5. revisar os trade-offs e as perguntas de entrevista.

---

## Pitch de 30 segundos

O Mini Authorizer é uma API de autorização de transações de cartão. Ela emite cartões com saldo inicial de `500.00`, permite consultar seu estado e autoriza um débito somente quando o cartão existe, a senha está correta e há saldo suficiente. A solução foi construída em Java 21 e Spring Boot 4.1.0, com domínio rico em DDD, arquitetura de portas e adaptadores, MySQL, Flyway e documentação OpenAPI. Os pontos centrais são segurança de dados sensíveis, contrato REST consistente e débito atômico, que impede saldo negativo mesmo com requisições concorrentes em múltiplas instâncias.

## A ideia central em uma frase

As regras pertencem ao domínio; HTTP, banco, hashing e frameworks são detalhes conectados ao domínio por portas.

---

## 1. Qual problema a aplicação resolve

O sistema representa uma parte pequena, porém crítica, de um autorizador de benefícios. Ele responde a três perguntas:

1. É possível emitir um novo cartão?
2. Qual é o estado e o saldo atual desse cartão?
3. Uma tentativa de compra pode ser autorizada e debitada?

Uma transação é autorizada somente se, nesta ordem:

1. o cartão existir;
2. a senha estiver correta;
3. o saldo cobrir o valor solicitado.

Se todas as condições forem atendidas, o saldo é debitado exatamente uma vez. Caso contrário, a API retorna o motivo da recusa e não altera o saldo.

### O que este sistema não é

Ele não é um arranjo completo de pagamentos. Não há captura, liquidação, estorno, ledger contábil, antifraude, gestão de estabelecimentos ou emissão real de PAN. A transação também não é persistida, conforme permitido pelo desafio.

Essa delimitação é intencional: o foco está na decisão de autorização e na consistência do saldo.

---

## 2. A decisão que orientou todo o design

O README foi tratado como fonte das capacidades e regras de negócio, mas não como autoridade absoluta sobre o contrato HTTP.

Foram preservados do README:

- emissão de cartão com saldo inicial de `500.00`;
- persistência do cartão;
- consulta do saldo;
- ordem das regras de autorização;
- débito após aprovação;
- não obrigatoriedade de persistir transações;
- preocupação com concorrência.

Foram deliberadamente redesenhados:

- URLs em português e sem versão;
- PAN na URL;
- senha devolvida no response;
- respostas primitivas como um decimal ou `OK` em texto;
- `201 Created` sem criação de um recurso de transação;
- `422` para todos os tipos de erro;
- códigos de erro textuais sem estrutura comum.

O resultado é um contrato REST canônico em `/api/v1`, com JSON seguro e erros RFC 9457.

### Trade-off explícito

Essa decisão melhora segurança, semântica HTTP e manutenibilidade, mas pode quebrar um avaliador automatizado que espere literalmente os endpoints legados `/cartoes` e `/transacoes`.

Não foi criada uma camada legada porque ela reintroduziria PAN em URL, senha em response e dois contratos concorrentes. Essa é uma decisão consciente de produto e segurança, não uma limitação técnica. Se compatibilidade literal fosse obrigatória, a alternativa seria um adapter legado isolado, marcado como deprecated e desabilitado em produção — ainda assim, a exposição de segredos precisaria ser recusada ou renegociada.

---

## 3. Linha do tempo da construção

A história do Git foi usada para registrar incrementos lógicos e permitir que um avaliador acompanhe o raciocínio, não apenas o resultado final.

| Fase | O que foi construído | Decisão relevante |
| --- | --- | --- |
| 1. Plataforma | Maven, Java 21, perfis e Docker | Build reproduzível com configuração externa |
| 2. Domínio e dados | Aggregate `Card`, portas, JPA, Flyway e débito condicional | Regras no domínio e concorrência resolvida no banco |
| 3. Contrato HTTP | API `/api/v1`, DTOs, Problem Details e OpenAPI | Transporte seguro e consistente separado do domínio |
| 4. Segurança | PBKDF2, API keys, papéis, TLS e rate limit | Defesa em profundidade com defaults restritivos |
| 5. Verificação | Unitários e integração com MySQL 5.7 | Contrato, dados sensíveis e concorrência cobertos |
| 6. Documentação | Guia de execução e decisões | Contrato executável e documentação humana alinhados |

### Como contar essa história oralmente

“Eu comecei isolando o problema de negócio e construindo um domínio independente. Depois conectei a persistência e resolvi concorrência no ponto em que ela realmente ocorre: o banco. Em seguida protegi credenciais, expus os casos de uso por uma API REST e fechei com documentação executável e testes de integração. Uma revisão posterior identificou que o contrato literal do README vazava dados sensíveis, então mantive as regras, mas evoluí a interface para UUID, respostas JSON seguras e Problem Details.”

---

## 4. Visão arquitetural

```text
Cliente HTTP / Swagger
          │
          ▼
┌──────────────────────── API ────────────────────────┐
│ Controllers, request/response DTOs, validação       │
│ OpenAPI e tradução de erros para Problem Details    │
└─────────────────────────┬───────────────────────────┘
                          │ input ports
                          ▼
┌──────────────────── Application ────────────────────┐
│ Casos de uso, orquestração e limites transacionais  │
│ CreateCard, GetCard e AuthorizeTransaction          │
└─────────────────────────┬───────────────────────────┘
                          │ domain model / output port
                          ▼
┌─────────────────────── Domain ──────────────────────┐
│ Card, Transaction, value objects e regras           │
│ Nenhuma dependência de Spring, HTTP, Jackson ou JPA │
└─────────────────────────▲───────────────────────────┘
                          │ adapters implementam portas
                          │
┌────────────────── Infrastructure ───────────────────┐
│ JPA/MySQL, Flyway, PBKDF2, masking e TLS            │
└─────────────────────────────────────────────────────┘
```

### Regra de dependência

As dependências apontam para dentro:

- a API conhece os casos de uso, mas o domínio não conhece a API;
- a aplicação conhece a abstração `CardRepository`, mas não conhece JPA;
- `MySqlCardRepository` implementa a porta definida pela aplicação;
- `Pbkdf2PasswordHasher` implementa uma abstração que o domínio consegue utilizar;
- DTOs e entidades JPA nunca são usados como entidades de domínio.

Isso permite trocar uma borda sem reescrever as regras. Por exemplo, PBKDF2 poderia ser substituído por outro hasher, ou MySQL por outro adapter, desde que o contrato da porta fosse preservado.

---

## 5. Estrutura do repositório

```text
mini-autorizador/
├── README.md                         problema original + como executar
├── pom.xml                           dependências, Java 21 e ciclo Maven
├── mvnw / .mvn/                      Maven Wrapper reproduzível
├── docker/
│   └── docker-compose.yml            MySQL 5.7 fornecido pelo cenário
├── docs/                             decisões duráveis e handoff
│   ├── 00-handoff.md                 estado canônico da solução
│   ├── 01-requisitos.md              requisitos e critérios de aceite
│   ├── 02-especificacao-tecnica.md   contrato e arquitetura
│   ├── 03-tarefas.md                 backlog executado
│   ├── 04-plano-testes.md            cenários unitários e integração
│   ├── 05-seguranca-dados-sensiveis.md
│   ├── 06-ddd-padroes-design.md
│   ├── 07-estrategia-commits.md
│   ├── 08-decisoes-implementacao.md
│   └── pitch.md                      este roteiro de apresentação
└── src/
    ├── main/
    │   ├── java/br/com/vr/miniautorizador/
    │   │   ├── api/                  HTTP, DTOs, OpenAPI e errors
    │   │   ├── application/          casos de uso e ports
    │   │   ├── domain/               modelo e regras de negócio
    │   │   └── infrastructure/       MySQL, segurança e adapters
    │   └── resources/
    │       ├── application.yml
    │       ├── application-avaliacao.yml
    │       ├── application-producao.yml
    │       └── db/migration/         V1 e V2 do Flyway
    └── test/java/...                 unitários, API e Testcontainers
```

### Como navegar durante a entrevista

Uma sequência simples para apresentar o código é:

1. `Card`: mostra onde vivem as regras;
2. `TransactionAuthorizer`: mostra a orquestração do caso de uso;
3. `CardRepository`: mostra a inversão de dependência;
4. `MySqlCardRepository` e `CardJpaRepository`: mostram o adapter e o débito atômico;
5. `CardController` e `TransactionController`: mostram que HTTP é uma borda fina;
6. `HttpErrorHandler`: mostra o contrato uniforme de erros;
7. testes de domínio e integração: provam comportamento e concorrência.

---

## 6. Modelo de domínio

### `Card` como aggregate root

`Card` é a raiz do agregado porque saldo, credencial e decisão de autorização precisam permanecer consistentes como uma unidade de negócio.

Responsabilidades principais:

- `issue(...)`: emite um cartão com UUID, senha protegida e saldo inicial;
- `restore(...)`: reconstitui um cartão vindo da persistência;
- `authorize(...)`: verifica que a tentativa pertence ao cartão, depois senha e saldo;
- `debit(...)`: reduz o saldo mantendo a invariante de não negatividade.

A entidade não recebe `HttpServletRequest`, não retorna status HTTP e não possui anotações JPA. Ela conhece conceitos do negócio.

### `Transaction`

`Transaction` representa uma solicitação de autorização, não um recurso persistido. Ela contém:

- `CardId`;
- senha fornecida de forma transitória;
- `Money` solicitado.

Essa modelagem explica por que uma autorização aprovada retorna `200 OK`: nenhuma nova representação persistida de transação é criada.

### Value objects

| Tipo | Invariante encapsulada | Benefício |
| --- | --- | --- |
| `CardId` | UUID obrigatório e válido | PAN não é usado como identidade pública |
| `CardNumber` | obrigatório, até 32 caracteres e mascaramento | dado sensível não circula como `String` sem comportamento |
| `CardPassword` | obrigatória, até 72 caracteres e `toString` redigido | reduz vazamento acidental |
| `PasswordHash` | valor não vazio e `toString` redigido | diferencia segredo transitório de credencial persistida |
| `Money` | positivo, escala máxima de duas casas | evita valores inválidos e erro de ponto flutuante |
| `Balance` | nunca negativo, escala monetária e débito seguro | protege a principal invariante financeira |

`BigDecimal` e `DECIMAL(19,2)` foram escolhidos porque `double` não representa valores decimais financeiros de forma exata.

### Por que não validar Luhn ou aceitar apenas dígitos?

O desafio não define emissão real de PAN nem essa regra. A implementação valida apenas os limites necessários e não inventa uma restrição de negócio que poderia rejeitar identificadores válidos no contexto fornecido. Em um produto real, essa política seria adicionada ao `CardNumber` quando confirmada pelo domínio.

---

## 7. Fluxos principais

### 7.1 Emissão do cartão

```text
POST /api/v1/cards
       │
       ▼
CreateCardRequest + Bean Validation
       │
       ▼
CardApplicationService
       ├── verifica duplicidade por CardNumber
       ├── Card.issue(...)
       │      ├── gera CardId UUID
       │      ├── cria hash PBKDF2
       │      └── aplica Balance.INITIAL = 500.00
       └── CardRepository.create(...)
              └── INSERT IGNORE + restrições únicas
       │
       ▼
201 Created + Location + CardResponse seguro
```

Existem duas proteções contra duplicidade:

1. a consulta prévia fornece um erro rápido e legível;
2. a restrição única no banco resolve a corrida entre duas instâncias.

O `INSERT IGNORE` retorna zero quando a criação concorrente perdeu a disputa, e a aplicação traduz isso para `409 CARD_ALREADY_EXISTS`.

### 7.2 Consulta do cartão

```text
GET /api/v1/cards/{cardId}
       │
       ├── valida UUID
       ├── busca por public_id
       ├── restaura Card
       └── converte para CardDetails/CardResponse
              ├── id público
              ├── número mascarado
              └── saldo
```

O response não é um decimal primitivo. Ele representa o recurso consultado e deixa espaço para evolução controlada sem expor senha, hash, ID interno ou timestamps.

### 7.3 Autorização e débito

```text
POST /api/v1/transactions
       │
       ▼
Transaction.request(cardId, password, amount)
       │
       ▼
TransactionAuthorizer
       ├── cartão existe? ───────── não ──► CARD_NOT_FOUND
       ├── Card.authorize(...)
       │      ├── senha válida? ─── não ──► INVALID_PASSWORD
       │      └── saldo suficiente? não ──► INSUFFICIENT_BALANCE
       ├── Card.debit(...) mantém invariante local
       └── UPDATE atômico no banco
              ├── 1 linha ────────────────► AUTHORIZED
              └── 0 linhas ───────────────► INSUFFICIENT_BALANCE
```

A entidade decide com base nas regras. A aplicação controla a ordem, a transação técnica e a tradução do resultado. O banco arbitra a concorrência entre instâncias.

---

## 8. A decisão de concorrência

O requisito mais importante além do fluxo feliz é evitar dupla autorização sobre o mesmo saldo.

Uma abordagem ingênua seria:

1. ler saldo `10.00`;
2. duas instâncias concluírem que podem debitar `10.00`;
3. ambas aprovarem;
4. ocorrer dupla utilização ou atualização perdida.

A solução usa uma única operação atômica:

```sql
UPDATE cards
   SET balance = balance - :amount
 WHERE public_id = :cardId
   AND balance >= :amount;
```

Interpretação:

- uma linha alterada: este request reservou e debitou o saldo;
- zero linhas alteradas: outro request consumiu o saldo ou ele já era insuficiente.

Com saldo `10.00` e duas transações simultâneas de `10.00`, somente uma operação consegue satisfazer `balance >= amount`. A outra retorna saldo insuficiente, e o saldo final é `0.00`.

### Por que essa solução foi escolhida

- funciona com múltiplas threads e múltiplas instâncias;
- não depende de lock em memória;
- evita lock pessimista durante o cálculo do hash da senha;
- reduz round-trips e contenção;
- faz o banco, que é o ponto de consistência compartilhado, arbitrar a disputa.

### Limite semântico

O retorno de zero linhas após uma autorização em memória é mapeado como saldo insuficiente porque, neste domínio, a causa esperada é uma disputa concorrente pelo saldo. Se surgirem outros estados mutáveis ou deleção concorrente, o contrato do repositório deve evoluir para distinguir as causas.

---

## 9. Contrato da API

### Endpoints

| Operação | Endpoint | Sucesso |
| --- | --- | --- |
| Emitir cartão | `POST /api/v1/cards` | `201 Created` |
| Consultar cartão | `GET /api/v1/cards/{cardId}` | `200 OK` |
| Autorizar transação | `POST /api/v1/transactions` | `200 OK` |

### Representação segura de cartão

```json
{
  "id": "7c97bca5-3c85-4a2d-aab8-2d06112b56e4",
  "cardNumber": "************4501",
  "balance": 500.00
}
```

### Autorização aprovada

```json
{
  "status": "AUTHORIZED"
}
```

### Erros

Os erros seguem RFC 9457 com media type `application/problem+json`:

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

| Situação | HTTP | Código estável |
| --- | --- | --- |
| JSON ou valor inválido | `400` | `MALFORMED_REQUEST` ou `VALIDATION_ERROR` |
| Cartão inexistente | `404` | `CARD_NOT_FOUND` |
| Cartão duplicado | `409` | `CARD_ALREADY_EXISTS` |
| Senha inválida | `422` | `INVALID_PASSWORD` |
| Saldo insuficiente | `422` | `INSUFFICIENT_BALANCE` |

O `HttpErrorHandler` centraliza a tradução. Assim, o domínio não conhece HTTP e os controllers não repetem estruturas de erro.

### Por que `422` nas recusas?

O JSON é sintaticamente válido, mas o pedido não pode ser processado devido ao estado e às regras do domínio. Cartão inexistente é separado como `404`, e duplicidade como `409`, porque cada condição possui uma semântica HTTP mais específica.

---

## 10. Segurança por desenho

### PAN/card number

- entra somente no body de criação;
- não aparece em path ou query string;
- é substituído por `CardId` UUID nas operações seguintes;
- aparece mascarado nos responses e em `toString`;
- não deve ser capturado por body logging, tracing ou parâmetros JDBC.

O UUID reduz enumeração e exposição operacional, mas não é uma credencial nem substitui autenticação.

### Senha

- entra somente no body sob TLS;
- é marcada como `writeOnly` no OpenAPI;
- nunca volta no response;
- nunca é armazenada em texto claro;
- usa PBKDF2-HMAC-SHA256 com 210.000 iterações;
- recebe salt aleatório de 16 bytes por cartão;
- incorpora pepper externo no perfil produtivo;
- usa comparação com `MessageDigest.isEqual`;
- seus objetos imprimem `[REDACTED]`.

Formato persistido:

```text
pbkdf2-sha256$iterations$saltBase64$hashBase64
```

Salt pode ficar ao lado do hash e impede hashes iguais para senhas iguais. Pepper é um segredo externo ao banco e reduz o impacto de vazamento isolado da base.

### Trânsito e perfis

- `avaliacao`: HTTP permitido apenas em `127.0.0.1`, com pepper local substituível;
- `producao`: TLS obrigatório e `APP_SECURITY_PASSWORD_PEPPER` obrigatório;
- ambos retornam exatamente o mesmo contrato seguro.

O perfil altera controles operacionais, não a representação dos dados. Um ambiente de avaliação não justifica resposta insegura.

### Risco residual assumido

O PAN permanece em texto claro na coluna `card_number` para suportar duplicidade com a infraestrutura simples do assessment. Em produção real, a evolução recomendada é:

1. tokenizar o PAN em vault compatível com PCI;
2. manter um HMAC determinístico separado para pesquisa de duplicidade;
3. criptografar backups e controlar acesso por menor privilégio;
4. rotacionar chaves e auditar acessos.

Também ficaram fora do escopo OAuth2/OIDC, rate limiting distribuído, idempotency keys e trilha persistente de auditoria. A aplicação usa API keys com papéis e rate limiting local como controles proporcionais ao assessment.

---

## 11. Persistência e evolução do schema

Foi escolhido MySQL 5.7 porque essa é a imagem fornecida no cenário. MongoDB não agregaria valor: saldo e atualização condicional se beneficiam diretamente de unicidade, transações e update atômico relacional.

### Tabela `cards`

| Coluna | Papel |
| --- | --- |
| `id BIGINT` | chave interna do banco, nunca exposta |
| `public_id CHAR(36)` | UUID público único |
| `card_number VARCHAR(32)` | número sensível e chave de duplicidade |
| `password_hash VARCHAR(255)` | credencial PBKDF2 codificada |
| `balance DECIMAL(19,2)` | saldo financeiro |
| timestamps | rastreio técnico de criação e atualização |

### Flyway

- `V1__create_cards_table.sql`: cria a estrutura original;
- `V2__add_public_card_id.sql`: adiciona UUID, preenche registros existentes e cria unicidade.

O Hibernate usa `ddl-auto=validate`: ele confirma que o modelo corresponde ao schema, mas não altera a base de forma implícita. A evolução é explícita, versionada e reproduzível pelo Flyway.

### Risco de plataforma

MySQL 5.7 está fora da faixa atual suportada pelo Hibernate usado no projeto. Ele foi mantido para compatibilidade com o desafio e validado por testes, mas uma implantação real deve migrar para MySQL 8 ou versão suportada equivalente.

---

## 12. Padrões de design aplicados

Os padrões foram usados para resolver problemas concretos, não para aumentar a quantidade de classes.

| Padrão | Onde | Problema resolvido |
| --- | --- | --- |
| Aggregate Root | `Card` | centraliza invariantes de autorização e saldo |
| Value Object | `CardId`, `Money`, `Balance` etc. | impede estados inválidos e primitive obsession |
| Factory Method | `Card.issue`, `Card.restore`, `Transaction.request` | torna explícita a intenção de criação |
| Strategy | `PasswordHasher` | desacopla a regra do algoritmo PBKDF2 e facilita testes |
| Repository | `CardRepository` | expressa persistência na linguagem da aplicação |
| Ports and Adapters | ports + controllers/adapters | isola domínio de frameworks e I/O |
| Data Mapper | `CardPersistenceMapper` | evita transformar JPA entity em domínio |
| DTO / Projection | requests, responses e `CardDetails` | minimiza dados e estabiliza o contrato externo |
| Controller Advice | `HttpErrorHandler` | padroniza erros em um ponto único |
| Unit of Work | `@Transactional` na aplicação/adapters | delimita atomicidade técnica |
| Conditional atomic update | repository JPA | resolve concorrência entre instâncias |
| Database Migration | Flyway V1/V2 | versiona a evolução do schema |

### Padrões deliberadamente evitados

- Active Record: misturaria regra e persistência;
- Generic Repository: esconderia operações importantes como débito condicional;
- modelo anêmico: espalharia autorização pelos services;
- Strategy de response por perfil: produziria contratos diferentes por ambiente;
- exposição direta de JPA/domain: acoplaria API e poderia vazar segredos;
- events/outbox sem necessidade atual: seria complexidade sem consumidor real.

---

## 13. Estratégia de testes

A suíte segue uma pirâmide:

```text
              Integração
          HTTP + Spring + MySQL
        -------------------------
          API e infraestrutura
        -------------------------
       Aplicação com ports/fakes
     -----------------------------
        Domínio puro e rápido
```

### Unitários

Os testes verificam:

- invariantes de `Money`, `Balance`, IDs e credenciais;
- ordem de senha antes de saldo;
- emissão e débito do cartão;
- casos de uso de criação, consulta e autorização;
- corrida representada pelo retorno do repositório;
- mapping entre domínio e persistência;
- masking e PBKDF2;
- filtro TLS;
- controllers e Problem Details.

### Integração

Testcontainers sobe `mysql:5.7`, a mesma imagem do cenário, e valida:

- migrações em schema real;
- criação, consulta, autorização e atualização do saldo;
- dados sensíveis no response e no banco;
- OpenAPI e ausência dos endpoints legados;
- duas criações concorrentes do mesmo cartão;
- dois débitos concorrentes disputando o mesmo saldo;
- igualdade do contrato seguro nos perfis de avaliação e produção.

Resultado da validação final documentada:

- 51 testes unitários;
- 6 testes de integração;
- `./mvnw clean verify` aprovado.

### Por que Testcontainers

Mocks não reproduzem SQL, constraints, isolamento, dialeto, migrações nem concorrência real. Testcontainers mantém os testes reproduzíveis e testa justamente os riscos que só aparecem na integração com MySQL.

---

## 14. Build, execução e documentação executável

### Tecnologia

- Java 21;
- Spring Boot 4.1.0;
- Maven Wrapper 3.9.11;
- Spring Data JPA;
- Flyway;
- MySQL 5.7;
- SpringDoc OpenAPI;
- JUnit 5, AssertJ e Testcontainers 2.

### Por que Maven Wrapper

O wrapper fixa a ferramenta de build esperada e permite executar o projeto com `./mvnw` sem depender da versão de Maven instalada na máquina do avaliador.

### Executar localmente

```bash
docker compose -f docker/docker-compose.yml up -d mysql
APP_SECURITY_READER_API_KEY='reader-local-key-with-at-least-32-chars' \
APP_SECURITY_WRITER_API_KEY='writer-local-key-with-at-least-32-chars' \
SPRING_PROFILES_ACTIVE=avaliacao ./mvnw spring-boot:run
```

### Rodar os testes

```bash
./mvnw test
./mvnw clean verify
```

### Documentação da API

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

OpenAPI é documentação executável: anotações nos controllers e DTOs descrevem endpoints, schemas, status, erros e campos `writeOnly`.

---

## 15. Roteiro de demonstração

### Demonstração de 7 minutos

#### Minuto 0–1: contexto

Explique o problema e diga que a prioridade foi preservar o core de autorização, com segurança e consistência concorrente.

#### Minuto 1–2: arquitetura

Abra `Card` e mostre que as regras estão no domínio. Depois mostre `CardRepository` para evidenciar a separação da infraestrutura.

#### Minuto 2–4: API

Crie um cartão:

```bash
curl -i -X POST http://localhost:8080/api/v1/cards \
  -H 'Content-Type: application/json' \
  -d '{"cardNumber":"6549873025634501","password":"1234"}'
```

Destaque:

- `201 Created`;
- `Location` com UUID;
- PAN mascarado;
- ausência da senha.

Copie o `id` e consulte:

```bash
curl -i http://localhost:8080/api/v1/cards/{cardId}
```

Autorize:

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"cardId":"{cardId}","password":"1234","amount":10.00}'
```

Consulte novamente e mostre o saldo `490.00`.

#### Minuto 4–5: erros

Envie senha inválida ou valor superior ao saldo. Mostre `422`, `application/problem+json` e o `code` estável.

#### Minuto 5–6: concorrência

Abra a query de update condicional e explique por que uma única operação no banco funciona também entre instâncias diferentes.

#### Minuto 6–7: provas

Mostre:

- testes de domínio;
- teste de concorrência com Testcontainers;
- Swagger UI;
- histórico de commits semântico.

Feche com os riscos conhecidos e as evoluções de produção.

---

## 16. Perguntas prováveis e respostas diretas

### “Por que não seguiu os endpoints do README?”

“Preservei integralmente as capacidades e regras de autorização, mas o contrato de exemplo expunha PAN em URL e senha em response, além de usar bodies primitivos e status pouco semânticos. Como a diretriz foi privilegiar boas práticas quando houvesse conflito, criei uma API versionada e segura. Reconheço que isso troca compatibilidade literal por segurança e consistência; se o teste automatizado legado fosse uma restrição absoluta, eu negociaria um adapter temporário isolado.”

### “Por que DDD para uma aplicação pequena?”

“O volume de código é pequeno, mas a regra é financeira e tende a crescer. Value objects protegem invariantes e `Card` concentra autorização e saldo. Usei apenas padrões com benefício concreto; não introduzi eventos, sagas ou specifications sem necessidade.”

### “Por que a regra está na entidade e ainda existe lógica no service?”

“A entidade decide senha e saldo porque são regras do agregado. O service orquestra I/O: localiza o cartão, abre a unidade transacional e solicita o débito atômico ao repositório. Regra de negócio e coordenação de infraestrutura são responsabilidades diferentes.”

### “Por que não apenas salvar a entidade JPA depois de debitar?”

“Um read-modify-write comum permitiria atualização perdida sob concorrência. O update condicional combina verificação e débito na mesma instrução SQL, no ponto compartilhado por todas as instâncias.”

### “Por que não usar lock pessimista?”

“Ele funcionaria, mas manteria locks por mais tempo e aumentaria contenção. Como a regra concorrente pode ser expressa em uma condição SQL simples, o update atômico é menor e mais escalável.”

### “Por que `200` em vez de `201` na transação?”

“A aplicação autoriza e debita, mas não persiste nem cria um recurso de transação consultável. Portanto, não existe um novo recurso nem `Location` a retornar.”

### “Por que UUID e não o número do cartão?”

“URLs aparecem em access logs, histórico, traces e ferramentas intermediárias. PAN é sensível e não deve ser um identificador operacional. UUID é opaco, embora ainda exija autenticação em produção.”

### “Hash de senha é criptografia?”

“Não. A senha usa derivação unidirecional PBKDF2 com salt e pepper. Ela é verificada, não recuperada. Para PAN, que eventualmente precisa de tokenização ou recuperação controlada, a estratégia produtiva seria vault/tokenização, não hash de senha.”

### “Por que o PAN ainda está em texto claro no banco?”

“É um risco residual assumido para o assessment, cuja infraestrutura não oferece vault ou KMS. Eu o documentei em vez de alegar segurança completa. Em produção, usaria tokenização e HMAC determinístico para duplicidade.”

### “Por que `CardRepository` está na aplicação?”

“A aplicação define o que precisa para executar os casos de uso. A infraestrutura fornece uma implementação. Isso inverte a dependência: a regra não depende de JPA.”

### “Por que existem DTO, `CardDetails`, domínio e JPA entity?”

“Cada modelo serve a uma fronteira. DTO define transporte; `CardDetails` é uma projeção segura da aplicação; `Card` protege invariantes; JPA entity representa armazenamento. Separá-los evita vazamento de senha e acoplamento de mudanças entre HTTP, domínio e banco.”

### “O UUID impede acesso indevido?”

“Não. Ele reduz exposição e enumeração de PAN, mas autorização do consumidor deve ser feita por OAuth2/gateway em uma implantação real.”

### “Por que MySQL e não MongoDB?”

“A principal operação é uma atualização monetária condicional com constraints de unicidade. O modelo relacional resolve isso diretamente e a imagem MySQL já era fornecida.”

### “Como você sabe que funciona em concorrência?”

“Além da propriedade da query, há teste de integração com MySQL real: duas operações simultâneas disputam o mesmo saldo, apenas uma é aprovada e o saldo termina em zero.”

---

## 17. O que evoluiria em produção

Prioridade sugerida:

1. migrar MySQL 5.7 para uma versão suportada;
2. substituir API keys por autenticação e autorização OAuth2 por escopo;
3. tokenizar PAN em vault/HSM e remover texto claro da base;
4. introduzir idempotency key para criação e autorização;
5. persistir ledger/auditoria imutável se exigido pelo negócio;
6. adicionar outbox para eventos de autorização;
7. mover o rate limiting para infraestrutura distribuída e aplicar controles antifraude;
8. definir observabilidade sem dados sensíveis;
9. adicionar políticas extensíveis para limite diário, categoria e estabelecimento;
10. definir estorno e reconciliação.

Esses itens não foram implementados porque dependem de regras ou infraestrutura não fornecidas. Documentar o limite é preferível a simular segurança ou consistência incompletas.

---

## 18. Pontos fortes para destacar

- domínio independente de framework e com invariantes explícitas;
- contrato REST versionado, consistente e documentado;
- tratamento uniforme de erros com RFC 9457;
- senha protegida com salt, pepper e PBKDF2;
- PAN ausente de URLs e mascarado em responses/logs;
- débito seguro sob concorrência entre instâncias;
- migrações reproduzíveis e schema validado;
- testes de integração com a mesma imagem MySQL do cenário;
- perfis operacionais sem alterar a segurança do contrato;
- histórico de commits incremental e legível;
- riscos e decisões fora do escopo documentados de forma explícita.

## 19. Fechamento sugerido

“A solução é pequena por escopo, mas trata os pontos que tornam um autorizador difícil na prática: invariantes de domínio, concorrência, segurança de credenciais e clareza do contrato. Eu mantive o núcleo solicitado, isolei frameworks nas bordas e documentei tanto as decisões quanto os riscos residuais. O resultado é uma base simples de operar hoje e preparada para crescer sem deslocar as regras de negócio para controllers ou queries.”
