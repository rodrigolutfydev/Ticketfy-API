# Ticketfy 02 - Documento de Requisitos

Oct 7, 2026 · @Rodrigo

## Nota sobre este documento

Os requisitos abaixo derivam das funcionalidades informadas para o Ticketfy e da estrutura atual do backend (Java, Spring Boot, API REST, PostgreSQL e Flyway, com pacotes por domínio: usuário, local, evento, tipo de ingresso, pedido, pagamento e ingresso). Pontos ainda não decididos aparecem como "a definir" e estão ligados às perguntas da seção 7.

**Prioridades**

| Prioridade | Significado |
| --- | --- |
| Alta | Necessário para o fluxo principal funcionar: sem ele, não há evento, compra ou ingresso. |
| Média | Necessário para a versão completa, mas o fluxo principal funciona sem ele. |
| Baixa | Desejável; pode ficar para uma versão posterior. |

**Premissas herdadas do Documento de Visão**

- P01: o sistema possui três perfis de acesso: cliente, organizador e administrador.
- P02: a primeira versão é entregue como API REST, sem interface gráfica.
- P03: o sistema atende eventos presenciais, vinculados a um local.

## 1. Requisitos Funcionais

São 18 requisitos funcionais: 13 de prioridade alta e 5 de prioridade média. Requisitos que dependem de uma decisão ainda em aberto indicam a pergunta correspondente.

### RF01 — Cadastro de usuário

**Descrição:** O sistema deve permitir que uma pessoa crie uma conta informando nome, e-mail e senha. **Atores:** Cliente. **Prioridade:** Alta. **Critérios de aceite:**

- Um cadastro com dados válidos cria a conta com o perfil de cliente.
- Um e-mail já cadastrado é recusado (RN01).
- Campos obrigatórios ausentes ou inválidos retornam erro de validação indicando o campo.
- A senha nunca é retornada nas respostas da API.

### RF02 — Autenticação

**Descrição:** O sistema deve autenticar o usuário por e-mail e senha e fornecer um token de acesso para as requisições seguintes. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Critérios de aceite:**

- Credenciais corretas retornam um token de acesso.
- Credenciais incorretas são recusadas sem indicar qual campo está errado.
- Usuários inativos não conseguem se autenticar (RN03).
- Requisições a endpoints protegidos sem token válido são recusadas.

### RF03 — Controle de acesso por perfil

**Descrição:** O sistema deve restringir cada operação ao perfil autorizado a executá-la. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Critérios de aceite:**

- Um perfil sem permissão recebe resposta de acesso negado (RN04 a RN06).
- Um organizador não altera eventos de outro organizador (RN04).
- Um cliente não acessa pedidos ou ingressos de outro cliente (RN05).

### RF04 — Gerenciamento de organizadores

**Descrição:** O sistema deve permitir o cadastro, a consulta, a edição e a desativação de organizadores. A forma de cadastro, pelo próprio organizador ou pelo administrador, está a definir (Q02). **Atores:** Administrador, Organizador. **Prioridade:** Média. **Critérios de aceite:**

- Um organizador cadastrado consegue se autenticar e criar eventos.
- Um organizador desativado não consegue criar nem editar eventos.

### RF05 — Gerenciamento de locais

**Descrição:** O sistema deve permitir cadastrar, consultar e editar os locais onde os eventos acontecem. Requisito confirmado pela existência do domínio de local no backend. **Atores:** Organizador, Administrador. **Prioridade:** Média. **Critérios de aceite:**

- Um local cadastrado pode ser associado a um evento.
- Campos obrigatórios ausentes retornam erro de validação.

### RF06 — Cadastro de evento

**Descrição:** O sistema deve permitir que o organizador cadastre um evento com nome, descrição, data e hora de início e término, local e limite de ingressos por pedido. **Atores:** Organizador. **Prioridade:** Alta. **Critérios de aceite:**

- O evento criado fica vinculado ao organizador autenticado e a um local (RN08).
- Um evento com data de início no passado ou término anterior ao início é recusado (RN07).

### RF07 — Edição e encerramento de evento

**Descrição:** O sistema deve permitir que o organizador edite os dados do seu evento e o encerre para novas vendas. **Atores:** Organizador. **Prioridade:** Alta. **Critérios de aceite:**

