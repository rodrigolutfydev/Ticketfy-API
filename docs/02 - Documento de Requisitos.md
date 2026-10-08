# Ticketfy 02 - Documento de Requisitos

Oct 7, 2026

## Nota sobre este documento

Os requisitos abaixo derivam das funcionalidades informadas para o Ticketfy e foram revisados contra o código atual, que é a fonte de verdade. O backend é feito em Java e Spring Boot, exposto como API REST, com PostgreSQL e Flyway. Os pacotes são organizados por domínio: `user`, `event`, `tickettype`, `order`, `payment`, `ticket`, `coupon`, `dashboard`, `payout`, `audit`, `auth` e `privacy`. Cada requisito informa sua situação no código. O que ainda não foi implementado aparece como "não implementado" ou "planejado", coerente com o [roadmap](ROADMAP.md).

**Prioridades**

| Prioridade | Significado |
| --- | --- |
| Alta | Necessário para o fluxo principal funcionar: sem ele, não há evento, compra ou ingresso. |
| Média | Necessário para a versão completa, mas o fluxo principal funciona sem ele. |
| Baixa | Desejável; pode ficar para uma versão posterior. |

**Situação**

| Situação | Significado |
| --- | --- |
| Implementado | O código atende à descrição e aos critérios de aceite. |
| Implementado com divergência | O código atende à maior parte do requisito; a divergência está descrita no próprio requisito e na seção 6. |
| Não implementado | Não existe no código. Quando está no roadmap, aparece como planejado. |

**Premissas herdadas do Documento de Visão**

- P01: o sistema possui três perfis de acesso: usuário (`USER`, o comprador), organizador (`ORGANIZER`) e administrador (`ADMIN`).
- P02: o backend é entregue como API REST; existe um frontend React, com publicação planejada.
- P03: o sistema atende eventos presenciais; o local é informado nos campos do próprio evento.

## 1. Requisitos Funcionais

São 25 requisitos funcionais. RF01 a RF18 vêm da versão original: 13 de prioridade alta e 5 de prioridade média. RF19 a RF25 registram funcionalidades implementadas depois, sem requisito na versão original.

### RF01 — Cadastro de usuário

**Descrição:** O sistema deve permitir que uma pessoa crie uma conta informando nome, e-mail e senha. **Atores:** Cliente. **Prioridade:** Alta. **Situação:** Implementado (`POST /users`). **Critérios de aceite:**

- Um cadastro com dados válidos cria a conta com o perfil `USER`.
- Um e-mail já cadastrado é recusado (RN01).
- Campos obrigatórios ausentes ou inválidos retornam erro de validação indicando o campo.
- A senha nunca é retornada nas respostas da API.
- E-mails do domínio reservado às contas excluídas (`deleted.ticketfy.invalid`) são recusados.

### RF02 — Autenticação

**Descrição:** O sistema deve autenticar o usuário por e-mail e senha e fornecer um token de acesso para as requisições seguintes. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`POST /auth/login`, `/auth/refresh`, `/auth/logout` e `/auth/logout-all`). **Critérios de aceite:**

- Credenciais corretas retornam um access token de 10 minutos e gravam o refresh token em cookie `HttpOnly`.
- Credenciais incorretas são recusadas sem indicar qual campo está errado.
- Contas excluídas não conseguem se autenticar (RN03).
- Requisições a endpoints protegidos sem token válido, ou com sessão revogada ou vencida, são recusadas.

### RF03 — Controle de acesso por perfil

**Descrição:** O sistema deve restringir cada operação ao perfil autorizado a executá-la. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`@PreAuthorize` por rota e conferência de propriedade nos services). **Critérios de aceite:**

- Um perfil sem permissão recebe resposta de acesso negado (RN04 a RN06).
- Um organizador não altera eventos de outro organizador (RN04).
- Um cliente não acessa pedidos ou ingressos de outro cliente (RN05); o pedido de outro usuário responde 404, sem revelar que existe.

### RF04 — Gerenciamento de organizadores

**Descrição:** O sistema deve permitir que um usuário passe a ser organizador. **Atores:** Organizador, Administrador. **Prioridade:** Média. **Situação:** Implementado com divergência: o próprio usuário se promove por `POST /users/me/organizer`, sem aprovação (Q02). A desativação de organizadores não está implementada; o administrador só bloqueia os saques de um organizador (RF22). **Critérios de aceite:**

- Um usuário promovido a organizador consegue criar eventos.
- Um organizador ou administrador não pode ser promovido de novo.

### RF05 — Gerenciamento de locais

**Descrição:** A versão original previa cadastrar, consultar e editar os locais onde os eventos acontecem, como entidade própria. **Atores:** Organizador, Administrador. **Prioridade:** Média. **Situação:** Substituído. Não há domínio de local: nome do local, endereço, cidade e estado são campos do evento (RF06). **Critérios de aceite:**

