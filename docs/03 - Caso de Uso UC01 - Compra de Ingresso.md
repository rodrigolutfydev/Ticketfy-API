# Ticketfy — Caso de Uso

Oct 7, 2026 · @Rodrigo

## 0. Análise do contexto e premissas

O caso de uso escolhido é **Realizar compra de ingresso**, porque é o fluxo que gera receita e envolve quase todos os domínios do sistema: usuário, evento, tipo de ingresso, pedido, pagamento e ingresso. Esta versão foi alinhada ao Documento de Requisitos, ao Diagrama de Classes de Domínio e à Documentação de Arquitetura. As referências RF, RN e RNF seguem a numeração do Documento de Requisitos.

### Premissas e decisões

| ID | Premissa ou decisão | Situação |
| --- | --- | --- |
| P01 | Backend em Java 17 e Spring Boot 4, com PostgreSQL e migrations Flyway. | Confirmada |
| P02 | Autenticação stateless por token JWT; senhas com BCrypt. | BCrypt em uso; JWT em andamento |
| P03 | Perfis de cliente, organizador e administrador; apenas o cliente compra ingressos (RN13). | Definido nos requisitos; o enum `Role` do código ainda não tem o perfil de organizador (conflito C01) |
| P04 | O pagamento é processado por um provedor real, que informa o resultado ao Ticketfy. | Confirmada; provedor a escolher (Q01) |
| P05 | Meios de pagamento aceitos: cartão de crédito e Pix. | Confirmada |
| P06 | Os ingressos ficam reservados por 15 minutos a partir da criação do pedido. | Confirmada (RN12) |
| P07 | O limite de ingressos por pedido é definido pelo organizador em cada evento. | Confirmada (RN13) |
| P08 | Cada ingresso tem um código único; o formato apresentado na entrada (código ou QR Code) está em aberto. | A definir (Q04) |
| P09 | Os identificadores das entidades são do tipo `Long`. | A confirmar (conflito C08) |

### Pontos em aberto que afetam este caso de uso

- **Q01:** escolha do provedor de pagamento. O mecanismo exato de confirmação (notificação ou consulta) depende dele.
- **Q04:** formato do código do ingresso.
- **Q08:** o que fazer com um pagamento aprovado depois que a reserva expirou.
- **C06:** número máximo de tentativas de pagamento por pedido.

### Fora deste caso de uso

- Cancelamento de pedido antes do pagamento e cancelamento de ingresso pago dependem da política de cancelamento (Q03, RF15).
- Envio de e-mail de confirmação é uma sugestão não confirmada (SUG02).

## 1. Identificação do Caso de Uso

| Campo | Valor |
| --- | --- |
| ID | UC01 |
| Nome | Realizar compra de ingresso |
| Objetivo | Permitir que um cliente autenticado adquira um ou mais ingressos de um evento aberto para vendas, com pagamento aprovado e ingressos emitidos. |
| Descrição | O cliente escolhe os tipos e as quantidades de ingresso de um evento. O sistema reserva as unidades por 15 minutos, cria o pedido e encaminha o pagamento, por cartão de crédito ou Pix, ao provedor. Após a aprovação, converte a reserva em venda e emite um ingresso com código único para cada unidade. |
| Prioridade | Alta |
| Requisitos principais | RF11, RF12 e RF13 |
| Versão | 2.0 (alinhada aos demais documentos) |
| Status | Em revisão |

## 2. Atores

| Ator | Tipo | Responsabilidades |
| --- | --- | --- |
| Cliente | Principal | Escolher os ingressos, confirmar o pedido, escolher o meio de pagamento e concluir o pagamento. |
| Provedor de pagamento | Secundário (sistema externo) | Processar a cobrança por cartão de crédito ou Pix e informar o resultado ao Ticketfy. |
| Organizador | Secundário (interessado) | Não participa da compra. Define antes o evento, os tipos de ingresso, os preços, as quantidades e o limite de ingressos por pedido. |
| Agendador do Ticketfy | Secundário (interno) | Expirar os pedidos não pagos em 15 minutos e devolver as unidades reservadas. |

## 3. Pré-condições

1. O cliente possui conta ativa com perfil de cliente (RN03, RN13).
2. O cliente está autenticado e possui um token JWT válido (RF02).
3. O evento está aberto para vendas, com situação `OPEN` (RN09).
4. Existe ao menos um tipo de ingresso ativo com disponibilidade maior que zero (RN12).
5. A integração com o provedor de pagamento está configurada.