- Apenas o organizador responsável edita ou encerra o evento (RN04).
- Um evento encerrado não aceita novas compras (RN09).
- O tratamento dos ingressos já vendidos quando um evento é cancelado está a definir (Q07).

### RF08 — Consulta de eventos

**Descrição:** O sistema deve listar os eventos disponíveis e exibir os detalhes de cada um, incluindo local e tipos de ingresso. **Atores:** Cliente, Organizador, Administrador. **Prioridade:** Alta. **Critérios de aceite:**

- A listagem exibe apenas eventos abertos para venda ao cliente.
- O detalhe do evento informa os tipos de ingresso com preço e disponibilidade.

### RF09 — Gerenciamento de tipos de ingresso

**Descrição:** O sistema deve permitir que o organizador crie, edite e desative tipos de ingresso de um evento, cada um com nome, preço e quantidade total. **Atores:** Organizador. **Prioridade:** Alta. **Critérios de aceite:**

- Um evento aceita mais de um tipo de ingresso, cada um com estoque próprio.
- Preço negativo ou quantidade menor que 1 são recusados (RN10).
- A quantidade total não pode ser reduzida abaixo do número de ingressos já vendidos (RN11).

### Sugestões — não confirmadas no projeto

Os itens abaixo não fazem parte dos requisitos oficiais. Ficam registrados para avaliação da equipe.

| ID | Sugestão | Motivo |
| --- | --- | --- |
| SUG01 | Recuperação de senha por e-mail. | Sugestão — não confirmado no projeto. Comum em sistemas com autenticação por senha. |
| SUG02 | Envio de e-mail de confirmação após a compra. | Sugestão — não confirmado no projeto. Notificações não constam na lista de funcionalidades. |
| SUG03 | Reserva temporária de ingressos durante o pagamento. | Confirmada (Q06 respondida): incorporada ao RF10 e à RN12, com prazo de 15 minutos. |
| SUG04 | Relatório de vendas por evento para o organizador. | Sugestão — não confirmado no projeto. Relatórios estão fora do escopo do Documento de Visão. |

## 2. Requisitos Não Funcionais

Os requisitos foram dimensionados para um projeto acadêmico executado em ambiente de desenvolvimento e demonstração. Metas numéricas marcadas como sugestão podem ser ajustadas pela equipe.

| ID | Categoria | Requisito | Verificação |
| --- | --- | --- | --- |
| RNF01 | Segurança | As senhas devem ser armazenadas com hash BCrypt, nunca em texto puro. | Inspeção do banco: nenhuma senha legível. |
| RNF02 | Segurança | O acesso aos endpoints protegidos deve exigir token JWT válido. | Requisição sem token ou com token expirado é recusada. |
| RNF03 | Segurança | Os dados de entrada devem ser validados no backend com Bean Validation antes de qualquer processamento. | Payload inválido retorna erro de validação sem gravar dados. |
| RNF04 | Proteção de dados | O sistema deve coletar apenas os dados pessoais necessários às funcionalidades e não deve expor senhas ou tokens em respostas e logs, em conformidade com os princípios da LGPD. Dados de cartão não são recebidos nem armazenados pelo Ticketfy: são informados diretamente ao provedor de pagamento. | Revisão das respostas da API e dos logs. |
| RNF05 | Confiabilidade | Compra, emissão e cancelamento de ingressos devem ocorrer em transações: ou todas as alterações são gravadas, ou nenhuma. | Teste com falha simulada no meio da operação não deixa dados parciais. |
| RNF06 | Confiabilidade | Os erros devem ser tratados de forma centralizada (`@RestControllerAdvice`), com respostas padronizadas e códigos HTTP adequados. | Erros de validação, autorização e recurso inexistente retornam formato único. |
| RNF07 | Escalabilidade | A disponibilidade de ingressos deve permanecer correta sob requisições simultâneas de compra. | Teste com requisições concorrentes para a última unidade resulta em uma única venda. |
| RNF08 | Escalabilidade | A API deve ser stateless, sem sessão no servidor, permitindo mais de uma instância da aplicação. | Autenticação funciona apenas com o token, sem sessão HTTP. |
| RNF09 | Performance | Consultas de eventos e de ingressos devem responder em até 1 segundo em ambiente local com massa de teste de até 1.000 eventos. Sugestão — não confirmado no projeto. | Medição com ferramenta de teste de API. |
| RNF10 | Disponibilidade | Uma falha em uma requisição não deve interromper a aplicação; erros inesperados retornam resposta de erro e a API continua atendendo. | Requisição que gera exceção não derruba o serviço. |
| RNF11 | Usabilidade | A API deve seguir convenções REST: recursos nomeados por substantivos, métodos HTTP adequados e mensagens de erro compreensíveis para quem consome a API. | Revisão dos endpoints e das mensagens. |
| RNF12 | Manutenibilidade | O código deve ser organizado em pacotes por domínio e escrito em inglês (classes, pacotes e variáveis). | Revisão da estrutura do projeto. |
| RNF13 | Manutenibilidade | Toda alteração no esquema do banco deve ser feita por migration Flyway versionada. | O banco é recriado do zero apenas com as migrations. |
| RNF14 | Manutenibilidade | As regras de negócio de compra, disponibilidade, cancelamento e validação devem ter testes automatizados. Meta sugerida: 70% de cobertura nas classes de serviço. Sugestão — não confirmado no projeto. | Relatório de cobertura de testes. |
| RNF15 | Proteção de dados | A remoção de usuários deve ser lógica, preservando o histórico de pedidos e ingressos. | Usuário desativado continua presente no banco, marcado como inativo. |

