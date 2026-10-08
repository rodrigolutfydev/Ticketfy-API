# Ticketfy 01 - Documento de Visão

Oct 7, 2026

## Nota sobre este documento

Este documento descreve a visão do Ticketfy. A versão original partiu das funcionalidades e tecnologias informadas pela equipe do projeto; esta revisão confere cada ponto com o código atual, que é a fonte de verdade. O que ainda não foi implementado aparece como planejado, coerente com o [roadmap](ROADMAP.md).

### Análise do contexto

Pontos que mudaram em relação à versão original:

- O pagamento é simulado: o `SimulatedPaymentGateway` aprova o pedido na hora, sem provedor externo. O pagamento via Pix com Asaas está planejado; cartão de crédito não está no roadmap.
- A reserva de 15 minutos foi mantida, mas o limite de ingressos por pedido é definido pelo organizador em cada lote (tipo de ingresso), e não no evento.
- O local não é um domínio próprio: nome do local, endereço, cidade e estado são campos do evento.

### Premissas adotadas

| ID | Premissa |
| --- | --- |
| P01 | O sistema possui três perfis de acesso: usuário (`USER`, o comprador), organizador (`ORGANIZER`) e administrador (`ADMIN`). |
| P02 | O backend é entregue como API REST. Existe um frontend React, cuja publicação na Cloudflare Pages está planejada. |
| P03 | O sistema atende eventos presenciais; o local é informado nos campos do próprio evento. |

### Questões em aberto

As questões da versão original foram respondidas pelo código.

| ID | Questão | Resposta no código |
| --- | --- | --- |
| Q01 | Como o pagamento é processado? | Pagamento simulado, aprovado na hora. Pix com Asaas está planejado; cartão de crédito não está no roadmap. |
| Q02 | Organizadores precisam ser aprovados por um administrador antes de publicar eventos? | Não. O próprio usuário passa a organizador por `POST /users/me/organizer`. O administrador pode bloquear os saques de um organizador. |
| Q03 | Qual é a política de cancelamento: o cliente pode cancelar após o pagamento, e há reembolso? | O comprador cancela um pedido ainda não pago e pede reembolso do pedido pago inteiro até 48 horas antes do início do evento (`REFUND_DEADLINE_HOURS`), desde que nenhum ingresso do pedido tenha sido transferido. O cancelamento do evento reembolsa todos os pedidos. |
| Q04 | Como o ingresso é identificado na entrada? | Por um código único de 16 caracteres, conferido no check-in pelo organizador do evento ou por um administrador. |

## 1. Introdução

### 1.1 Contextualização do problema

A realização de um evento envolve atividades que dependem umas das outras: divulgar a programação, definir os tipos de ingresso, controlar quantos ainda estão disponíveis, receber pagamentos e conferir quem pode entrar. Quando essas atividades são feitas em ferramentas diferentes, ou manualmente, a informação fica dispersa e sujeita a inconsistências.

### 1.2 Sistemas de gerenciamento de eventos

Sistemas de gerenciamento de eventos concentram essas atividades em uma única base de dados. Em geral, atendem dois lados: o de quem organiza o evento, que precisa cadastrá-lo e acompanhar as vendas, e o de quem participa, que precisa encontrar o evento, comprar o ingresso e apresentá-lo na entrada. Por lidarem com estoque limitado e com pagamentos, esses sistemas exigem controle de concorrência, integridade dos dados e regras claras de acesso.

### 1.3 Motivação

O Ticketfy foi proposto como projeto acadêmico de Engenharia de Software para aplicar, em um domínio real, conceitos de modelagem de requisitos, arquitetura de APIs REST, persistência em banco de dados relacional e segurança de aplicações. O domínio de eventos e ingressos foi escolhido por reunir regras de negócio não triviais, como controle de disponibilidade e diferentes perfis de usuário, em um escopo viável para o desenvolvimento acadêmico.

## 2. Descrição do problema

### 2.1 Problema a ser resolvido