- O evento é cadastrado com nome do local, endereço, cidade e estado (UF com 2 letras).
- Campos obrigatórios ausentes retornam erro de validação.

### RF06 — Cadastro de evento

**Descrição:** O sistema deve permitir que o organizador cadastre um evento com nome, descrição, imagem de capa por endereço https, local (nome, endereço, cidade e estado) e data e hora de início e término. **Atores:** Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`POST /events`). **Critérios de aceite:**

- O evento criado fica vinculado ao usuário autenticado como organizador (RN08).
- Um evento com data de início no passado ou término anterior ao início é recusado (RN07).

O limite de ingressos por pedido, previsto aqui na versão original, é definido em cada tipo de ingresso (RF09, RN13).

### RF07 — Edição, cancelamento e exclusão de evento

**Descrição:** O sistema deve permitir que o organizador edite os dados do seu evento, cancele o evento e exclua um evento sem vendas. **Atores:** Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`PUT /events/{id}`, `POST /events/{id}/cancel` e `DELETE /events/{id}`). Não existe "encerrar para novas vendas" sem cancelar. **Critérios de aceite:**

- Apenas o organizador responsável ou um administrador edita, cancela ou exclui o evento (RN04).
- Um evento cancelado não aceita novas compras nem check-in (RN09).
- O cancelamento reembolsa automaticamente os pedidos pagos, cancela os pendentes e responde com a contagem de cada caso (Q07).
- A exclusão é lógica e só é aceita quando nenhum ingresso foi vendido ou reservado.

### RF08 — Consulta de eventos

**Descrição:** O sistema deve listar os eventos disponíveis e exibir os detalhes de cada um, incluindo local e tipos de ingresso. **Atores:** Cliente, Organizador, Administrador e visitantes sem conta. **Prioridade:** Alta. **Situação:** Implementado com divergência (`GET /events`, `GET /events/{id}` e `GET /events/{id}/ticket-types`, públicos). A busca filtra por nome, cidade, destaque e esgotado, com paginação. **Critérios de aceite:**

- A listagem exibe apenas eventos ativos e não cancelados. Divergência: eventos que já começaram ou terminaram continuam na listagem (ver RN09).
- O detalhe do evento informa os tipos de ingresso com preço e disponibilidade.

### RF09 — Gerenciamento de tipos de ingresso

**Descrição:** O sistema deve permitir que o organizador crie, edite e desative tipos de ingresso (lotes) de um evento, cada um com nome, descrição, preço, quantidade total e limite de ingressos por pedido. **Atores:** Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado com divergência: criação e edição existem (`POST` e `PATCH /events/{id}/ticket-types`); a desativação de um lote não está implementada, embora a coluna `active` exista. **Critérios de aceite:**

- Um evento aceita mais de um tipo de ingresso, cada um com estoque próprio e nome único no evento.
- Preço negativo ou quantidade menor que 1 são recusados (RN10).
- A quantidade total não pode ser reduzida abaixo do número de ingressos já vendidos ou reservados (RN11).
- Uma mudança de preço é registrada na auditoria e não altera pedidos existentes (RN14).

### Sugestões

Os itens abaixo não faziam parte dos requisitos originais. A situação de cada um foi conferida no código e no roadmap.

| ID | Sugestão | Situação |
| --- | --- | --- |
| SUG01 | Recuperação de senha por e-mail. | Não implementado. Depende dos e-mails transacionais com Brevo, que estão planejados. |
| SUG02 | Envio de e-mail de confirmação após a compra. | Planejado: e-mails transacionais com Brevo. |
| SUG03 | Reserva temporária de ingressos durante o pagamento. | Implementado: incorporada ao RF10 e à RN12, com prazo de 15 minutos. |
| SUG04 | Relatório de vendas por evento para o organizador. | Implementado: painel do evento e do organizador (RF21). |

## 2. Requisitos Não Funcionais

Os requisitos foram dimensionados para um projeto acadêmico. A aplicação roda em produção no Render, com PostgreSQL no Neon. Metas numéricas marcadas como sugestão podem ser ajustadas pela equipe.