## 3. Regras de Negócio

São 23 regras, agrupadas por tema. Regras que dependem de decisão em aberto estão marcadas como "a definir" e não devem ser implementadas antes da resposta.

| ID | Tema | Regra |
| --- | --- | --- |
| RN01 | Usuários | O e-mail é único no sistema: não pode haver duas contas com o mesmo e-mail. |
| RN02 | Usuários | Cada usuário possui um único perfil: cliente, organizador ou administrador. |
| RN03 | Usuários | Apenas usuários ativos podem se autenticar. A desativação não apaga os dados do usuário. |
| RN04 | Permissões | O organizador só pode gerenciar, consultar participantes e validar ingressos dos eventos que criou. |
| RN05 | Permissões | O cliente só pode consultar e cancelar os próprios pedidos e ingressos. |
| RN06 | Permissões | Apenas o administrador pode ativar e desativar usuários e organizadores. |
| RN07 | Eventos | A data de início do evento deve ser futura no momento do cadastro, e a data de término deve ser posterior à de início. |
| RN08 | Eventos | Todo evento pertence a um único organizador e está associado a um local. |
| RN09 | Eventos | Eventos encerrados ou já realizados não aceitam novas compras. |
| RN10 | Ingressos | Todo tipo de ingresso pertence a um evento, com preço maior ou igual a zero e quantidade total maior que zero. |
| RN11 | Ingressos | A quantidade total de um tipo de ingresso não pode ser reduzida abaixo da quantidade já vendida. |
| RN12 | Ingressos | Disponibilidade = quantidade total − quantidade vendida − quantidade reservada, e nunca pode ser negativa. Ao criar o pedido, as unidades ficam reservadas por 15 minutos; sem pagamento aprovado nesse prazo, o pedido expira e as unidades voltam à disponibilidade. |
| RN13 | Compras | Apenas clientes podem comprar ingressos, e a quantidade comprada de cada tipo não pode exceder a disponibilidade. Cada pedido respeita o limite de ingressos por pedido definido pelo organizador no cadastro do evento. |
| RN14 | Compras | O preço de cada ingresso é registrado no pedido no momento da compra. Alterações posteriores de preço não afetam pedidos existentes. |
| RN15 | Pagamentos | Cada pagamento está vinculado a um único pedido, e seu valor corresponde ao total do pedido. |
| RN16 | Pagamentos | Ingressos só são emitidos após a confirmação do pagamento. Pagamentos recusados não geram ingressos. |
| RN17 | Ingressos | Cada ingresso emitido possui um identificador único. |
| RN18 | Cancelamentos | Um ingresso cancelado devolve sua unidade à disponibilidade e não pode mais ser validado. |
| RN19 | Cancelamentos | Ingressos já utilizados não podem ser cancelados. |
| RN20 | Cancelamentos | O prazo para cancelamento e a existência de reembolso estão a definir (Q03). |
| RN21 | Validação | Um ingresso só pode ser validado uma vez; após a validação, passa à situação "utilizado". |
| RN22 | Validação | Um ingresso só é aceito na validação do evento ao qual pertence. |
| RN23 | Validação | A validação na entrada (check-in) é feita pelo organizador do evento, apenas nos eventos que criou (RN04). |

