# Documentação da solução

Este diretório é a fonte versionada das decisões técnicas, requisitos derivados, tarefas, estratégia de testes e handoff entre agentes do Mini Autorizador.

O README da raiz define o problema e o comportamento de negócio. Quando seus exemplos HTTP conflitarem com segurança ou boas práticas REST, o contrato canônico destes documentos prevalece, conforme `00-handoff.md`.

Esta documentação descreve a branch `feature/api-following-good-practices`. A branch `feature/api-following-readme-literal` deriva dela e mantém documentação própria das divergências intencionais.

## Ordem de leitura

1. `00-handoff.md`
2. `01-requisitos.md`
3. `02-especificacao-tecnica.md`
4. `03-tarefas.md`
5. `04-plano-testes.md`
6. `05-seguranca-dados-sensiveis.md`
7. `06-ddd-padroes-design.md`
8. `07-estrategia-commits.md`
9. `08-decisoes-implementacao.md`

Decisões descobertas durante a implementação devem ser registradas aqui no mesmo incremento e commit da mudança relacionada, ou em um commit `docs` próprio quando forem independentes.

O diretório local `sdd/` é uma área de planejamento de agentes, está ignorado pelo Git e não faz parte da entrega.