| ID | Categoria | Requisito | Verificação |
| --- | --- | --- | --- |
| RNF01 | Segurança | As senhas devem ser armazenadas com hash BCrypt, nunca em texto puro. | Inspeção do banco: nenhuma senha legível. |
| RNF02 | Segurança | O acesso aos endpoints protegidos deve exigir um access token JWT válido de uma sessão ativa. | Requisição sem token, com token expirado ou de sessão revogada é recusada. |
| RNF03 | Segurança | Os dados de entrada devem ser validados no backend com Bean Validation antes de qualquer processamento. | Payload inválido retorna erro de validação sem gravar dados. |
| RNF04 | Proteção de dados | O sistema deve coletar apenas os dados pessoais necessários às funcionalidades e não deve expor senhas ou tokens em respostas e logs, seguindo os princípios da LGPD. Documento e chave Pix do organizador são cifrados com AES-256-GCM. O titular pode exportar os próprios dados e excluir a conta (RF25). O Ticketfy não recebe dados de cartão. A leitura jurídica da retenção de dados está pendente de revisão. | Revisão das respostas da API, dos logs e do banco. |
| RNF05 | Confiabilidade | Compra, pagamento, emissão, reembolso e cancelamento devem ocorrer em transações: ou todas as alterações são gravadas, ou nenhuma. | Teste com falha simulada no meio da operação não deixa dados parciais. |
| RNF06 | Confiabilidade | Os erros devem ser tratados de forma centralizada (`@RestControllerAdvice`), com respostas no padrão RFC 7807, `type` estável e códigos HTTP adequados. | Erros de validação, autorização e recurso inexistente retornam formato único. |
| RNF07 | Escalabilidade | A disponibilidade de ingressos deve permanecer correta sob requisições simultâneas de compra. | Teste com requisições concorrentes para a última unidade resulta em uma única venda. |
| RNF08 | Escalabilidade | A API não deve depender de `HttpSession`: o estado da sessão fica no banco (`auth_sessions`), o que permite mais de uma instância da aplicação. Os limites de tentativas ficam em memória, por instância. | Autenticação funciona apenas com o access token e a sessão no banco. |
| RNF09 | Performance | Consultas de eventos e de ingressos devem responder em até 1 segundo em ambiente local com massa de teste de até 1.000 eventos. Sugestão — não confirmado no projeto; o teste de carga com k6 está planejado. | Medição com ferramenta de teste de API. |
| RNF10 | Disponibilidade | Uma falha em uma requisição não deve interromper a aplicação; erros inesperados retornam resposta de erro e a API continua atendendo. | Requisição que gera exceção não derruba o serviço. |
| RNF11 | Usabilidade | A API deve seguir convenções REST: recursos nomeados por substantivos, métodos HTTP adequados e mensagens de erro compreensíveis para quem consome a API. | Revisão dos endpoints e das mensagens. |
| RNF12 | Manutenibilidade | O código deve ser organizado em pacotes por domínio e escrito em inglês (classes, pacotes e variáveis). | Revisão da estrutura do projeto. |
| RNF13 | Manutenibilidade | Toda alteração no esquema do banco deve ser feita por migration Flyway versionada. | O banco é recriado do zero apenas com as migrations. |
| RNF14 | Manutenibilidade | As regras de negócio devem ter testes automatizados, com integração em PostgreSQL real (Testcontainers). O `./mvnw verify` roda o `jacoco:check`, que exige no projeto inteiro pelo menos 89% de linhas cobertas (`LINE` 0,89) e 75% de branches cobertos (`BRANCH` 0,75); o CI falha abaixo disso. | Build `./mvnw verify` no GitHub Actions e relatório JaCoCo. |
| RNF15 | Proteção de dados | A remoção de usuários deve preservar o histórico de pedidos, ingressos e lançamentos financeiros. A exclusão da conta anonimiza nome, e-mail, foto e senha e grava `deleted_at`, mantendo o mesmo id. | Conta excluída continua no banco, anonimizada, com o histórico ligado a ela. |

## 3. Regras de Negócio

São 23 regras, agrupadas por tema. Regras não implementadas ou implementadas com divergência estão marcadas e também aparecem na seção 6.