## 4. Matriz de rastreabilidade

Apenas o UC01 — Realizar compra de ingresso está documentado. Os casos UC02 a UC05 são citados no UC01 como inclusões, mas ainda não têm especificação própria. Onde não há caso de uso, a matriz indica "Não documentado".

| Requisito | Caso de uso | Situação do caso de uso | Regras de negócio |
| --- | --- | --- | --- |
| RF01 Cadastro de usuário | — | Não documentado | RN01, RN02 |
| RF02 Autenticação | UC02 Autenticar usuário | Citado no UC01, sem especificação | RN03 |
| RF03 Controle de acesso por perfil | UC02 Autenticar usuário (parcial) | Citado no UC01, sem especificação | RN04, RN05, RN06 |
| RF04 Gerenciamento de organizadores | — | Não documentado | RN02, RN06 |
| RF05 Gerenciamento de locais | — | Não documentado | Nenhuma regra específica definida; informação insuficiente |
| RF06 Cadastro de evento | — | Não documentado | RN07, RN08 |
| RF07 Edição e encerramento de evento | — | Não documentado | RN04, RN09 |
| RF08 Consulta de eventos | — | Não documentado | RN09, RN12 |
| RF09 Gerenciamento de tipos de ingresso | — | Não documentado | RN10, RN11 |
| RF10 Controle de disponibilidade | UC01; UC03 Reservar ingressos | UC01 documentado; UC03 citado | RN12 |
| RF11 Compra de ingressos | UC01 Realizar compra de ingresso | Documentado | RN09, RN13, RN14 |
| RF12 Registro de pagamento | UC01; UC04 Processar pagamento | UC01 documentado; UC04 citado | RN15, RN16 |
| RF13 Emissão de ingresso | UC01; UC05 Emitir ingressos | UC01 documentado; UC05 citado | RN16, RN17 |
| RF14 Consulta de ingressos | — | Não documentado | RN05 |
| RF15 Cancelamento de ingresso | — | Não documentado (depende de Q03) | RN05, RN18, RN19, RN20 |
| RF16 Validação de ingresso | — | Não documentado | RN04, RN18, RN21, RN22, RN23 |
| RF17 Consulta de participantes | — | Não documentado | RN04 |
| RF18 Gerenciamento de usuários | — | Não documentado | RN03, RN06 |

Todas as 23 regras estão ligadas a pelo menos um requisito. O RF05 é o único requisito sem regra de negócio associada.

## 5. Critérios gerais de aceite

O sistema é considerado aceito quando todos os critérios abaixo forem atendidos em testes automatizados ou em demonstração registrada.

| ID | Critério | Requisitos cobertos |
| --- | --- | --- |
| CGA01 | Todos os requisitos funcionais de prioridade alta estão implementados e atendem aos seus critérios de aceite. | RF01–RF03, RF06–RF14, RF16 |
| CGA02 | O fluxo completo funciona de ponta a ponta: organizador cadastra evento e tipos de ingresso; cliente compra e paga; ingresso é emitido, consultado e validado uma única vez. | RF06, RF09, RF11–RF14, RF16 |
| CGA03 | Cada perfil acessa apenas as operações permitidas, e as tentativas fora da permissão são recusadas. | RF02, RF03, RNF02 |
| CGA04 | Em nenhum cenário de teste, inclusive com compras simultâneas, a quantidade vendida ultrapassa a quantidade total. | RF10, RF11, RNF07 |
| CGA05 | Compra, cancelamento e validação atualizam a disponibilidade e a situação do ingresso de forma consistente. | RF10, RF15, RF16 |
| CGA06 | Entradas inválidas são recusadas com respostas de erro padronizadas, sem gravação de dados parciais. | RNF03, RNF05, RNF06 |
| CGA07 | Nenhuma senha ou token aparece em respostas da API, no banco em texto puro ou nos logs. | RNF01, RNF04 |
| CGA08 | O banco de dados é criado do zero apenas com as migrations Flyway, e a aplicação sobe sem ajustes manuais. | RNF13 |
| CGA09 | Os testes automatizados das regras de compra, disponibilidade, cancelamento e validação executam sem falhas. | RNF14 |
| CGA10 | Todas as questões da seção 7 que afetam requisitos de prioridade alta foram respondidas e incorporadas ao documento. | RF12, RF16 |