Organizadores de eventos de pequeno e médio porte nem sempre dispõem de um meio centralizado para cadastrar eventos, vender ingressos e controlar a entrada dos participantes. Sem esse meio, o controle de disponibilidade e a conferência de ingressos dependem de processos manuais ou de ferramentas não integradas.

### 2.2 Dificuldades sem uma plataforma centralizada

- **Controle de disponibilidade:** sem um registro único do estoque, o mesmo lugar pode ser vendido mais de uma vez.
- **Rastreabilidade das vendas:** pedidos, pagamentos e ingressos ficam em registros separados, o que dificulta saber quem comprou, quanto pagou e qual ingresso recebeu.
- **Validação na entrada:** sem um identificador verificável por ingresso, a conferência depende de listas impressas e fica exposta a fraudes e duplicidades.
- **Cancelamentos:** sem regras definidas no sistema, cancelamentos são tratados caso a caso e o estoque pode não ser devolvido corretamente.
- **Controle de acesso:** sem perfis distintos, não há separação clara entre o que um cliente, um organizador e um administrador podem fazer.

## 3. Proposta da solução

### 3.1 Como o Ticketfy resolve o problema

O Ticketfy centraliza em uma API REST o ciclo de vida do evento e do ingresso: cadastro do evento, definição dos tipos de ingresso, compra, pagamento, consulta, cancelamento e validação na entrada. Todos os dados ficam em um banco relacional, o que permite manter a integridade entre evento, pedido, pagamento e ingresso. O acesso a cada operação é controlado pelo perfil do usuário autenticado.

### 3.2 Principais benefícios

- Um único registro de disponibilidade por tipo de ingresso, atualizado a cada compra e cancelamento.
- Relação rastreável entre cliente, pedido, pagamento e ingresso emitido.
- Ingressos com identificação própria, que permite validá-los na entrada do evento.
- Separação de responsabilidades entre clientes, organizadores e administradores.
- Backend independente da interface, que poderá ser consumido por um frontend web ou outro cliente.

## 4. Objetivo geral

Desenvolver o backend de uma plataforma de gerenciamento de eventos e ingressos, exposto como API REST em Java com Spring Boot e persistido em banco de dados relacional, que permita a organizadores cadastrar eventos e ingressos, a clientes comprar e consultar ingressos e a administradores gerenciar os usuários da plataforma.

## 5. Objetivos específicos

Cada objetivo tem um critério de verificação, para que possa ser avaliado ao final do projeto.

| ID | Objetivo | Critério de verificação |
| --- | --- | --- |
| OE01 | Implementar cadastro e autenticação de usuários com perfis de cliente, organizador e administrador. | Cada perfil acessa apenas os endpoints permitidos; requisições sem autenticação válida são recusadas. |
| OE02 | Permitir que organizadores cadastrem, editem, cancelem e excluam seus eventos. | Operações de criação, consulta, atualização, cancelamento com reembolso e exclusão lógica de eventos disponíveis e testadas. |
| OE03 | Permitir a criação de tipos de ingresso com preço e quantidade disponível por evento. | Um evento aceita mais de um tipo de ingresso, cada um com estoque próprio. |
| OE04 | Implementar o fluxo de compra de ingressos com registro de pedido e pagamento. | Uma compra concluída gera pedido, pagamento e ingressos vinculados ao cliente. |
| OE05 | Garantir que a quantidade vendida nunca ultrapasse a disponível. | Teste com compras simultâneas para a última unidade resulta em apenas uma venda. |
| OE06 | Permitir que o cliente consulte os ingressos que comprou. | O cliente lista apenas os próprios ingressos. |
| OE07 | Implementar o reembolso de pedidos com cancelamento dos ingressos e devolução ao estoque. | Após o reembolso, os ingressos do pedido ficam cancelados e a disponibilidade de cada tipo de ingresso aumenta na mesma quantidade. |
| OE08 | Implementar a validação de ingressos na entrada do evento. | Um ingresso válido é aceito uma única vez; ingressos cancelados ou já utilizados são recusados. |
| OE09 | Permitir que organizadores consultem os pedidos e o check-in de seus eventos. | O organizador vê os pedidos, com o comprador, e o painel de check-in apenas dos eventos que criou. A lista de participantes por ingresso não está implementada. |
| OE10 | Versionar o banco de dados por meio de migrations. | O esquema completo é criado do zero apenas com a execução das migrations. |
| OE11 | Documentar os requisitos e os casos de uso principais do sistema. | Documento de visão, requisitos e casos de uso entregues junto ao código. |