## 4. Pós-condições

**Sucesso**

- O pedido está com situação `PAID`.
- O pagamento está registrado como `APPROVED`, com o identificador da transação no provedor.
- Um ingresso `VALID`, com código único, foi emitido para cada unidade comprada (RN16, RN17).
- A reserva foi convertida em venda: a quantidade reservada diminuiu e a vendida aumentou na mesma proporção.
- Os ingressos aparecem na consulta de ingressos do cliente (RF14).

**Falha**

- O pedido termina como `EXPIRED` ou `PAYMENT_FAILED`, ou não chega a ser criado.
- Nenhum ingresso é emitido.
- As unidades reservadas voltam à disponibilidade (RN12).
- Nenhum valor é cobrado do cliente, exceto no caso de pagamento aprovado após a expiração, cujo tratamento está em aberto (Q08).

## 5. Gatilho

O cliente aciona a opção "Comprar ingressos" na página de um evento aberto para vendas.

## 6. Fluxo Principal

O fluxo descreve uma compra paga com cartão de crédito; o pagamento com Pix está em FA01. Os endpoints seguem o padrão do projeto, sem prefixo de versão (como o `POST /users` já implementado), e exigem o header `Authorization: Bearer <token>`, exceto o endpoint de notificação do provedor.

| Passo | Ator | Ação | Endpoint / Resposta |
| --- | --- | --- | --- |
| 1 | Cliente | Acessa a página de um evento. | `GET /events/{eventId}` → 200 |
| 2 | Sistema | Retorna nome, datas, local, situação e limite de ingressos por pedido do evento. | — |
| 3 | Cliente | Aciona "Comprar ingressos". | `GET /events/{eventId}/ticket-types` → 200 |
| 4 | Sistema | Lista os tipos de ingresso ativos com preço e disponibilidade (RF08, RN12). | — |
| 5 | Cliente | Seleciona os tipos e as quantidades desejadas. | — |
| 6 | Cliente | Confirma a seleção. | `POST /orders` |
| 7 | Sistema | Valida o token e o perfil de cliente (<\<include>> UC02; RF02, RF03). | — |
| 8 | Sistema | Valida os dados de entrada (RNF03), confere se o evento está aberto (RN09) e se a quantidade respeita o limite por pedido do evento (RN13). | — |
| 9 | Sistema | Reserva as unidades de cada tipo de ingresso, com controle de concorrência (<\<include>> UC03; RN12, RNF07). | — |
| 10 | Sistema | Registra o preço unitário de cada item (RN14), calcula o total e grava o pedido como `PENDING_PAYMENT`, com fim da reserva 15 minutos depois (RN12). | 201 Created + `Location: /orders/{orderId}` |
| 11 | Cliente | Escolhe cartão de crédito e informa os dados do cartão no formulário do provedor, sem que eles passem pelo Ticketfy (RNF04). | Formulário do provedor no frontend |
| 12 | Cliente | Envia o pagamento. | `POST /orders/{orderId}/payments` com `method=CREDIT_CARD` |
| 13 | Sistema | Verifica se o pedido pertence ao cliente (RN05), está `PENDING_PAYMENT` e ainda está dentro do prazo de reserva (RN12). | — |
| 14 | Sistema | Registra o pagamento como `PENDING`, com o valor total do pedido (RN15), e envia a cobrança ao provedor (<\<include>> UC04). | — |
| 15 | Provedor | Processa a cobrança e informa o resultado ao Ticketfy. | `POST /payments/notifications` (formato definido pelo provedor) |
| 16 | Sistema | Confere o resultado junto ao provedor e marca o pagamento como `APPROVED` (RN16). | 200 OK ao provedor |
| 17 | Sistema | Em uma única transação (RNF05), marca o pedido como `PAID`, converte a reserva em venda e emite um ingresso `VALID` com código único para cada unidade (<\<include>> UC05; RN16, RN17). | — |
| 18 | Cliente | Consulta o pedido e os ingressos emitidos (RF14). O caso de uso termina. | `GET /orders/{orderId}` → 200, situação `PAID` |

Enquanto o resultado do provedor não chega (passos 15 a 17), o frontend consulta `GET /orders/{orderId}` periodicamente e exibe "Processando pagamento".

## 7. Fluxos Alternativos

**FA01 – Pagamento com Pix** (desvia no passo 11)