## 6. Análise de inconsistências

Foram encontradas 7 inconsistências; I01, I02, I06 e I07 já foram resolvidas. As pendentes (I03, I04 e I05) dependem das respostas a Q03, Q02 e Q07.

| ID | Inconsistência | Onde aparece | Recomendação |
| --- | --- | --- | --- |
| I01 | O UC01 tem numeração própria de RF e RN (por exemplo, o RN01 do UC01 não é o RN01 deste documento). | UC01, seções 9 e 10 | Resolvida: o UC01 passou a usar a numeração deste documento. |
| I02 | O UC01 define valores não confirmados: reserva de 15 minutos, limite de 6 ingressos por pedido, taxa de serviço de 10%, pagamento por gateway externo com Pix e ingresso nominal com CPF. Aqui esses pontos estão como "a definir". | UC01, seção 0 e regras de negócio | Resolvida: reserva de 15 minutos, cartão e Pix e provedor externo confirmados; limite por pedido definido pelo organizador; taxa de serviço e CPF removidos do UC01. |
| I03 | O RF15 trata o cancelamento de ingresso já pago, mas não está definido se o cliente também poderá cancelar um pedido ainda não pago, antes de a reserva expirar. | RF15, UC01 | Definir se haverá os dois tipos de cancelamento (Q03). |
| I04 | O RF01 cria apenas contas de cliente, mas o RF04 não define como um organizador obtém sua conta. | RF01, RF04 | Definir o fluxo de cadastro de organizadores (Q02). |
| I05 | O RF07 permite encerrar eventos, mas não há regra para o cancelamento de um evento com ingressos já vendidos. | RF07, RN09 | Definir o que acontece com os ingressos nesse caso (Q07). |
| I06 | A RN12 depende da existência de reserva temporária, que o UC01 assume e este documento trata como sugestão (SUG03). | RN12, SUG03, UC01 | Resolvida: haverá reserva de 15 minutos (RN12). |
| I07 | O RF16 atribui a validação ao organizador, mas em eventos reais a conferência costuma ser feita por outra pessoa na portaria. | RF16, RN23 | Resolvida: a validação é feita pelo organizador do evento. |

## 7. Perguntas em aberto

As perguntas Q01 a Q04 vêm do Documento de Visão; Q05 a Q07 surgiram nesta análise. Q01 e Q04 foram respondidas em parte; o que falta nelas ainda afeta requisitos de prioridade alta (RF12 e RF16).

| ID | Pergunta | Afeta |
| --- | --- | --- |
| Q01 | Respondida em parte: pagamento real, com cartão de crédito e Pix. Falta escolher o provedor. | RF12, RN15, RN16 |
| Q02 | O organizador cria a própria conta ou é cadastrado pelo administrador? Precisa de aprovação? | RF01, RF04, RN06 |
| Q03 | O cliente pode cancelar um ingresso já pago? Até quando, e há reembolso? | RF15, RN20 |
| Q04 | Respondida em parte: a validação é feita pelo organizador do evento. Falta definir como o ingresso será identificado na entrada (código, QR Code ou outro). | RF16, RN23 |
| Q05 | Respondida: o limite de ingressos por pedido é definido pelo organizador em cada evento. | RF11, RN13 |
| Q06 | Respondida: sim, as unidades ficam reservadas por 15 minutos. | RF10, RN12 |
| Q07 | O que acontece com os ingressos vendidos quando um evento é cancelado? | RF07, RN09 |

**Q08 (surgida no alinhamento com o UC01):** se um pagamento for aprovado pelo provedor depois que a reserva de 15 minutos expirou, o Ticketfy deve estornar o valor ou emitir os ingressos, caso ainda haja disponibilidade? Afeta RF12, RN12 e RN16.

### RF10 — Controle de disponibilidade

**Descrição:** O sistema deve manter atualizada a quantidade disponível de cada tipo de ingresso a cada reserva, venda, expiração de reserva e cancelamento. **Atores:** Sistema. **Prioridade:** Alta. **Critérios de aceite:**

