# Ticketfy 01 - Documento de Visão

Oct 7, 2026 · @Rodrigo

## Nota sobre este documento

Este documento descreve a visão do Ticketfy com base nas funcionalidades e tecnologias informadas pela equipe do projeto. Pontos ainda não definidos aparecem como premissas (P) ou questões em aberto (Q), e não como fatos.

### Análise do contexto

Não foram encontradas informações conflitantes que impeçam a redação. Foram identificados dois pontos de atenção:

- "Compra de ingressos" e "Pagamentos" aparecem como funcionalidades separadas, mas a forma de pagamento não foi definida. Este documento trata o pagamento como parte do fluxo de compra, processado por um provedor real ainda não escolhido, com cartão de crédito e Pix.
- O documento de caso de uso UC01 foi alinhado às decisões da equipe: provedor de pagamento real, cartão e Pix, reserva de ingressos por 15 minutos e limite de ingressos por pedido definido pelo organizador.

### Premissas adotadas

| ID | Premissa |
| --- | --- |
| P01 | O sistema possui três perfis de acesso: cliente, organizador e administrador. |
| P02 | A primeira versão é entregue como API REST; a interface gráfica será desenvolvida em etapa posterior. |
| P03 | O sistema atende eventos presenciais, já que o projeto possui um domínio de local (venue) associado ao evento. |

### Questões em aberto

| ID | Questão | Impacto |
| --- | --- | --- |
| Q01 | Respondida em parte: pagamento real, com cartão de crédito e Pix. Falta escolher o provedor. | Define o escopo do módulo de pagamentos e os testes de integração. |
| Q02 | Organizadores precisam ser aprovados por um administrador antes de publicar eventos? | Define o fluxo de gerenciamento de organizadores. |
| Q03 | Qual é a política de cancelamento: o cliente pode cancelar após o pagamento, e há reembolso? | Define as regras do módulo de cancelamento. |
| Q04 | Respondida em parte: a validação na entrada é feita pelo organizador do evento. Falta definir o meio de identificação do ingresso (código, QR Code ou outro). | Define o módulo de validação e os dados do ingresso. |

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
| OE02 | Permitir que organizadores cadastrem, editem e encerrem seus eventos. | Operações de criação, consulta, atualização e encerramento de eventos disponíveis e testadas. |
| OE03 | Permitir a criação de tipos de ingresso com preço e quantidade disponível por evento. | Um evento aceita mais de um tipo de ingresso, cada um com estoque próprio. |
| OE04 | Implementar o fluxo de compra de ingressos com registro de pedido e pagamento. | Uma compra concluída gera pedido, pagamento e ingressos vinculados ao cliente. |
| OE05 | Garantir que a quantidade vendida nunca ultrapasse a disponível. | Teste com compras simultâneas para a última unidade resulta em apenas uma venda. |
| OE06 | Permitir que o cliente consulte os ingressos que comprou. | O cliente lista apenas os próprios ingressos. |
| OE07 | Implementar o cancelamento de ingressos com devolução ao estoque. | Após o cancelamento, a disponibilidade do tipo de ingresso aumenta na mesma quantidade. |
| OE08 | Implementar a validação de ingressos na entrada do evento. | Um ingresso válido é aceito uma única vez; ingressos cancelados ou já utilizados são recusados. |
| OE09 | Permitir que organizadores consultem os participantes de seus eventos. | O organizador lista os participantes apenas dos eventos que criou. |
| OE10 | Versionar o banco de dados por meio de migrations. | O esquema completo é criado do zero apenas com a execução das migrations. |
| OE11 | Documentar os requisitos e os casos de uso principais do sistema. | Documento de visão, requisitos e casos de uso entregues junto ao código. |

## 6. Visão geral do sistema

O Ticketfy é um backend único, exposto como API REST, que atende clientes, organizadores e administradores e mantém todos os dados em um banco relacional.

&#91;embedded content: visão geral · 3 perfis, 8 módulos funcionais, 1 banco relacional\]

Cada perfil acessa a API por requisições autenticadas, e o backend decide o que cada um pode fazer. Os módulos correspondem aos domínios do código (usuário, local, evento, tipo de ingresso, pedido, pagamento e ingresso), mais a validação na entrada. A consulta de participantes é feita a partir dos ingressos de cada evento. O pagamento será processado por um provedor real, que não aparece no diagrama porque ainda não foi escolhido (Q01).

O ciclo principal do sistema segue esta ordem: o organizador cadastra o evento e seus tipos de ingresso; o cliente realiza a compra, que gera um pedido e um pagamento; após a confirmação, o ingresso é emitido e passa a constar na consulta do cliente e na lista de participantes; na entrada, o ingresso é validado e não pode ser usado novamente.

## 7. Público-alvo