## 6. Visão geral do sistema

O Ticketfy é um backend único, exposto como API REST, que atende clientes, organizadores e administradores e mantém todos os dados em um banco relacional.

&#91;embedded content: visão geral · 3 perfis, 8 módulos funcionais, 1 banco relacional\]

O diagrama embutido acima é da versão original e mostra os módulos planejados naquela época; a lista atual de módulos está na seção 8.

Cada perfil acessa a API por requisições autenticadas, e o backend decide o que cada um pode fazer. Os módulos correspondem aos pacotes do código: `user`, `event`, `tickettype`, `order`, `payment`, `ticket`, `coupon`, `dashboard`, `payout`, `audit`, `auth` e `privacy`. O pagamento é simulado (Q01); a integração com um provedor real (Pix com Asaas) está planejada.

O ciclo principal do sistema segue esta ordem: o organizador cadastra o evento, seus tipos de ingresso e, se quiser, cupons de desconto; o comprador cria um pedido, que reserva as unidades por 15 minutos; com o pagamento aprovado, ou na hora quando o total é zero, os ingressos são emitidos e passam a constar na consulta do comprador e no painel do organizador; o valor líquido entra no extrato do organizador, que pode pedir saque; na entrada, o ingresso é validado e não pode ser usado novamente.

## 7. Público-alvo

| Perfil | Quem é | O que faz no Ticketfy |
| --- | --- | --- |
| Cliente (`USER`) | Pessoa que deseja participar de um evento. | Cadastra-se, consulta eventos, compra ingressos com ou sem cupom, consulta e transfere os próprios ingressos, pede reembolso, exporta os próprios dados e exclui a conta. |
| Organizador (`ORGANIZER`) | Pessoa ou instituição responsável por realizar eventos. | Cadastra, edita, cancela e exclui eventos, tipos de ingresso e cupons, acompanha vendas e check-in no painel, valida ingressos na entrada, consulta saldo e extrato, cadastra a chave Pix e pede saques. |
| Administrador (`ADMIN`) | Responsável pela operação da plataforma. | Analisa, aprova ou recusa saques, bloqueia os saques de organizadores, destaca eventos na vitrine, consulta a auditoria e pode agir sobre qualquer evento. A suspensão de contas não está implementada. |

A validação de ingressos na entrada é feita pelo organizador do evento ou por um administrador. Qualquer perfil autenticado pode comprar ingressos; a restrição da compra ao perfil de cliente, prevista originalmente, não está implementada.

## 8. Escopo do projeto

Fazem parte da versão atual do Ticketfy:

| Módulo | Descrição |
| --- | --- |
| Usuários e autenticação | Cadastro, foto de perfil por endereço https, login com sessões revogáveis (access token curto e refresh token rotativo em cookie), troca de senha e controle de acesso por perfil e por propriedade do recurso. |
| Organizadores | Promoção do próprio usuário a organizador, sem aprovação. |
| Eventos | Cadastro, edição, busca por nome e cidade, destaque na vitrine, cancelamento com reembolso automático e exclusão lógica. O local (nome, endereço, cidade e estado) faz parte do evento. |
| Tipos de ingresso | Lotes por evento, com preço, quantidade e limite de ingressos por pedido. A desativação de um lote não está implementada. |
| Disponibilidade | Controle da quantidade disponível de cada lote, com reserva de 15 minutos até o pagamento. |
| Pedidos e compra | Pedido com um ou mais lotes de um mesmo evento, preço congelado, chave de idempotência, cancelamento do pedido pendente e reembolso. |
| Cupons | Cupons de desconto percentual ou fixo por evento, com validade e limite de usos; pedidos de valor zero são confirmados sem pagamento. |
| Pagamentos | Pagamento simulado vinculado ao pedido (Q01). |
| Ingressos | Emissão com código único, consulta e transferência para outro usuário. |
| Validação | Check-in do ingresso na entrada do evento, protegido contra leitura dupla. |
| Painel do organizador | Vendas, receita, descontos, check-in e pedidos de cada evento. A lista de participantes por ingresso não está implementada. |
| Financeiro do organizador | Taxa da plataforma, extrato imutável com exportação CSV, dados de recebimento cifrados e saques com análise pelo administrador. |
| Auditoria | Registro imutável das ações sensíveis, consultado pelo administrador. |
| Privacidade | Exportação dos próprios dados e exclusão da conta por anonimização. |

