# Execução local

Estas instruções executam somente o MySQL em Docker, usando o arquivo fornecido
no desafio. A aplicação é executada diretamente no host.

## Pré-requisitos

- Java 21;
- Docker com Docker Compose.

O projeto inclui Maven Wrapper, portanto não é necessário instalar Maven.

## Iniciar o banco

Na raiz do projeto, suba apenas o serviço `mysql` declarado no Compose do
desafio:

```bash
docker compose -f docker/docker-compose.yml up -d mysql
```

O container publica o MySQL 5.7 em `localhost:3306` e cria o banco
`miniautorizador`. As tabelas são criadas automaticamente pelo Flyway quando a
aplicação inicia.

## Executar a aplicação

Execute a aplicação no host com o perfil `avaliacao`:

```bash
SPRING_PROFILES_ACTIVE=avaliacao \
./mvnw spring-boot:run
```

A aplicação estará disponível em `http://localhost:8080`. Para verificar se a
inicialização terminou corretamente:

```bash
curl --fail http://localhost:8080/actuator/health
```

O endpoint deve responder com status `UP`. Encerre a aplicação com `Ctrl+C`.

Para encerrar e remover o container do banco:

```bash
docker compose -f docker/docker-compose.yml down
```