| ID | Tema | Regra |
| --- | --- | --- |
| RN01 | Usuários | O e-mail é único no sistema: não pode haver duas contas com o mesmo e-mail. |
| RN02 | Usuários | Cada usuário possui um único perfil: `USER`, `ORGANIZER` ou `ADMIN`. |
| RN03 | Usuários | Apenas contas não excluídas podem se autenticar. A exclusão anonimiza a conta e não apaga o histórico. A suspensão de uma conta pelo administrador não está implementada. |
| RN04 | Permissões | O organizador só pode gerenciar, consultar vendas e validar ingressos dos eventos que criou. O administrador pode agir sobre qualquer evento. |
| RN05 | Permissões | O cliente só pode consultar, cancelar e reembolsar os próprios pedidos, e consultar e transferir os ingressos que possui. |
| RN06 | Permissões | Apenas o administrador pode suspender e reativar contas. Não implementado: hoje o administrador só bloqueia e desbloqueia os saques de um organizador. |
| RN07 | Eventos | As datas de início e de término do evento devem ser futuras no cadastro e na edição, e a data de término deve ser posterior à de início. |
| RN08 | Eventos | Todo evento pertence a um único organizador e informa o local (nome, endereço, cidade e estado). |
| RN09 | Eventos | Eventos cancelados ou excluídos não aceitam novas compras. Não implementado para eventos já iniciados ou realizados: a compra não confere a data do evento. |
| RN10 | Ingressos | Todo tipo de ingresso pertence a um evento, com nome único no evento, preço maior ou igual a zero e quantidade total maior que zero. |
| RN11 | Ingressos | A quantidade total de um tipo de ingresso não pode ser reduzida abaixo da quantidade já vendida ou reservada. |
| RN12 | Ingressos | Disponibilidade = quantidade total − quantidade vendida, e nunca pode ser negativa. A quantidade vendida inclui as unidades reservadas por pedidos pendentes. Ao criar o pedido, as unidades ficam reservadas por 15 minutos (`ORDER_RESERVATION_MINUTES`); sem pagamento nesse prazo, o pedido expira e as unidades voltam à disponibilidade. |
| RN13 | Compras | A quantidade comprada de cada tipo não pode exceder a disponibilidade nem o limite por pedido definido pelo organizador naquele tipo de ingresso. Todos os itens de um pedido pertencem ao mesmo evento. Divergência: qualquer perfil autenticado compra; a restrição ao perfil de cliente prevista originalmente não está implementada. |
| RN14 | Compras | O preço de cada ingresso é registrado no pedido no momento da compra. Alterações posteriores de preço não afetam pedidos existentes. |
| RN15 | Pagamentos | Cada pagamento está vinculado a um único pedido, e seu valor corresponde ao total do pedido depois do desconto. Um pedido tem no máximo um pagamento aprovado. Pedidos de valor zero não têm pagamento. |
| RN16 | Pagamentos | Ingressos só são emitidos após a aprovação do pagamento ou, em pedidos de valor zero, na criação do pedido. |
| RN17 | Ingressos | Cada ingresso emitido possui um código único de 16 caracteres, gerado aleatoriamente. |
| RN18 | Cancelamentos | O reembolso de um pedido cancela todos os seus ingressos e devolve as unidades à disponibilidade. Um ingresso cancelado não pode mais ser validado. |
| RN19 | Cancelamentos | Pedidos com ingressos já utilizados não podem ser reembolsados. |
| RN20 | Cancelamentos | O reembolso pelo comprador vale para o pedido inteiro, até 48 horas antes do início do evento (`REFUND_DEADLINE_HOURS`), e é recusado se algum ingresso do pedido foi transferido. O cancelamento do evento reembolsa todos os pedidos pagos, sem prazo. |
| RN21 | Validação | Um ingresso só pode ser validado uma vez; após a validação, passa à situação `USED`. |
| RN22 | Validação | Um ingresso só é aceito na validação pelo organizador do evento ao qual pertence, ou por um administrador, e nunca em evento cancelado. |
| RN23 | Validação | A validação na entrada (check-in) é feita pelo organizador do evento, apenas nos eventos que criou (RN04), ou por um administrador. |

## 4. Matriz de rastreabilidade

Apenas o UC01 — Realizar compra de ingresso está documentado. Os casos UC02 a UC05 eram citados no UC01 como inclusões e não têm especificação própria. Onde não há caso de uso, a matriz indica "Não documentado".

| Requisito | Caso de uso | Situação do caso de uso | Regras de negócio |
| --- | --- | --- | --- |
| RF01 Cadastro de usuário | — | Não documentado | RN01, RN02 |
| RF02 Autenticação | UC02 Autenticar usuário | Citado no UC01, sem especificação | RN03 |
| RF03 Controle de acesso por perfil | UC02 Autenticar usuário (parcial) | Citado no UC01, sem especificação | RN04, RN05, RN06 |
| RF04 Gerenciamento de organizadores | — | Não documentado | RN02, RN06 |
| RF05 Gerenciamento de locais (substituído) | — | Não se aplica | RN08 |
| RF06 Cadastro de evento | — | Não documentado | RN07, RN08 |
| RF07 Edição, cancelamento e exclusão de evento | — | Não documentado | RN04, RN09, RN20 |
| RF08 Consulta de eventos | — | Não documentado | RN09, RN12 |
| RF09 Gerenciamento de tipos de ingresso | — | Não documentado | RN10, RN11, RN14 |
| RF10 Controle de disponibilidade | UC01 | Documentado | RN12 |
| RF11 Compra de ingressos | UC01 Realizar compra de ingresso | Documentado | RN09, RN13, RN14 |
| RF12 Registro de pagamento | UC01 | Documentado | RN15, RN16 |
| RF13 Emissão de ingresso | UC01 | Documentado | RN16, RN17 |
| RF14 Consulta de ingressos | UC01 (pós-condição) | Documentado em parte | RN05 |
| RF15 Reembolso de pedido | — | Não documentado | RN05, RN18, RN19, RN20 |
| RF16 Validação de ingresso | — | Não documentado | RN04, RN18, RN21, RN22, RN23 |
| RF17 Consulta de participantes | — | Não documentado | RN04 |
| RF18 Gerenciamento de usuários | — | Não documentado | RN03, RN06 |
| RF19 Cupons de desconto | UC01 (fluxo alternativo) | Documentado em parte | RN13, RN15 |
| RF20 Transferência de ingresso | — | Não documentado | RN05, RN20 |
| RF21 Painel do organizador | — | Não documentado | RN04 |
| RF22 Financeiro do organizador | — | Não documentado | RN04, RN06 |
| RF23 Auditoria | — | Não documentado | — |
| RF24 Sessões e senha | — | Não documentado | RN03 |
| RF25 Privacidade | — | Não documentado | RN03 |