1. O cliente escolhe Pix como meio de pagamento.
2. O frontend envia `POST /orders/{orderId}/payments` com `method=PIX`.
3. O sistema solicita ao provedor uma cobrança Pix com validade igual ao tempo restante da reserva.
4. O sistema registra o pagamento como `PENDING` e retorna o QR Code e o código copia e cola.
5. O cliente paga pelo aplicativo do banco.
6. O fluxo retorna ao passo 15.

**FA02 – Cliente inicia a compra sem estar autenticado** (desvia no passo 3)

1. O frontend detecta que não há token válido.
2. O cliente é levado ao login (UC02), e a seleção do evento fica salva no frontend.
3. Após autenticar, o cliente volta à seleção de ingressos, e o fluxo retorna ao passo 4.

**FA03 – Cliente altera a seleção antes de confirmar** (desvia no passo 6)

1. O cliente muda tipos ou quantidades antes de confirmar.
2. O fluxo retorna ao passo 5. Nenhuma unidade foi reservada até aqui.

**FA04 – Cliente retoma um pedido pendente** (desvia no passo 11)

1. O cliente sai da página antes de pagar e volta dentro do prazo de 15 minutos.
2. O sistema retorna o pedido em `GET /orders?status=PENDING_PAYMENT`, com o tempo restante da reserva.
3. O fluxo retorna ao passo 11.

## 8. Fluxos de Exceção

As respostas de erro serão padronizadas pelo exception handler planejado (Documentação de Arquitetura, limitação L02), com um código de negócio e, nos erros de validação, a lista de campos inválidos.

| ID | Situação | Ponto | HTTP / código | Tratamento do sistema |
| --- | --- | --- | --- | --- |
| FE01 | Erro de validação: campo obrigatório ausente ou quantidade menor que 1 | Passo 8 | 400 `VALIDATION_ERROR` | Informa os campos inválidos. Nenhum pedido é criado e nada é reservado. |
| FE02 | Ingresso indisponível, inclusive por compra simultânea | Passo 9 | 409 `TICKET_UNAVAILABLE` | Desfaz a transação inteira e informa a disponibilidade atual de cada tipo afetado. |
| FE03 | Pagamento recusado pelo provedor | Passo 16 | 402 `PAYMENT_REFUSED` | Marca o pagamento como `REFUSED` e mantém a reserva. O cliente pode tentar de novo enquanto a reserva for válida. O número máximo de tentativas está em aberto (C06); se for definido, ao atingi-lo o pedido passa a `PAYMENT_FAILED` e a reserva é devolvida. |
| FE04 | Evento encerrado ou fora de venda | Passos 4 e 8 | 422 `SALES_CLOSED` | Não aceita a compra (RN09). Nenhum pedido é criado. |
| FE05 | Usuário não autenticado, token expirado ou perfil diferente de cliente | Passos 6 e 12 | 401 / 403 | O Spring Security recusa a requisição antes do controller (RF02, RF03). |
| FE06 | Reserva expirada antes do pagamento | Passo 13 ou agendador | 409 `ORDER_EXPIRED` | O agendador marca o pedido como `EXPIRED` e devolve as unidades (RN12). Uma tentativa de pagamento posterior é recusada. |
| FE07 | Pagamento aprovado depois que a reserva expirou | Passo 16 | 200 ao provedor | Nenhum ingresso é emitido automaticamente. O tratamento do valor pago (estorno ou emissão, se ainda houver disponibilidade) está em aberto (Q08). |
| FE08 | Provedor de pagamento indisponível | Passo 14 | 503 `PAYMENT_PROVIDER_UNAVAILABLE` | O pedido continua `PENDING_PAYMENT`, e o cliente pode tentar de novo enquanto a reserva for válida. |
| FE09 | Notificação de pagamento não reconhecida | Passo 15 | 400 | A notificação é descartada e nenhum status muda. A forma de autenticar a notificação depende do provedor (Q01). |
| FE10 | Quantidade acima do limite por pedido definido pelo organizador | Passo 8 | 422 `TICKET_LIMIT_EXCEEDED` | Informa o limite do evento. Nenhum pedido é criado (RN13). |
| FE11 | Acesso ou pagamento de pedido de outro cliente | Passos 12 e 18 | 404 `ORDER_NOT_FOUND` | Responde 404, e não 403, para não revelar que o pedido existe (RN05). |
| FE12 | Erro interno inesperado | Qualquer passo | 500 `INTERNAL_ERROR` | Desfaz a transação, preservando o estoque (RNF05), e mantém a aplicação em funcionamento (RNF10). |

