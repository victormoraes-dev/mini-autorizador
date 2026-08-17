# DDD e padrões de design

## Bounded context

O contexto `Card Authorization` emite cartões de benefício, consulta saldo e decide autorizações. “Transação” representa uma tentativa; não é aggregate persistido neste escopo.

## Linguagem ubíqua

- `Card`: cartão emitido.
- `CardId`: identidade pública opaca.
- `CardNumber`: PAN sensível e identificador de duplicidade.
- `Balance`: saldo disponível.
- `Transaction`: solicitação de débito.
- `AuthorizationResult`: decisão da entidade.

Código e contrato REST usam inglês. Nomes do README são traduzidos apenas ao interpretar suas regras de negócio.

## Aggregate e invariantes

`Card` é aggregate root. Ele garante:

- saldo inicial `500.00`;
- hash obrigatório;
- senha válida antes da análise de saldo;
- débito somente com valor positivo e coberto;
- saldo nunca negativo no modelo em memória.

Consistência entre instâncias é complementada pelo update condicional no repositório.

## Camadas

### Domain

Entidades, value objects, enums e `PasswordHasher` como abstração de serviço de domínio. Zero dependência de frameworks.

### Application

Input ports, repository port, serviços transacionais, projeções seguras e exceptions de caso de uso.

### Infrastructure

JPA, Flyway, PBKDF2, TLS e adaptadores.

### API

Controllers, DTOs, OpenAPI e tradução para Problem Details.

## Padrões aplicados

### Aggregate Root

`Card` concentra autorização e débito. Evita regras espalhadas em controller/repository.

### Value Object

`CardId`, `CardNumber`, `CardPassword`, `PasswordHash`, `Money` e `Balance` validam invariantes na construção e são imutáveis, exceto a substituição controlada de `Balance` dentro do aggregate.

### Factory Method

`Card.issue`, `Card.restore` e `Transaction.request` expressam criação nova, reconstituição e comando. Impedem estados parcialmente válidos.

### Strategy

`PasswordHasher` permite PBKDF2 em runtime e fake determinístico em testes. Algoritmo não contamina a entidade.

Não há Strategy de response por perfil: o contrato seguro é único e não deve variar por ambiente.

### Repository

`CardRepository` oferece operações na linguagem do domínio. JPA permanece detalhe de infraestrutura.

### Ports and Adapters

Controllers dependem de input ports; serviços dependem de output ports; adaptadores implementam bordas. Facilita testes e troca de tecnologia.

### Data Mapper / Anti-Corruption Layer

`CardPersistenceMapper` separa JPA do aggregate. DTO mapping separa HTTP do domínio. Nenhuma entidade é serializada diretamente.

### DTO / Projection

`CardResponse` e `CardDetails` aplicam minimização de dados. Não contêm password hash, timestamps ou ID interno.

### Optimistic Concurrency por operação atômica

O update `balance >= amount` funciona como compare-and-set no banco e garante consistência entre instâncias sem lock distribuído.

### Unit of Work

`@Transactional` delimita casos de uso. Regra continua no domínio; demarcação técnica fica na aplicação/infraestrutura.

### Controller Advice

`HttpErrorHandler` traduz exceptions/resultados para RFC 9457 em um ponto único.

### Problem Details

Padrão de contrato para erros reduz DTOs ad hoc e oferece códigos de máquina estáveis sem vazar detalhes internos.

### Method Object / Result Type

`TransactionAuthorizationResult` representa decisão da aplicação. API decide status/Problem Details sem acoplar domínio a HTTP.

### Database Migration

Flyway aplica evolução incremental V1→V2, inclusive backfill de UUID para dados existentes.

### OpenAPI as executable documentation

Anotações ficam na API e descrevem schemas seguros, `writeOnly`, status e erros. Nunca entram no domínio.

## Oportunidades futuras

### Idempotency Key

Útil para evitar repetição de criação/autorização após timeout. Exige armazenamento de chaves e política de expiração; fora do escopo atual.

### Domain Events

`CardCreated`, `TransactionAuthorized` e `TransactionDenied` seriam úteis para auditoria. Exigem outbox para entrega confiável; não implementar parcialmente.

### Specification / Policy

Novas regras de autorização (limites, categoria, antifraude) podem ser policies compostas. Para três regras fixas, métodos da entidade são mais simples.

### Tokenization Adapter

Uma port para token vault substituiria PAN em texto claro. Requer infraestrutura PCI externa.

### Circuit Breaker

Somente necessário ao integrar serviços remotos; aplicar agora seria complexidade acidental.

## Padrões evitados

- Active Record: mistura persistência e domínio.
- Service Locator/Singleton manual: oculta dependências.
- Generic Repository: perde linguagem de domínio e operações atômicas específicas.
- Anemic Domain Model: desloca regras para scripts de serviço.
- Responses condicionais por perfil: criam contratos imprevisíveis.
- Exposição direta de JPA/domain: vaza dados e acopla API.

## Critérios arquiteturais

- domínio compila/testa sem Spring;
- controllers não calculam autorização/saldo;
- repositório não decide senha;
- respostas não dependem do modelo persistente;
- segurança do contrato é idêntica nos perfis;
- cada padrão possui problema concreto e teste associado.