As 23 regras estão ligadas a pelo menos um requisito. RF23 é o único requisito sem regra de negócio própria; suas regras estão na Documentação de Arquitetura (DA13).

## 5. Critérios gerais de aceite

O sistema é considerado aceito quando todos os critérios abaixo forem atendidos em testes automatizados ou em demonstração registrada.

| ID | Critério | Requisitos cobertos |
| --- | --- | --- |
| CGA01 | Todos os requisitos funcionais de prioridade alta estão implementados e atendem aos seus critérios de aceite. | RF01–RF03, RF06–RF14, RF16 |
| CGA02 | O fluxo completo funciona de ponta a ponta: organizador cadastra evento e tipos de ingresso; cliente compra e paga; ingresso é emitido, consultado e validado uma única vez. | RF06, RF09, RF11–RF14, RF16 |
| CGA03 | Cada perfil acessa apenas as operações permitidas, e as tentativas fora da permissão são recusadas. | RF02, RF03, RNF02 |
| CGA04 | Em nenhum cenário de teste, inclusive com compras simultâneas, a quantidade vendida ultrapassa a quantidade total. | RF10, RF11, RNF07 |
| CGA05 | Compra, reembolso, cancelamento de evento e validação atualizam a disponibilidade e a situação do ingresso de forma consistente. | RF07, RF10, RF15, RF16 |
| CGA06 | Entradas inválidas são recusadas com respostas de erro padronizadas, sem gravação de dados parciais. | RNF03, RNF05, RNF06 |
| CGA07 | Nenhuma senha ou token aparece em respostas da API, no banco em texto puro ou nos logs. | RNF01, RNF04 |
| CGA08 | O banco de dados é criado do zero apenas com as migrations Flyway, e a aplicação sobe sem ajustes manuais. | RNF13 |
| CGA09 | Os testes automatizados executam sem falhas e atingem os limites de cobertura do JaCoCo. | RNF14 |
| CGA10 | Todas as questões da seção 7 que afetam requisitos de prioridade alta foram respondidas e incorporadas ao documento. | RF12, RF16 |

## 6. Análise de inconsistências

Foram encontradas 7 inconsistências na versão original, todas resolvidas. A revisão contra o código encontrou 6 divergências novas (I08 a I13), entre o que os requisitos pedem e o que o código faz.