## 9. Regras de Negócio

As regras são as do Documento de Requisitos. Aplicam-se a este caso de uso:

| ID | Regra (resumo) | Onde se aplica |
| --- | --- | --- |
| RN03 | Apenas usuários ativos se autenticam. | Pré-condição 1 |
| RN05 | O cliente só acessa os próprios pedidos e ingressos. | Passos 13 e 18, FE11 |
| RN09 | Eventos encerrados não aceitam compras. | Passo 8, FE04 |
| RN12 | Disponibilidade = total − vendidos − reservados; reserva de 15 minutos a partir da criação do pedido. | Passos 9, 10 e 13, FE02, FE06 |
| RN13 | Apenas clientes compram, dentro da disponibilidade e do limite por pedido definido pelo organizador. | Passo 8, FE10 |
| RN14 | O preço é registrado no pedido no momento da compra. | Passo 10 |
| RN15 | Cada pagamento pertence a um único pedido e tem o valor total dele. | Passo 14 |
| RN16 | Ingressos só são emitidos após a confirmação do pagamento. | Passos 16 e 17 |
| RN17 | Cada ingresso tem identificador único. | Passo 17 |

## 10. Requisitos Funcionais relacionados

| ID | Requisito | Papel neste caso de uso |
| --- | --- | --- |
| RF02 | Autenticação | Identifica o cliente (UC02). |
| RF03 | Controle de acesso por perfil | Garante que só clientes comprem. |
| RF08 | Consulta de eventos | Exibe o evento e os tipos de ingresso (passos 1 a 4). |
| RF10 | Controle de disponibilidade | Reserva, converte em venda ou devolve as unidades (UC03). |
| RF11 | Compra de ingressos | Cria o pedido (passos 6 a 10). |
| RF12 | Registro de pagamento | Registra o pagamento por cartão ou Pix (UC04). |
| RF13 | Emissão de ingresso | Emite os ingressos após a aprovação (UC05). |
| RF14 | Consulta de ingressos | Mostra os ingressos ao cliente (passo 18). |

## 11. Requisitos Não Funcionais relacionados

| ID | Categoria | Aplicação neste caso de uso |
| --- | --- | --- |
| RNF02 | Segurança | Todos os endpoints da compra exigem token JWT válido. |
| RNF03 | Segurança | O pedido é validado com Bean Validation antes de qualquer reserva. |
| RNF04 | Proteção de dados | Os dados do cartão vão direto ao provedor e não passam pelo Ticketfy. |
| RNF05 | Confiabilidade | Reserva, criação do pedido e emissão de ingressos são transacionais. |
| RNF06 | Confiabilidade | Erros retornam respostas padronizadas. |
| RNF07 | Escalabilidade | Compras simultâneas nunca vendem além do estoque. |
| RNF09 | Performance | A consulta de eventos e de ingressos responde em até 1 segundo (sugestão). |
| RNF14 | Manutenibilidade | As regras de reserva, compra e expiração têm testes automatizados. |

## 12. Dados envolvidos

As entidades e atributos seguem o Diagrama de Classes de Domínio, com nomes em inglês como no código.

| Entidade | Atributos usados neste caso de uso | Uso |
| --- | --- | --- |
| `User` | id, name, email, role, active | Identifica o comprador e confere o perfil. |
| `Event` | id, name, startDateTime, endDateTime, maxTicketsPerOrder, status, venue | Define se a venda está aberta e o limite por pedido. |
| `TicketType` | id, name, price, totalQuantity, soldQuantity, reservedQuantity, active | Fonte do preço e da disponibilidade; recebe a reserva. |
| `Order` | id, customer, createdAt, expiresAt, total, status, items | Agrupa a compra e controla o prazo de 15 minutos. |
| `OrderItem` | id, quantity, unitPrice, ticketType | Guarda o preço de cada tipo no momento da compra. |
| `Payment` | id, order, method, amount, status, providerTransactionId, createdAt | Registra cada tentativa de pagamento e o resultado do provedor. |
| `Ticket` | id, code, status, issuedAt, orderItem | Ingresso emitido após a aprovação do pagamento. |

**Situações**

- **Order:** `PENDING_PAYMENT` → `PAID`, `EXPIRED` ou `PAYMENT_FAILED`.
- **Payment:** `PENDING` → `APPROVED` ou `REFUSED`; meio `CREDIT_CARD` ou `PIX`.
- **Ticket:** emitido como `VALID`; depois pode passar a `USED` (validação) ou `CANCELLED` (fora deste caso de uso).

