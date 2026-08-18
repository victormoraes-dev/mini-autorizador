# Documentação da solução

Este diretório é a fonte versionada das decisões técnicas, requisitos derivados, tarefas, estratégia de testes e handoff entre agentes do Mini Autorizador.

O README da raiz define o problema e o comportamento de negócio. Os documentos `00` a `08` descrevem a branch-base `feature/api-following-good-practices` e são preservados aqui como referência. Nesta branch literal, `09-comparacao-contratos.md` documenta o contrato vigente e tem precedência sobre eles.

## Ordem de leitura

1. `09-comparacao-contratos.md` — contrato vigente nesta branch;
2. `00-handoff.md`;
3. `01-requisitos.md`;
4. `02-especificacao-tecnica.md`;
5. `03-tarefas.md`;
6. `04-plano-testes.md`;
7. `05-seguranca-dados-sensiveis.md`;
8. `06-ddd-padroes-design.md`;
9. `07-estrategia-commits.md`;
10. `08-decisoes-implementacao.md`.

Decisões descobertas durante a implementação devem ser registradas aqui no mesmo incremento e commit da mudança relacionada, ou em um commit `docs` próprio quando forem independentes.

O diretório local `sdd/` é uma área de planejamento de agentes, está ignorado pelo Git e não faz parte da entrega.