- Ao criar um pedido de N ingressos, a disponibilidade diminui em N imediatamente, por reserva (RN12). Se a reserva expirar sem pagamento, as N unidades voltam a ficar disponíveis.
- Após o cancelamento de um ingresso, a disponibilidade aumenta em 1 (RN18).
- Compras simultâneas nunca resultam em disponibilidade negativa (RN12).

### RF11 — Compra de ingressos

**Descrição:** O sistema deve permitir que o cliente compre um ou mais ingressos de um ou mais tipos de um mesmo evento, gerando um pedido. **Atores:** Cliente. **Prioridade:** Alta. **Critérios de aceite:**

- Uma compra válida gera um pedido vinculado ao cliente, com o preço de cada ingresso registrado (RN14).
- Uma compra acima da quantidade disponível é recusada (RN13).
- Não é possível comprar ingressos de evento encerrado (RN09).
- Uma compra acima do limite de ingressos por pedido definido pelo organizador do evento é recusada (RN13).

### RF12 — Registro de pagamento

**Descrição:** O sistema deve registrar o pagamento de cada pedido e seu resultado (aprovado ou recusado). O pagamento é processado por um provedor de pagamento real. Os meios aceitos são cartão de crédito e Pix; o provedor ainda será escolhido (Q01). **Atores:** Cliente, Provedor de pagamento. **Prioridade:** Alta. **Critérios de aceite:**

- Cada pagamento fica vinculado a exatamente um pedido, com o valor total do pedido (RN15).
- Um pagamento recusado não gera ingressos (RN16).

### RF13 — Emissão de ingresso

**Descrição:** O sistema deve emitir um ingresso para cada unidade comprada após a confirmação do pagamento. **Atores:** Sistema. **Prioridade:** Alta. **Critérios de aceite:**

- Um pedido pago com N ingressos gera N ingressos válidos (RN16).
- Cada ingresso possui identificador único (RN17).

### RF14 — Consulta de ingressos

**Descrição:** O sistema deve permitir que o cliente liste os ingressos que comprou e veja os detalhes de cada um, incluindo evento, tipo e situação. **Atores:** Cliente. **Prioridade:** Alta. **Critérios de aceite:**

- A listagem contém apenas ingressos do cliente autenticado (RN05).
- Cada ingresso exibe sua situação atual: válido, utilizado ou cancelado.

### RF15 — Cancelamento de ingresso

**Descrição:** O sistema deve permitir que o cliente cancele um ingresso conforme a política de cancelamento do Ticketfy. Prazo e reembolso estão a definir (Q03). **Atores:** Cliente. **Prioridade:** Média. **Critérios de aceite:**

- Um ingresso cancelado passa à situação "cancelado" e devolve a unidade ao estoque (RN18).
- Um ingresso já utilizado não pode ser cancelado (RN19).

### RF16 — Validação de ingresso

**Descrição:** O sistema deve permitir conferir um ingresso na entrada do evento, registrando seu uso. A validação é feita pelo organizador do evento. O meio de identificação do ingresso (código, QR Code ou outro) está a definir (Q04). **Atores:** Organizador. **Prioridade:** Alta. **Critérios de aceite:**

- Um ingresso válido do evento é aceito e passa à situação "utilizado" (RN21).
- Um ingresso já utilizado, cancelado ou de outro evento é recusado (RN18, RN21, RN22).

### RF17 — Consulta de participantes

**Descrição:** O sistema deve permitir que o organizador liste os participantes de seus eventos, com a situação de cada ingresso. **Atores:** Organizador. **Prioridade:** Média. **Critérios de aceite:**

- A lista contém apenas participantes de eventos do organizador autenticado (RN04).
- Ingressos cancelados aparecem identificados ou excluídos da contagem de participantes.

### RF18 — Gerenciamento de usuários

**Descrição:** O sistema deve permitir que o administrador consulte, ative e desative contas de usuários. **Atores:** Administrador. **Prioridade:** Média. **Critérios de aceite:**

- Um usuário desativado não consegue se autenticar (RN03).
- A desativação é lógica: os dados e o histórico de compras são preservados.
- Apenas administradores executam essas operações (RN06).