### Planejado

Itens do [roadmap](ROADMAP.md) ainda não implementados:

- Idioma preferido salvo na conta.
- Adicionar o evento à agenda com arquivo `.ics`.
- E-mails transacionais com Brevo.
- Pagamento via Pix com Asaas.
- Publicação do frontend na Cloudflare Pages.
- Login com Google.
- Monitoramento de erros com Sentry.
- Upload de foto de perfil e de capa de evento (hoje são endereços https informados pelo usuário).
- Teste de carga com k6.

## 9. Fora do escopo

Os itens abaixo não fazem parte da versão atual nem do roadmap.

- Aplicativo móvel.
- Pagamento com cartão de crédito.
- Mapa de assentos e escolha de lugar marcado.
- Meia-entrada e promoções além dos cupons de desconto.
- Emissão de nota fiscal.
- Eventos on-line ou transmissão ao vivo.
- Integração com redes sociais para divulgação.

Transferência de ingressos, cupons de desconto, painéis de vendas e o frontend web, que estavam nesta lista na versão original, foram implementados e estão na seção 8.

## 10. Tecnologias utilizadas

A lista completa, com a situação de cada tecnologia, está na Documentação de Arquitetura.

| Tecnologia | Uso no projeto | Situação |
| --- | --- | --- |
| Java | Linguagem do backend (versão 17). | Em uso |
| Spring Boot | Framework da aplicação (versão 4.1), organizada em pacotes por domínio. | Em uso |
| API REST | Forma de exposição das funcionalidades, com contrato OpenAPI gerado no build. | Em uso |
| PostgreSQL | Banco de dados relacional (versão 16; Neon em produção). | Em uso |
| Flyway | Versionamento do esquema do banco por meio de migrations. | Em uso |
| JWT, BCrypt e Bean Validation | Access token assinado, armazenamento seguro de senhas e validação dos dados de entrada. | Em uso |
| Docker, GitHub Actions e Render | Imagem da aplicação, integração contínua com testes e cobertura, e hospedagem. | Em uso |

## 11. Benefícios esperados

**Para os organizadores**

- Cadastro de eventos e ingressos em um único sistema.
- Acompanhamento da disponibilidade de ingressos sem controle manual.
- Painel de vendas, check-in e extrato baseados nos mesmos dados da venda.

**Para os clientes**

- Compra de ingressos com confirmação registrada no sistema.
- Acesso aos próprios ingressos, transferência para outra pessoa e reembolso dentro do prazo.

**Para o projeto acadêmico**

- Aplicação prática de levantamento de requisitos, modelagem de casos de uso e arquitetura em camadas.
- Exercício de problemas reais de backend: controle de concorrência, integridade transacional e controle de acesso por perfil.
- Base técnica documentada que pode ser evoluída com uma interface gráfica e novas funcionalidades.

## 12. Considerações finais

O Ticketfy propõe centralizar o gerenciamento de eventos e ingressos em uma API REST, com dados mantidos em banco relacional e acesso controlado por perfil. O escopo desta versão se concentra no backend e no ciclo completo do ingresso, da criação do evento à validação na entrada.

As questões Q01 a Q04 foram respondidas pelo código, e as respostas estão neste documento e no caso de uso de compra. As próximas evoluções seguem o [roadmap](ROADMAP.md), começando pela integração com um provedor de pagamento real.