**Exemplo de requisição `POST /orders`**

```json
{
  "eventId": 12,
  "items": [
    { "ticketTypeId": 31, "quantity": 2 },
    { "ticketTypeId": 32, "quantity": 1 }
  ]
}
```

O cliente não envia preços nem totais: o servidor calcula tudo a partir dos tipos de ingresso (RN14).

## 13. Relacionamentos

O UC01 inclui quatro casos de uso, e o processamento do pagamento se especializa nos dois meios aceitos: cartão de crédito e Pix.

&#91;embedded content: UC01 e relacionamentos · 4 inclusões, 1 generalização, 3 atores\]

| Tipo | Origem | Destino | Justificativa |
| --- | --- | --- | --- |
| <\<include>> | UC01 | UC02 Autenticar usuário | Toda compra exige cliente autenticado (RF02, RN13). |
| <\<include>> | UC01 | UC03 Reservar ingressos | A reserva de 15 minutos sempre acontece antes do pagamento (RN12). O Agendador participa deste caso de uso ao expirar reservas não pagas. |
| <\<include>> | UC01 | UC04 Processar pagamento | Sempre executado; depende do provedor de pagamento externo (RF12). |
| <\<include>> | UC01 | UC05 Emitir ingressos | Sempre executado após a confirmação do pagamento (RN16). |
| Generalização | UC04.1 Pagar com cartão e UC04.2 Pagar com Pix | UC04 | Os dois herdam a verificação do pedido e o registro do pagamento. Diferem na cobrança: o cartão é informado no formulário do provedor; o Pix gera um QR Code com validade igual ao tempo restante da reserva (FA01). |
| Generalização (atores) | Cliente e Organizador | Usuário | Ambos herdam a autenticação e os dados de conta. Apenas o Cliente se associa ao UC01. |

O pagamento foi modelado como generalização, e não como <\<extend>>, porque o cliente sempre paga por exatamente um dos dois meios: o pagamento não é opcional, só a forma muda. Não há extensões confirmadas: o cancelamento de pedido pendente depende de Q03, e o tratamento de pagamento aprovado após a expiração depende de Q08.

## 14. Critérios de aceite

**CA01 – Compra aprovada com cartão:** dado um cliente autenticado e um tipo de ingresso com 10 unidades disponíveis, quando ele compra 2 ingressos e o provedor aprova o pagamento, então o pedido fica `PAID`, 2 ingressos `VALID` são emitidos com códigos distintos e a disponibilidade passa a 8.

**CA02 – Reserva:** dado um tipo de ingresso com 10 unidades disponíveis, quando um pedido de 3 ingressos é criado e ainda não foi pago, então a disponibilidade exibida passa a 7 e o pedido fica `PENDING_PAYMENT`, com fim da reserva 15 minutos após a criação.

**CA03 – Expiração:** dado um pedido `PENDING_PAYMENT` com 3 unidades reservadas, quando passam 15 minutos sem pagamento aprovado, então o pedido fica `EXPIRED` e a disponibilidade volta a 10.

**CA04 – Sem venda acima do estoque:** dado um tipo de ingresso com 1 unidade disponível, quando dois clientes pedem 1 ingresso ao mesmo tempo, então exatamente um pedido é criado e o outro recebe 409.

**CA05 – Pagamento recusado:** dado um pedido `PENDING_PAYMENT`, quando o provedor recusa o pagamento, então o pagamento fica `REFUSED`, a reserva é mantida e o cliente pode tentar novamente dentro do prazo.

**CA06 – Limite por pedido:** dado um evento cujo organizador definiu limite de 4 ingressos por pedido, quando o cliente pede 5, então o sistema responde 422 e nenhum pedido é criado.

**CA07 – Usuário não autenticado:** dada uma requisição sem token, quando ela chama `POST /orders`, então o sistema responde 401 e nenhum pedido é criado.

**CA08 – Evento encerrado:** dado um evento com situação `CLOSED`, quando o cliente tenta comprar, então o sistema responde 422.

**CA09 – Compra com Pix:** dado um pedido `PENDING_PAYMENT`, quando o cliente escolhe Pix e paga dentro do prazo, então o sistema retorna o QR Code, e após a confirmação do provedor o pedido fica `PAID` e os ingressos são emitidos.

**CA10 – Preço registrado no pedido:** dado um pedido criado com ingresso a R$ 100,00, quando o organizador altera o preço para R$ 120,00, então o pedido existente continua com R$ 100,00 por ingresso.