| ID | Inconsistência | Onde aparece | Recomendação |
| --- | --- | --- | --- |
| I01 | O UC01 tem numeração própria de RF e RN (por exemplo, o RN01 do UC01 não é o RN01 deste documento). | UC01, seções 9 e 10 | Resolvida: o UC01 passou a usar a numeração deste documento. |
| I02 | O UC01 define valores não confirmados: reserva de 15 minutos, limite de 6 ingressos por pedido, taxa de serviço de 10%, pagamento por gateway externo com Pix e ingresso nominal com CPF. | UC01, seção 0 e regras de negócio | Resolvida: reserva de 15 minutos confirmada; limite por pedido definido pelo organizador em cada tipo de ingresso; pagamento simulado; taxa da plataforma de 5% cobrada do organizador, e não do comprador; sem CPF no ingresso. |
| I03 | Não estava definido se o cliente poderia cancelar um pedido ainda não pago. | RF15, UC01 | Resolvida: o pedido pendente é cancelado por `DELETE /orders/{id}`, e o pedido pago é reembolsado (RF15). |
| I04 | O RF01 cria apenas contas de cliente, mas o RF04 não definia como um organizador obtém sua conta. | RF01, RF04 | Resolvida: o próprio usuário se promove a organizador (Q02). |
| I05 | Não havia regra para o cancelamento de um evento com ingressos já vendidos. | RF07, RN09 | Resolvida: o cancelamento reembolsa os pedidos pagos e cancela os pendentes (Q07). |
| I06 | A RN12 dependia da existência de reserva temporária. | RN12, SUG03, UC01 | Resolvida: há reserva de 15 minutos (RN12). |
| I07 | O RF16 atribui a validação ao organizador, mas em eventos reais a conferência costuma ser feita por outra pessoa na portaria. | RF16, RN23 | Resolvida: a validação é feita pelo organizador do evento ou por um administrador. |
| I08 | A RN09 recusa compras de eventos já realizados, mas o código só confere se o evento está ativo e não cancelado. | RN09, RF08, RF11 | Não implementado: conferir a data do evento na reserva de estoque. |
| I09 | A RN13 restringe a compra ao perfil de cliente, mas organizadores e administradores também compram. | RN13, RF11 | Divergência: confirmar se o comportamento é intencional e ajustar a regra ou o código. |
| I10 | O RF09 prevê desativar tipos de ingresso, mas não há endpoint para isso. | RF09 | Não implementado; listado em "Possíveis melhorias" no roadmap. |
| I11 | O RF17 prevê a lista de participantes por ingresso, mas o organizador só vê os pedidos e o painel de check-in. | RF17 | Não implementado; listado em "Possíveis melhorias" no roadmap. |
| I12 | O RF18 e a RN06 preveem a suspensão de contas pelo administrador, mas ela não existe. | RF18, RN03, RN06 | Não implementado; listado em "Possíveis melhorias" no roadmap. |
| I13 | O RF04 previa desativar organizadores. | RF04 | O administrador só bloqueia os saques; a suspensão da conta depende da I12. |

## 7. Perguntas em aberto

As perguntas Q01 a Q04 vêm do Documento de Visão; Q05 a Q08 surgiram nesta análise. Q01 a Q07 foram respondidas pelo código.

| ID | Pergunta | Resposta |
| --- | --- | --- |
| Q01 | Como o pagamento é processado? | Respondida: pagamento simulado, aprovado na hora. Pix com Asaas está planejado; cartão de crédito não está no roadmap. Afeta RF12, RN15 e RN16. |
| Q02 | O organizador cria a própria conta ou é cadastrado pelo administrador? Precisa de aprovação? | Respondida: o próprio usuário se promove a organizador, sem aprovação. Afeta RF01, RF04 e RN06. |
| Q03 | O cliente pode cancelar um ingresso já pago? Até quando, e há reembolso? | Respondida: reembolso do pedido inteiro até 48 horas antes do início do evento, sem ingresso transferido ou usado. Afeta RF15 e RN20. |
| Q04 | Como o ingresso é identificado na entrada? | Respondida: código único de 16 caracteres. Afeta RF16 e RN23. |
| Q05 | O limite de ingressos por pedido é fixo ou definido pelo organizador? | Respondida: definido pelo organizador em cada tipo de ingresso. Afeta RF11 e RN13. |
| Q06 | As unidades ficam reservadas durante o pagamento? | Respondida: sim, por 15 minutos. Afeta RF10 e RN12. |
| Q07 | O que acontece com os ingressos vendidos quando um evento é cancelado? | Respondida: os pedidos pagos são reembolsados e seus ingressos cancelados; os pedidos pendentes são cancelados. Afeta RF07 e RN09. |

**Q08 (surgida no alinhamento com o UC01):** se um pagamento for aprovado pelo provedor depois que a reserva de 15 minutos expirou, o Ticketfy deve estornar o valor ou emitir os ingressos, caso ainda haja disponibilidade? Com o pagamento simulado a situação não ocorre: a aprovação é síncrona e o pedido expirado é recusado. A pergunta volta a valer com a integração com o Asaas. Afeta RF12, RN12 e RN16.

### RF10 — Controle de disponibilidade

**Descrição:** O sistema deve manter atualizada a quantidade disponível de cada tipo de ingresso a cada reserva, expiração de reserva, cancelamento de pedido pendente e reembolso. **Atores:** Sistema. **Prioridade:** Alta. **Situação:** Implementado (reserva por `UPDATE` condicional e rotina `OrderExpirationJob`). **Critérios de aceite:**

- Ao criar um pedido de N ingressos, a disponibilidade diminui em N imediatamente, por reserva (RN12). Se a reserva expirar sem pagamento, ou o pedido pendente for cancelado, as N unidades voltam a ficar disponíveis.
- Após o reembolso de um pedido, a disponibilidade aumenta na quantidade do pedido (RN18).
- Compras simultâneas nunca resultam em disponibilidade negativa (RN12).

### RF11 — Compra de ingressos

**Descrição:** O sistema deve permitir que o cliente compre um ou mais ingressos de um ou mais tipos de um mesmo evento, gerando um pedido, com cupom de desconto opcional. **Atores:** Cliente (ver RN13). **Prioridade:** Alta. **Situação:** Implementado com divergência (`POST /orders`; ver I08 e I09). **Critérios de aceite:**