| Perfil | Quem é | O que faz no Ticketfy |
| --- | --- | --- |
| Cliente | Pessoa que deseja participar de um evento. | Cadastra-se, consulta eventos, compra ingressos, consulta e cancela os próprios ingressos. |
| Organizador | Pessoa ou instituição responsável por realizar eventos. | Cadastra e gerencia eventos e tipos de ingresso, acompanha a disponibilidade, consulta participantes e valida ingressos na entrada. |
| Administrador | Responsável pela operação da plataforma. | Gerencia usuários e organizadores e tem acesso de consulta aos dados do sistema. |

A validação de ingressos na entrada é responsabilidade do organizador do evento.

## 8. Escopo do projeto

Fazem parte da versão atual do Ticketfy:

| Módulo | Descrição |
| --- | --- |
| Usuários e autenticação | Cadastro, login e controle de acesso por perfil. |
| Organizadores | Cadastro e gerenciamento de organizadores. |
| Locais | Cadastro dos locais onde os eventos acontecem. |
| Eventos | Cadastro, edição, consulta e encerramento de eventos, incluindo o limite de ingressos por pedido de cada evento. |
| Tipos de ingresso | Definição de categorias de ingresso por evento, com preço e quantidade. |
| Disponibilidade | Controle da quantidade disponível de cada tipo de ingresso, com reserva de 15 minutos durante o pagamento. |
| Pedidos e compra | Registro da compra de um ou mais ingressos pelo cliente. |
| Pagamentos | Registro do pagamento vinculado ao pedido, processado por um provedor de pagamento real, com cartão de crédito e Pix (provedor a escolher, Q01). |
| Ingressos | Emissão, consulta e cancelamento de ingressos. |
| Participantes | Consulta dos participantes de cada evento pelo organizador. |
| Validação | Conferência do ingresso na entrada do evento. |

## 9. Fora do escopo

Os itens abaixo não fazem parte da versão atual. Alguns podem ser avaliados em versões futuras.

- Interface gráfica (frontend web ou aplicativo móvel), prevista para etapa posterior.
- Revenda ou transferência de ingressos entre clientes.
- Mapa de assentos e escolha de lugar marcado.
- Meia-entrada, cupons de desconto e promoções.
- Emissão de nota fiscal.
- Eventos on-line ou transmissão ao vivo.
- Integração com redes sociais para divulgação.
- Relatórios financeiros e dashboards analíticos.

Como nenhum desses itens foi informado entre as funcionalidades do sistema, eles foram listados aqui para delimitar o escopo de forma explícita.

## 10. Tecnologias utilizadas

Foram listadas apenas as tecnologias confirmadas pelo projeto. A lista completa, com a situação de cada uma, está na Documentação de Arquitetura.

| Tecnologia | Uso no projeto | Situação |
| --- | --- | --- |
| Java | Linguagem do backend (versão 17). | Confirmada |
| Spring Boot | Framework da aplicação (versão 4), organizada em pacotes por domínio (usuário, local, evento, tipo de ingresso, pedido, pagamento e ingresso). | Confirmada |
| API REST | Forma de exposição das funcionalidades para os clientes da API. | Confirmada |
| PostgreSQL | Banco de dados relacional. | Confirmada |
| Flyway | Versionamento do esquema do banco por meio de migrations. | Confirmada |
| JWT, BCrypt e Bean Validation | Autenticação por token, armazenamento seguro de senhas e validação dos dados de entrada. | BCrypt e Bean Validation em uso; autenticação por JWT em andamento |

## 11. Benefícios esperados

**Para os organizadores**

- Cadastro de eventos e ingressos em um único sistema.
- Acompanhamento da disponibilidade de ingressos sem controle manual.
- Lista de participantes e validação de ingressos baseadas nos mesmos dados da venda.

**Para os clientes**

- Compra de ingressos com confirmação registrada no sistema.
- Acesso aos próprios ingressos e possibilidade de cancelamento conforme as regras definidas.

**Para o projeto acadêmico**

- Aplicação prática de levantamento de requisitos, modelagem de casos de uso e arquitetura em camadas.
- Exercício de problemas reais de backend: controle de concorrência, integridade transacional e controle de acesso por perfil.
- Base técnica documentada que pode ser evoluída com uma interface gráfica e novas funcionalidades.

## 12. Considerações finais

O Ticketfy propõe centralizar o gerenciamento de eventos e ingressos em uma API REST, com dados mantidos em banco relacional e acesso controlado por perfil. O escopo desta versão se concentra no backend e no ciclo completo do ingresso, da criação do evento à validação na entrada.

As questões Q01 a Q04 devem ser resolvidas antes da especificação detalhada dos módulos de pagamento, cancelamento, organizadores e validação. As respostas serão incorporadas a este documento e aos casos de uso correspondentes, mantendo a documentação coerente com as decisões do projeto.
