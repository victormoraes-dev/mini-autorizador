# Estratégia de commits e handoff

## Objetivo

Construir um histórico Git que permita ao avaliador entender como a solução evoluiu e possibilite a outro agente continuar o trabalho sem depender de contexto externo.

## Convenção

Usar Conventional Commits no formato:

```text
<type>(<optional-scope>): <human description>
```

O tipo e o escopo técnico permanecem em inglês para aderir à convenção. A descrição depois dos dois-pontos deve ser escrita em português do Brasil.

Tipos preferenciais:

- `docs`: requisitos, decisões, handoff e documentação de uso;
- `build`: Maven, dependências e configuração do ciclo de build;
- `feat`: capacidade funcional observável;
- `fix`: correção de comportamento defeituoso;
- `test`: cobertura automatizada sem mudança funcional principal;
- `refactor`: mudança estrutural sem alteração de comportamento;
- `chore`: manutenção de repositório que não cabe nos tipos anteriores.

## Regras de qualidade

1. Um commit deve representar uma unidade lógica completa e revisável.
2. A mensagem deve descrever o resultado, não a atividade mecânica realizada.
3. A descrição deve ser curta, humana, específica, escrita no imperativo e em português do Brasil.
4. O commit deve incluir os testes diretamente ligados à capacidade quando eles fizerem parte natural do incremento.
5. Testes abrangentes de contrato ou integração podem ser commits próprios.
6. Alterações não relacionadas não devem ser agrupadas.
7. Antes do commit, revisar `git diff`, executar a validação proporcional e conferir os arquivos staged.
8. O histórico final não deve conter commits `WIP`, mensagens genéricas ou segredos.

## Sequência executada

1. `build: configura a aplicação Maven com Java 21`
2. `feat: implementa o domínio e a persistência de cartões`
3. `feat: expõe a API REST versionada com respostas estruturadas`
4. `feat: protege a API com autenticação e rate limiting`
5. `test: cobre regras, contratos e concorrência`
6. `docs: documenta o contrato seguro e as decisões`
7. `fix: permite injetar o filtro de rate limiting` (correção revelada pelo primeiro `verify` completo)

A branch literal nasce do sétimo commit. Seus commits alteram somente os pontos em que o README prescreve um contrato diferente, para que o diff entre branches seja didático.

## Handoff entre agentes

Ao assumir o trabalho, o agente deve:

1. ler `00-handoff.md` e a tarefa ativa;
2. inspecionar `git status` e os commits recentes;
3. preservar mudanças existentes que não sejam de sua autoria;
4. executar os testes relevantes antes de alterar uma parte estável;
5. atualizar a documentação em `docs/` quando uma decisão relevante mudar;
6. encerrar a parte lógica com um commit semântico e registrar bloqueios reais no handoff.

## Critério de conclusão

O histórico está adequado quando `git log --oneline --decorate` narra a arquitetura e as capacidades entregues, cada commit tem escopo coeso e a árvore de trabalho termina limpa.