- Uma compra válida gera um pedido vinculado ao cliente, com o preço de cada ingresso registrado (RN14).
- Uma compra acima da quantidade disponível é recusada (RN13).
- Não é possível comprar ingressos de evento cancelado ou excluído (RN09).
- Uma compra acima do limite por pedido definido pelo organizador no tipo de ingresso é recusada (RN13).
- Repetir a requisição com o mesmo header `Idempotency-Key` devolve o pedido já criado, sem reservar de novo.

### RF12 — Registro de pagamento

**Descrição:** O sistema deve registrar o pagamento de cada pedido e seu resultado. Hoje o pagamento é simulado e aprovado na hora (`POST /orders/{id}/payment`); a integração com um provedor real (Pix com Asaas) está planejada (Q01). **Atores:** Cliente. **Prioridade:** Alta. **Situação:** Implementado (simulado). **Critérios de aceite:**

- Cada pagamento fica vinculado a exatamente um pedido, com o valor total do pedido (RN15).
- Um pedido expirado, cancelado, de evento cancelado ou já pago não aceita pagamento.
- Pedidos de valor zero são confirmados na criação, sem pagamento (RN16).

### RF13 — Emissão de ingresso

**Descrição:** O sistema deve emitir um ingresso para cada unidade comprada após a confirmação do pagamento. **Atores:** Sistema. **Prioridade:** Alta. **Situação:** Implementado. **Critérios de aceite:**

- Um pedido pago com N ingressos gera N ingressos válidos (RN16).
- Cada ingresso possui código único (RN17).

### RF14 — Consulta de ingressos

**Descrição:** O sistema deve permitir que o cliente liste os ingressos que possui e veja os detalhes de cada um, incluindo evento, tipo e situação. **Atores:** Cliente. **Prioridade:** Alta. **Situação:** Implementado (`GET /tickets/me`). **Critérios de aceite:**

- A listagem contém apenas ingressos de que o cliente autenticado é dono, incluindo os recebidos por transferência (RN05).
- Cada ingresso exibe sua situação atual: válido, utilizado ou cancelado.

### RF15 — Reembolso de pedido

**Descrição:** O sistema deve permitir que o cliente peça o reembolso de um pedido pago, conforme a política de cancelamento do Ticketfy (RN20). Na versão original, este requisito tratava do cancelamento de um ingresso isolado; o código reembolsa o pedido inteiro. **Atores:** Cliente. **Prioridade:** Média. **Situação:** Implementado (`POST /orders/{id}/refund`). **Critérios de aceite:**

- Os ingressos do pedido reembolsado passam à situação "cancelado" e devolvem as unidades ao estoque (RN18).
- Um pedido com ingresso já utilizado não pode ser reembolsado (RN19).
- Um pedido com ingresso transferido, ou de evento que começa em menos de 48 horas, não pode ser reembolsado (RN20).

### RF16 — Validação de ingresso

**Descrição:** O sistema deve permitir conferir um ingresso na entrada do evento, registrando seu uso. A validação é feita pelo organizador do evento ou por um administrador, a partir do código do ingresso. **Atores:** Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`POST /tickets/{code}/check-in`). **Critérios de aceite:**

- Um ingresso válido do evento é aceito e passa à situação "utilizado" (RN21).
- Um ingresso já utilizado, cancelado ou de evento cancelado é recusado (RN18, RN21, RN22).

### RF17 — Consulta de participantes

**Descrição:** O sistema deve permitir que o organizador liste os participantes de seus eventos, com a situação de cada ingresso. **Atores:** Organizador. **Prioridade:** Média. **Situação:** Não implementado. O organizador vê os pedidos do evento com o comprador (`GET /events/{id}/orders`) e os totais de check-in no painel (RF21), mas não uma lista por ingresso, com o dono atual. Listado em "Possíveis melhorias" no roadmap. **Critérios de aceite:**

- A lista contém apenas participantes de eventos do organizador autenticado (RN04).
- Ingressos cancelados aparecem identificados ou excluídos da contagem de participantes.

### RF18 — Gerenciamento de usuários

**Descrição:** O sistema deve permitir que o administrador consulte, suspenda e reative contas de usuários. **Atores:** Administrador. **Prioridade:** Média. **Situação:** Não implementado. O administrador só consulta um organizador por e-mail para bloquear ou desbloquear os saques (RF22). Listado em "Possíveis melhorias" no roadmap. **Critérios de aceite:**

- Um usuário suspenso não consegue se autenticar, e suas sessões são revogadas (RN03).
- A suspensão é lógica: os dados e o histórico de compras são preservados.
- Apenas administradores executam essas operações (RN06).