**CA11 – Total calculado no servidor:** dado um tipo de ingresso de R$ 100,00, quando o cliente pede 2 ingressos, então o total do pedido é R$ 200,00, calculado pelo servidor.

## 15. Cenários de teste

| ID | Tipo | Cenário | Entrada | Resultado esperado | Referência |
| --- | --- | --- | --- | --- | --- |
| CT01 | Positivo | Compra com cartão aprovado | 2 ingressos, pagamento aprovado | Pedido `PAID`; 2 ingressos `VALID` | Fluxo principal, CA01 |
| CT02 | Positivo | Compra com Pix | 1 ingresso, Pix pago dentro do prazo | QR Code retornado; após confirmação, pedido `PAID` | FA01, CA09 |
| CT03 | Positivo | Retomar pedido pendente | Pedido criado há 5 minutos | Pedido listado com o tempo restante; pagamento aceito | FA04 |
| CT04 | Positivo | Preço alterado após o pedido | Preço do tipo muda depois da criação | Pedido mantém o preço original | CA10 |
| CT05 | Negativo | Quantidade inválida | `quantity` = 0 | 400; nenhum pedido criado | FE01 |
| CT06 | Negativo | Limite por pedido excedido | Limite 4, pedido de 5 | 422; nenhum pedido criado | FE10, CA06 |
| CT07 | Negativo | Sem token | Requisição sem `Authorization` | 401 | FE05, CA07 |
| CT08 | Negativo | Organizador tentando comprar | Token de organizador | 403 | FE05, RN13 |
| CT09 | Negativo | Evento encerrado | Evento `CLOSED` | 422 | FE04, CA08 |
| CT10 | Negativo | Pedido de outro cliente | `orderId` de outro usuário | 404 | FE11, RN05 |
| CT11 | Negativo | Pagamento recusado | Provedor recusa o cartão | Pagamento `REFUSED`; reserva mantida | FE03, CA05 |
| CT12 | Exceção | Concorrência na última unidade | 50 pedidos simultâneos para 1 unidade | 1 pedido criado; 49 respostas 409 | FE02, CA04 |
| CT13 | Exceção | Expiração da reserva | Pedido sem pagamento por 15 minutos | Pedido `EXPIRED`; unidades devolvidas; pagamento posterior recebe 409 | FE06, CA03 |
| CT14 | Exceção | Pagamento aprovado após expiração | Confirmação do provedor para pedido `EXPIRED` | Nenhum ingresso emitido; tratamento do valor conforme Q08 | FE07 |
| CT15 | Exceção | Provedor indisponível | Provedor não responde | 503; pedido segue `PENDING_PAYMENT` | FE08 |
| CT16 | Exceção | Falha durante a emissão | Erro simulado no passo 17 | Nada é gravado pela metade; pedido segue `PENDING_PAYMENT` | FE12, RNF05 |

CT12 e CT16 devem ser testes de integração com PostgreSQL real, porque dependem do comportamento de bloqueio e de transação do banco.

## 16. Resumo do Caso de Uso

| Item | Resumo |
| --- | --- |
| Caso de uso | UC01 – Realizar compra de ingresso |
| Ator principal | Cliente |
| Atores secundários | Provedor de pagamento, Agendador do Ticketfy |
| Entrada | Evento, tipos de ingresso, quantidades e meio de pagamento (cartão de crédito ou Pix) |
| Saída | Pedido `PAID` e ingressos `VALID` com código único |
| Endpoints | `GET /events/{id}`, `GET /events/{id}/ticket-types`, `POST /orders`, `POST /orders/{id}/payments`, `GET /orders/{id}`, `POST /payments/notifications` |
| Pontos críticos | Reserva por 15 minutos sem venda acima do estoque, limite por pedido definido pelo organizador e emissão somente após confirmação do provedor |
| Inclui | UC02 Autenticar usuário, UC03 Reservar ingressos, UC04 Processar pagamento, UC05 Emitir ingressos |
| Pendências | Q01 (provedor), Q04 (formato do código), Q08 (pagamento após expiração), C06 (tentativas de pagamento) |

O cliente escolhe os ingressos de um evento aberto, o Ticketfy reserva as unidades por 15 minutos e encaminha o pagamento ao provedor. Os ingressos só são emitidos após a confirmação do pagamento; se ele não for aprovado no prazo, a reserva expira e as unidades voltam à venda.
