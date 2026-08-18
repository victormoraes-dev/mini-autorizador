# Teste de programação - VR Benefícios

Como parte do processo de seleção, gostaríamos que você desenvolvesse um pequeno sistema, para que possamos ver melhor o seu trabalho.

Fique à vontade para criar a partir dos requisitos abaixo. Se algo não ficou claro, pode assumir o que ficar mais claro para você, e, por favor, *documente suas suposições*.

Crie o projeto no seu Github para que possamos ver os passos realizados (por meio dos commits) para a implementação da solução.

Caso sua solução seja aprovada, faremos uma entrevista contigo, e a utilizaremos durante a entrevista.

Se quiser documentar outros detalhes da sua solução (como *design patterns* e boas práticas utilizadas e outras decisões de projeto) pode mandar ver!

# Mini autorizador

A VR processa todos os dias diversas transações de Vale Refeição e Vale Alimentação, entre outras.
De forma breve, as transações saem das maquininhas de cartão e chegam até uma de nossas aplicações, conhecida como *autorizador*, que realiza uma série de verificações e análises. Essas também são conhecidas como *regras de autorização*. 

Ao final do processo, o autorizador toma uma decisão, aprovando ou não a transação: 
* se aprovada, o valor da transação é debitado do saldo disponível do benefício, e informamos à maquininha que tudo ocorreu bem. 
* senão, apenas informamos o que impede a transação de ser feita e o processo se encerra.

Sua tarefa será construir um *mini-autorizador*. Este será uma aplicação Spring Boot com interface totalmente REST que permita:

 * a criação de cartões (todo cartão deverá ser criado com um saldo inicial de R$500,00)
 * a obtenção de saldo do cartão
 * a autorização de transações realizadas usando os cartões previamente criados como meio de pagamento

## Regras de autorização a serem implementadas

Uma transação pode ser autorizada se:
   * o cartão existir
   * a senha do cartão for a correta
   * o cartão possuir saldo disponível

Caso uma dessas regras não ser atendida, a transação não será autorizada.

## Demais instruções

O projeto contém um docker-compose.yml com 1 banco de dados relacional e outro não relacional.
Sinta-se à vontade para utilizar um deles. Se quiser, pode deixar comentado o banco que não for utilizar, mas não altere o que foi declarado para o banco que você selecionou. 

Não é necessário persistir a transação. Mas é necessário persistir o cartão criado e alterar o saldo do cartão caso uma transação ser autorizada pelo sistema.

Serão analisados o estilo e a qualidade do seu código, bem como as técnicas utilizadas para sua escrita. Ficaremos felizes também se você utilizar testes automatizados como ferramenta auxiliar de criação da solução.

Também, na avaliação da sua solução, serão realizados os seguintes testes, nesta ordem:

 * criação de um cartão
 * verificação do saldo do cartão recém-criado
 * realização de diversas transações, verificando-se o saldo em seguida, até que o sistema retorne informação de saldo insuficiente
 * realização de uma transação com senha inválida
 * realização de uma transação com cartão inexistente

Esses testes serão realizados:
* rodando o docker-compose enviado para você
* rodando a aplicação 

Para isso, é importante que os contratos abaixo sejam respeitados:

## Contratos dos serviços

### Criar novo cartão
```
Method: POST
URL: http://localhost:8080/cartoes
Body (json):
{
    "numeroCartao": "6549873025634501",
    "senha": "1234"
}
```
#### Possíveis respostas:
```
Criação com sucesso:
   Status Code: 201
   Body (json):
   {
      "senha": "1234",
      "numeroCartao": "6549873025634501"
   } 
-----------------------------------------
Caso o cartão já exista:
   Status Code: 422
   Body (json):
   {
      "senha": "1234",
      "numeroCartao": "6549873025634501"
   } 
```

### Obter saldo do Cartão
```
Method: GET
URL: http://localhost:8080/cartoes/{numeroCartao} , onde {numeroCartao} é o número do cartão que se deseja consultar
```

#### Possíveis respostas:
```
Obtenção com sucesso:
   Status Code: 200
   Body: 495.15 
-----------------------------------------
Caso o cartão não exista:
   Status Code: 404 
   Sem Body
```

### Realizar uma Transação
```
Method: POST
URL: http://localhost:8080/transacoes
Body (json):
{
    "numeroCartao": "6549873025634501",
    "senhaCartao": "1234",
    "valor": 10.00
}
```

#### Possíveis respostas:
```
Transação realizada com sucesso:
   Status Code: 201
   Body: OK 
-----------------------------------------
Caso alguma regra de autorização tenha barrado a mesma:
   Status Code: 422 
   Body: SALDO_INSUFICIENTE|SENHA_INVALIDA|CARTAO_INEXISTENTE (dependendo da regra que impediu a autorização)
```

Desafios (não obrigatórios): 
 * é possível construir a solução inteira sem utilizar nenhum if. Só não pode usar *break* e *continue*! 
 * como garantir que 2 transações disparadas ao mesmo tempo não causem problemas relacionados à concorrência?
Exemplo: dado que um cartão possua R$10.00 de saldo. Se fizermos 2 transações de R$10.00 ao mesmo tempo, em instâncias diferentes da aplicação, como o sistema deverá se comportar?

---

# Implementação

## Pré-requisitos

- Java 21
- Docker com Docker Compose

O projeto usa Maven Wrapper; não é necessário instalar Maven globalmente.

## Contrato implementado

Esta branch implementa literalmente os contratos descritos acima:

- `POST /cartoes` recebe `numeroCartao` e `senha` e devolve os mesmos campos;
- `GET /cartoes/{numeroCartao}` devolve somente o saldo em texto;
- `POST /transacoes` recebe `numeroCartao`, `senhaCartao` e `valor` e devolve `OK` ou o motivo textual da recusa.

Não há autenticação, rate limiting nem contrato OpenAPI nesta branch, pois o enunciado não os solicita. Também não há envelope JSON para saldo e transação nem tratamento adicional de erros fora das respostas enumeradas pelo README.

Consulte [`docs/09-comparacao-contratos.md`](docs/09-comparacao-contratos.md) para entender cada divergência em relação à branch `feature/api-following-good-practices`.

## Execução local

Suba o MySQL 5.7 fornecido:

```bash
docker compose -f docker/docker-compose.yml up -d mysql
```

Inicie a aplicação:

```bash
SPRING_PROFILES_ACTIVE=avaliacao \
./mvnw spring-boot:run
```

A API estará em `http://localhost:8080` e não exige headers adicionais.

## Testes

Testes unitários:

```bash
./mvnw test
```

Suíte completa, incluindo integração com a imagem `mysql:5.7` via Testcontainers:

```bash
./mvnw clean verify
```

## Arquitetura e decisões

O contrato externo segue o README mesmo quando isso expõe PAN/senha ou usa status e bodies não convencionais. Onde o enunciado é silencioso, permanecem o domínio rico, senha persistida com hash, migrações Flyway e débito atômico para proteger o saldo em concorrência.