### RF19 — Cupons de desconto

**Descrição:** O sistema deve permitir que o organizador crie, edite, desative e exclua cupons de desconto de um evento, percentual ou de valor fixo, com período de validade e limite de usos opcionais, e que o cliente aplique um cupom na compra. **Atores:** Organizador, Administrador, Cliente. **Prioridade:** Média. **Situação:** Implementado (`/events/{id}/coupons` e `POST /events/{id}/coupons/preview`). **Critérios de aceite:**

- O código é único no evento, gravado em maiúsculas e não pode ser alterado.
- Um cupom inválido, expirado, inativo, esgotado ou de outro evento é recusado com a mesma resposta, e as falhas por usuário são limitadas.
- Um cupom já usado não pode ter tipo e valor alterados nem ser excluído.
- O pedido guarda subtotal, desconto e total; um pedido de valor zero é confirmado sem pagamento.

### RF20 — Transferência de ingresso

**Descrição:** O sistema deve permitir que o dono de um ingresso válido o transfira para outro usuário cadastrado, informando o e-mail do destinatário e a própria senha. **Atores:** Cliente. **Prioridade:** Média. **Situação:** Implementado (`POST /tickets/{id}/transfer`). **Critérios de aceite:**

- O ingresso recebe um código novo, e o antigo deixa de valer.
- Não é possível transferir ingresso usado, cancelado, de evento cancelado ou já iniciado, nem acima do limite de transferências por ingresso.
- Cada transferência fica registrada de forma imutável e na auditoria.

### RF21 — Painel do organizador

**Descrição:** O sistema deve mostrar ao organizador as vendas, a receita, os descontos, a taxa da plataforma, o check-in e os pedidos de cada evento, além de um resumo de todos os seus eventos. **Atores:** Organizador, Administrador. **Prioridade:** Média. **Situação:** Implementado (`GET /events/{id}/dashboard`, `GET /events/{id}/orders` e `GET /organizer/dashboard`). **Critérios de aceite:**

- O organizador vê apenas os painéis dos próprios eventos (RN04).

### RF22 — Financeiro do organizador

**Descrição:** O sistema deve calcular a taxa da plataforma de cada pedido, manter o extrato do organizador, guardar os dados de recebimento (documento e chave Pix) e permitir pedidos de saque, com análise e bloqueio pelo administrador. **Atores:** Organizador, Administrador, Sistema. **Prioridade:** Média. **Situação:** Implementado com transferência simulada (`/organizer/balance`, `/organizer/ledger`, `/organizer/payout-account`, `/organizer/payouts` e `/admin/payouts`). **Critérios de aceite:**

- O saldo é derivado de um extrato imutável, com exportação em CSV.
- Saques acima do limite de análise aguardam aprovação do administrador.
- Um organizador com saques bloqueados não consegue pedir saque.

### RF23 — Auditoria

**Descrição:** O sistema deve registrar as ações sensíveis (financeiras, administrativas, de sessão e de privacidade) com autor, alvo e data, e permitir que o administrador as consulte. **Atores:** Administrador, Sistema. **Prioridade:** Média. **Situação:** Implementado (`GET /admin/audit`). **Critérios de aceite:**

- Os registros não podem ser alterados nem apagados.

### RF24 — Sessões e senha

**Descrição:** O sistema deve permitir encerrar a sessão atual ou todas as sessões do usuário e trocar a senha, revogando as sessões abertas. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Situação:** Implementado (`POST /auth/logout`, `POST /auth/logout-all` e `PATCH /users/me/password`). **Critérios de aceite:**

- Depois do logout ou da troca de senha, os tokens anteriores deixam de valer na hora.
- Reapresentar um refresh token já usado revoga a sessão inteira.

### RF25 — Privacidade

**Descrição:** O sistema deve permitir que o usuário exporte os próprios dados e exclua a própria conta. **Atores:** Cliente, Organizador. **Prioridade:** Média. **Situação:** Implementado (`POST /users/me/data-export` e `POST /users/me/deletion`), com revisão jurídica pendente. **Critérios de aceite:**

- A exportação exige a senha e tem limite de vezes por período.
- A exclusão exige a senha e uma confirmação, é recusada para administradores e enquanto houver saldo, saque em andamento, evento ativo ou ingresso para evento futuro, e anonimiza a conta (RNF15).

### Planejado

Funcionalidades do [roadmap](ROADMAP.md) ainda não implementadas, sem requisito detalhado: idioma preferido salvo na conta, adicionar o evento à agenda com arquivo `.ics`, e-mails transacionais com Brevo, pagamento via Pix com Asaas, publicação do frontend na Cloudflare Pages, login com Google, monitoramento de erros com Sentry, upload de foto de perfil e de capa de evento e teste de carga com k6.
