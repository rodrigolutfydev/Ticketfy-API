# Ticketfy — Caso de Uso

Oct 7, 2026

## 0. Análise do contexto e premissas

O caso de uso escolhido é **Realizar compra de ingresso**, porque é o fluxo que gera receita e envolve quase todos os domínios do sistema: usuário, evento, tipo de ingresso, cupom, pedido, pagamento, ingresso e o extrato do organizador. Esta versão foi revisada contra o código, que é a fonte de verdade, e alinhada ao Documento de Requisitos, ao Diagrama de Classes de Domínio e à Documentação de Arquitetura. As referências RF, RN e RNF seguem a numeração do Documento de Requisitos.

### Premissas e decisões

| ID | Premissa ou decisão | Situação |
| --- | --- | --- |
| P01 | Backend em Java 17 e Spring Boot 4, com PostgreSQL e migrations Flyway. | Confirmada |
| P02 | Access token JWT de 10 minutos ligado a uma sessão no banco, com refresh token em cookie; senhas com BCrypt. | Implementada |
| P03 | Perfis `USER`, `ORGANIZER` e `ADMIN`. Qualquer perfil autenticado compra ingressos. | Implementada; diverge da RN13, que restringe a compra ao cliente (I09) |
| P04 | O pagamento é simulado: o servidor aprova na hora, sem provedor externo. | Implementada; Pix com Asaas planejado |
| P05 | Meio de pagamento registrado: `SIMULATED`. Os valores `PIX` e `CREDIT_CARD` existem no enum, mas não são usados. | Implementada; cartão de crédito fora do roadmap |
| P06 | Os ingressos ficam reservados por 15 minutos a partir da criação do pedido (`ORDER_RESERVATION_MINUTES`). | Implementada (RN12) |
| P07 | O limite de ingressos por pedido é definido pelo organizador em cada tipo de ingresso (`maxPerOrder`), e todos os itens do pedido pertencem ao mesmo evento. | Implementada (RN13) |
| P08 | Cada ingresso tem um código único de 16 caracteres, gerado com `SecureRandom`. | Implementada (RN17) |
| P09 | Os identificadores das entidades são do tipo `UUID`. | Implementada |
| P10 | O cliente pode aplicar um cupom de desconto do evento; o desconto é consumido na criação do pedido. | Implementada (RF19) |
| P11 | Um pedido com total zero (lote gratuito ou desconto integral) é confirmado na criação, sem pagamento. | Implementada (RN16) |
| P12 | O header `Idempotency-Key` impede pedidos duplicados por clique duplo ou reenvio. | Implementada |

### Pontos em aberto que afetam este caso de uso

- **I08:** a compra não confere se o evento já começou ou terminou; só se está ativo e não cancelado.
- **Q08:** o que fazer com um pagamento aprovado depois que a reserva expirou. Não ocorre com o pagamento simulado e volta a valer com o Asaas.

### Fora deste caso de uso

- Reembolso do pedido pago (RF15) e cancelamento do evento (RF07).
- Transferência dos ingressos emitidos (RF20).
- Envio de e-mail de confirmação, planejado com Brevo (SUG02).

## 1. Identificação do Caso de Uso

| Campo | Valor |
| --- | --- |
| ID | UC01 |
| Nome | Realizar compra de ingresso |
| Objetivo | Permitir que um usuário autenticado adquira um ou mais ingressos de um evento ativo, com pagamento aprovado e ingressos emitidos. |
| Descrição | O cliente escolhe os tipos e as quantidades de ingresso de um evento e, se quiser, informa um cupom. O sistema reserva as unidades por 15 minutos e cria o pedido. O cliente paga, o pagamento simulado é aprovado na hora, e o sistema emite um ingresso com código único para cada unidade. Pedidos de valor zero são confirmados na criação. |
| Prioridade | Alta |
| Requisitos principais | RF11, RF12 e RF13 |
| Versão | 3.0 (revisada contra o código) |
| Status | Revisado |

**Histórico de versões**

| Versão | Descrição |
| --- | --- |
| 2.0 | Alinhada aos demais documentos: provedor de pagamento real com cartão e Pix, reserva de 15 minutos e limite por pedido definido pelo organizador. Status: em revisão. |
| 3.0 | Revisada contra o código: pagamento simulado, limite por tipo de ingresso, cupom, pedido de valor zero, idempotência, endpoints e erros reais. |

## 2. Atores

| Ator | Tipo | Responsabilidades |
| --- | --- | --- |
| Cliente | Principal | Escolher os ingressos, informar o cupom, confirmar o pedido e pagar. Qualquer perfil autenticado pode atuar como cliente (P03). |
| Gateway de pagamento simulado | Secundário (interno) | Aprovar o pagamento na hora. Será substituído por um provedor real (Pix com Asaas), que está planejado. |
| Organizador | Secundário (interessado) | Não participa da compra. Define antes o evento, os tipos de ingresso, os preços, as quantidades, o limite por pedido de cada tipo e os cupons. |
| Rotina de expiração (`OrderExpirationJob`) | Secundário (interno) | A cada 60 segundos, expirar os pedidos pendentes cujo prazo de 15 minutos passou e devolver as unidades e o uso do cupom. |

## 3. Pré-condições

1. O cliente possui conta não excluída (RN03).
2. O cliente está autenticado, com access token válido de uma sessão ativa (RF02).
3. O evento está ativo e não foi cancelado (RN09).
4. Existe ao menos um tipo de ingresso ativo com disponibilidade maior que zero (RN12).

## 4. Pós-condições

**Sucesso**

- O pedido está com situação `PAID`, com subtotal, desconto, total, taxa da plataforma e valor líquido gravados.
- Se o total é maior que zero, o pagamento está registrado como `APPROVED`, método `SIMULATED`, com o valor total do pedido, e o extrato do organizador recebeu um crédito `SALE_CREDIT` com o valor líquido.
- Um ingresso `VALID`, com código único, foi emitido para cada unidade comprada, tendo o cliente como dono (RN16, RN17).
- As unidades reservadas permanecem contadas como vendidas.
- Os ingressos aparecem em `GET /tickets/me` e no pedido (RF14).

**Falha**

- O pedido termina como `EXPIRED` ou `CANCELLED`, ou não chega a ser criado.
- Nenhum ingresso é emitido e nenhum pagamento é registrado.
- As unidades reservadas e o uso do cupom voltam à disponibilidade (RN12).

## 5. Gatilho

O cliente aciona a opção "Comprar ingressos" na página de um evento.

## 6. Fluxo Principal

O fluxo descreve uma compra com total maior que zero, sem cupom; o cupom está em FA01 e o pedido de valor zero em FA02. Os endpoints não têm prefixo de versão. Os de compra exigem o header `Authorization: Bearer <token>`; os de consulta de evento são públicos.

| Passo | Ator | Ação | Endpoint / Resposta |
| --- | --- | --- | --- |
| 1 | Cliente | Acessa a página de um evento. | `GET /events/{eventId}` → 200 |
| 2 | Sistema | Retorna nome, descrição, imagem, local, datas e situação do evento. | — |
| 3 | Cliente | Aciona "Comprar ingressos". | `GET /events/{eventId}/ticket-types` → 200 |
| 4 | Sistema | Lista os tipos de ingresso ativos com preço, disponibilidade e limite por pedido (RF08, RN12). | — |
| 5 | Cliente | Seleciona os tipos e as quantidades desejadas. | — |
| 6 | Cliente | Confirma a seleção. O frontend gera uma chave única para esta tentativa de compra. | `POST /orders` com header `Idempotency-Key` |
| 7 | Sistema | O `SecurityFilter` valida o token e a sessão; o service confere que a conta não foi excluída (RF02, RF03). | — |
| 8 | Sistema | Valida os dados de entrada: lista de itens não vazia e quantidade positiva (RNF03). Se a chave de idempotência já foi usada, segue FA03. | — |
| 9 | Sistema | Para cada item, confere se o tipo de ingresso está ativo e se o evento está ativo e não cancelado (RN09), se todos os itens são do mesmo evento e se a quantidade respeita o limite por pedido do tipo (RN13). | — |
| 10 | Sistema | Reserva as unidades de cada tipo com um `UPDATE` condicional, que só soma à quantidade vendida se couber no total e se o evento continuar ativo e não cancelado (RN12, RNF07). | — |
| 11 | Sistema | Registra o preço unitário de cada item (RN14), calcula o subtotal, congela o percentual e o valor da taxa da plataforma e grava o pedido como `PENDING`, com fim da reserva 15 minutos depois (RN12). | 201 Created, com o pedido (status, subtotal, desconto, total, `expiresAt` e itens) |
| 12 | Cliente | Confirma o pagamento. | `POST /orders/{orderId}/payment`, sem corpo |
| 13 | Sistema | Trava o pedido (`SELECT … FOR UPDATE`) e confere se ele pertence ao cliente (RN05), se o evento não foi cancelado, se o pedido ainda não foi pago e se a reserva não expirou (RN12). | — |
| 14 | Sistema | Em uma única transação (RNF05), registra o pagamento `SIMULATED` como `APPROVED`, com o valor total do pedido (RN15); marca o pedido como `PAID`; grava o crédito no extrato do organizador; e emite um ingresso `VALID` com código único para cada unidade (RN16, RN17). | 201 Created, com o pagamento |
| 15 | Cliente | Consulta o pedido e os ingressos emitidos (RF14). O caso de uso termina. | `GET /orders/{orderId}` → 200, situação `PAID`, com os ingressos |

## 7. Fluxos Alternativos

**FA01 – Compra com cupom de desconto** (desvia nos passos 5 e 11)

1. O cliente informa um código de cupom. O frontend pode pré-visualizar o desconto com `POST /events/{eventId}/coupons/preview`, enviando o código e os itens; a resposta traz subtotal, desconto e total, sem consumir o cupom.
2. O cliente confirma, e o frontend envia `couponCode` no `POST /orders`.
3. Depois da reserva (passo 10), o sistema consome um uso do cupom com um `UPDATE … RETURNING` condicional (ativo, dentro da validade, abaixo do limite de usos) e calcula o desconto com os valores devolvidos. O desconto fixo é limitado ao subtotal.
4. O pedido guarda o cupom, o código, o desconto e o total (`total = subtotal − desconto`). A taxa da plataforma é calculada sobre o total.
5. Se o total for zero, segue FA02; caso contrário, o fluxo retorna ao passo 11.

**FA02 – Pedido de valor zero** (desvia no passo 11)

1. O total do pedido é zero, por lote gratuito ou desconto integral.
2. Na mesma transação da criação, o sistema confirma o pedido como `PAID` e emite os ingressos, sem registrar pagamento e sem lançamento no extrato.
3. A resposta 201 já traz o pedido `PAID` com os ingressos, e o fluxo retorna ao passo 15.

**FA03 – Requisição repetida com a mesma chave de idempotência** (desvia no passo 8)

1. O cliente reenvia o `POST /orders` com a mesma `Idempotency-Key`, por clique duplo ou nova tentativa depois de uma falha de rede.
2. O sistema encontra o pedido já criado com essa chave e o devolve com 201, sem reservar de novo. Se duas requisições com a mesma chave chegam juntas, a restrição de unicidade do banco garante um único pedido, e a segunda recebe o pedido da primeira.
3. O caso de uso continua a partir do estado em que o pedido estiver.

**FA04 – Cliente cancela o pedido antes de pagar** (desvia no passo 12)

1. O cliente desiste da compra.
2. O frontend envia `DELETE /orders/{orderId}`.
3. O sistema marca o pedido como `CANCELLED` e devolve as unidades e o uso do cupom. O caso de uso termina sem ingressos.

**FA05 – Cliente retoma um pedido pendente** (desvia no passo 12)

1. O cliente sai da página antes de pagar e volta dentro do prazo de 15 minutos.
2. O frontend lista os pedidos do cliente com `GET /orders` (paginado) ou consulta o pedido com `GET /orders/{orderId}`, que traz `expiresAt`.
3. O fluxo retorna ao passo 12.

**FA06 – Cliente inicia a compra sem estar autenticado** (desvia no passo 6)

1. O frontend detecta que não há sessão válida.
2. O cliente é levado ao login (UC02), e a seleção do evento fica salva no frontend.
3. Após autenticar, o cliente volta à seleção de ingressos, e o fluxo retorna ao passo 5.

## 8. Fluxos de Exceção

As respostas de erro seguem a RFC 7807 (`application/problem+json`), com `type` terminado no identificador da tabela e, nos erros de validação, a lista `errors` por campo. Qualquer erro desfaz a transação inteira, inclusive a reserva e o uso do cupom.

| ID | Situação | Ponto | HTTP / `type` | Tratamento do sistema |
| --- | --- | --- | --- | --- |
| FE01 | Erro de validação: lista de itens vazia, item sem tipo ou quantidade menor que 1 | Passo 8 | 400 `validation-failed` | Informa os campos inválidos. Nenhum pedido é criado e nada é reservado. |
| FE02 | Tipo de ingresso inexistente ou inativo, ou evento inativo ou cancelado | Passo 9 | 404 `ticket-type-not-found` | Nenhum pedido é criado (RN09). |
| FE03 | Itens de eventos diferentes no mesmo pedido | Passo 9 | 400 `mixed-events-order` | Nenhum pedido é criado (RN13). |
| FE04 | Quantidade acima do limite por pedido do tipo de ingresso | Passo 9 | 400 `max-per-order-exceeded` | Informa o limite do tipo. Nenhum pedido é criado (RN13). |
| FE05 | Ingresso indisponível, inclusive por compra simultânea | Passo 10 | 409 `insufficient-stock` | Desfaz a transação inteira (RN12, RNF07). |
| FE06 | Evento cancelado entre a consulta e a reserva | Passo 10 | 409 `invalid-event-state` | Desfaz a transação inteira. |
| FE07 | Cupom inválido, expirado, inativo, esgotado ou de outro evento | FA01 | 422 `invalid-coupon` | Mesma mensagem para todos os casos. Desfaz a reserva. A falha conta no limite de tentativas. |
| FE08 | Muitas tentativas de cupom inválido | FA01 | 429 `too-many-coupon-attempts` | Recusa antes de reservar, com `Retry-After`. O limite é de 10 falhas em 15 minutos por usuário. |
| FE09 | Usuário não autenticado ou token inválido | Passos 6 e 12 | 401 `authentication-required` | O Spring Security recusa a requisição antes do controller (RF02). |
| FE10 | Sessão revogada ou vencida, ou conta excluída | Passos 7 e 13 | 401 `session-expired` | Recusa a requisição; o cliente precisa entrar de novo. |
| FE11 | Pedido de outro cliente, ou chave de idempotência usada por outro cliente | Passos 8, 13 e 15 | 404 `order-not-found` | Responde 404, e não 403, para não revelar que o pedido existe (RN05). |
| FE12 | Reserva expirada, ou pedido cancelado ou expirado, antes do pagamento | Passo 13 ou rotina | 409 `invalid-order-state` | A rotina marca o pedido como `EXPIRED` e devolve as unidades e o cupom (RN12). Uma tentativa de pagamento posterior é recusada. |
| FE13 | Pedido já pago | Passo 13 | 409 `invalid-payment-state` | Nenhum pagamento novo é registrado. |
| FE14 | Evento cancelado depois da criação do pedido | Passo 13 | 409 `invalid-event-state` | O pagamento é recusado; o pedido pendente é cancelado pelo cancelamento do evento. |
| FE15 | Erro interno inesperado | Qualquer passo | 500 `internal-error` | Desfaz a transação, preservando o estoque (RNF05), e mantém a aplicação em funcionamento (RNF10). A resposta traz o `requestId`. |

## 9. Regras de Negócio

As regras são as do Documento de Requisitos. Aplicam-se a este caso de uso:

| ID | Regra (resumo) | Onde se aplica |
| --- | --- | --- |
| RN03 | Apenas contas não excluídas se autenticam. | Pré-condição 1, FE10 |
| RN05 | O cliente só acessa os próprios pedidos e ingressos. | Passos 13 e 15, FE11 |
| RN09 | Eventos cancelados ou excluídos não aceitam compras. | Passos 9 e 10, FE02, FE06 |
| RN12 | Disponibilidade = total − vendidos (os reservados contam como vendidos); reserva de 15 minutos a partir da criação do pedido. | Passos 10, 11 e 13, FE05, FE12 |
| RN13 | Compra dentro da disponibilidade e do limite por pedido de cada tipo, com itens de um único evento. | Passo 9, FE03, FE04 |
| RN14 | O preço é registrado no pedido no momento da compra. | Passo 11 |
| RN15 | Cada pagamento pertence a um único pedido e tem o valor total dele. | Passo 14 |
| RN16 | Ingressos só são emitidos após a aprovação do pagamento, ou na criação de um pedido de valor zero. | Passo 14, FA02 |
| RN17 | Cada ingresso tem código único. | Passo 14 |

## 10. Requisitos Funcionais relacionados

| ID | Requisito | Papel neste caso de uso |
| --- | --- | --- |
| RF02 | Autenticação | Identifica o cliente (UC02). |
| RF03 | Controle de acesso por perfil | Garante que o cliente só acesse os próprios pedidos. |
| RF08 | Consulta de eventos | Exibe o evento e os tipos de ingresso (passos 1 a 4). |
| RF10 | Controle de disponibilidade | Reserva e devolve as unidades (UC03). |
| RF11 | Compra de ingressos | Cria o pedido (passos 6 a 11). |
| RF12 | Registro de pagamento | Registra o pagamento simulado (UC04). |
| RF13 | Emissão de ingresso | Emite os ingressos após a aprovação (UC05). |
| RF14 | Consulta de ingressos | Mostra os ingressos ao cliente (passo 15). |
| RF19 | Cupons de desconto | Aplica o desconto do cupom (FA01). |
| RF22 | Financeiro do organizador | Recebe o crédito da venda no extrato (passo 14). |

## 11. Requisitos Não Funcionais relacionados

| ID | Categoria | Aplicação neste caso de uso |
| --- | --- | --- |
| RNF02 | Segurança | Todos os endpoints da compra exigem access token de uma sessão ativa. |
| RNF03 | Segurança | O pedido é validado com Bean Validation antes de qualquer reserva. |
| RNF04 | Proteção de dados | O Ticketfy não recebe dados de cartão. |
| RNF05 | Confiabilidade | Reserva, consumo do cupom, criação do pedido, pagamento, crédito no extrato e emissão de ingressos são transacionais. |
| RNF06 | Confiabilidade | Erros retornam respostas no padrão RFC 7807. |
| RNF07 | Escalabilidade | Compras simultâneas nunca vendem além do estoque nem usam um cupom além do limite. |
| RNF09 | Performance | A consulta de eventos e de ingressos responde em até 1 segundo (sugestão). |
| RNF14 | Manutenibilidade | As regras de reserva, cupom, compra e expiração têm testes automatizados com PostgreSQL real. |

## 12. Dados envolvidos

As entidades e atributos seguem o Diagrama de Classes de Domínio, com nomes em inglês como no código.

| Entidade | Atributos usados neste caso de uso | Uso |
| --- | --- | --- |
| `User` | id, name, email, role, deletedAt | Identifica o comprador e confere que a conta não foi excluída. |
| `Event` | id, name, startsAt, endsAt, active, cancelledAt, organizer | Define se o evento aceita compras e quem recebe o crédito. |
| `TicketType` | id, name, price, quantityTotal, quantitySold, maxPerOrder, active, event | Fonte do preço, da disponibilidade e do limite por pedido; recebe a reserva. |
| `Coupon` | id, code, discountType, discountValue, maxUses, usesCount, startsAt, endsAt, active, firstUsedAt | Define o desconto e controla os usos (FA01). |
| `Order` | id, user, status, subtotalAmount, discountAmount, totalAmount, couponId, couponCode, platformFeePercent, platformFee, netAmount, expiresAt, idempotencyKey, items, version | Agrupa a compra, congela valores e controla o prazo de 15 minutos. |
| `OrderItem` | id, quantity, unitPrice, ticketType | Guarda o preço de cada tipo no momento da compra. |
| `Payment` | id, order, method, amount, status, approvedAt | Registra o pagamento aprovado. |
| `Ticket` | id, code, status, order, orderItem, ticketType, owner, createdAt | Ingresso emitido após a aprovação do pagamento. |
| `LedgerEntry` | organizerId, eventId, orderId, type, amount | Crédito do valor líquido no extrato do organizador. |

**Situações**

- **Order:** `PENDING` → `PAID`, `EXPIRED` ou `CANCELLED`; um pedido `PAID` pode passar a `REFUNDED` (fora deste caso de uso).
- **Payment:** criado como `PENDING` e aprovado na mesma transação (`APPROVED`); pode passar a `REFUNDED` no reembolso. O valor `REJECTED` existe, mas o pagamento simulado nunca recusa. Método `SIMULATED`.
- **Ticket:** emitido como `VALID`; depois pode passar a `USED` (validação) ou `CANCELLED` (reembolso, fora deste caso de uso).

**Exemplo de requisição `POST /orders`**

```http
POST /orders
Authorization: Bearer <token>
Idempotency-Key: 6f1c2b8e-3d4a-4f0b-9e7d-1a2b3c4d5e6f
Content-Type: application/json

{
  "items": [
    { "ticketTypeId": "0b9d8c7e-6f5a-4b3c-2d1e-0f9a8b7c6d5e", "quantity": 2 },
    { "ticketTypeId": "1c0e9d8f-7a6b-5c4d-3e2f-1a0b9c8d7e6f", "quantity": 1 }
  ],
  "couponCode": "PROMO10"
}
```

O cliente não envia o evento, preços nem totais: o servidor deduz o evento pelos tipos de ingresso e calcula tudo a partir deles (RN14). O `couponCode` é opcional.

## 13. Relacionamentos

O UC01 inclui quatro casos de uso. O diagrama embutido abaixo é da versão 2.0 e mostra a generalização do pagamento em cartão e Pix, que não existe no código; a tabela a seguir descreve os relacionamentos atuais.

&#91;embedded content: UC01 e relacionamentos · 4 inclusões, 1 generalização, 3 atores\]

| Tipo | Origem | Destino | Justificativa |
| --- | --- | --- | --- |
| <\<include>> | UC01 | UC02 Autenticar usuário | Toda compra exige usuário autenticado (RF02). |
| <\<include>> | UC01 | UC03 Reservar ingressos | A reserva de 15 minutos sempre acontece na criação do pedido (RN12). A rotina de expiração participa deste caso de uso ao expirar reservas não pagas. |
| <\<include>> | UC01 | UC04 Processar pagamento | Executado em todo pedido com total maior que zero; hoje é o pagamento simulado (RF12). |
| <\<include>> | UC01 | UC05 Emitir ingressos | Sempre executado após a aprovação do pagamento ou na confirmação de um pedido de valor zero (RN16). |
| <\<extend>> | UC06 Aplicar cupom | UC01 | Opcional: só acontece quando o cliente informa um cupom (FA01). |

O pagamento real (Pix com Asaas) está planejado. Quando for integrado, o UC04 voltará a depender de um ator externo e da confirmação assíncrona do provedor, e a pergunta Q08 precisará de resposta.

## 14. Critérios de aceite

**CA01 – Compra aprovada:** dado um cliente autenticado e um tipo de ingresso com 10 unidades disponíveis, quando ele compra 2 ingressos e paga, então o pedido fica `PAID`, 2 ingressos `VALID` são emitidos com códigos distintos e a disponibilidade passa a 8.

**CA02 – Reserva:** dado um tipo de ingresso com 10 unidades disponíveis, quando um pedido de 3 ingressos é criado e ainda não foi pago, então a disponibilidade exibida passa a 7 e o pedido fica `PENDING`, com fim da reserva 15 minutos após a criação.

**CA03 – Expiração:** dado um pedido `PENDING` com 3 unidades reservadas e um cupom aplicado, quando passam 15 minutos sem pagamento, então a rotina marca o pedido como `EXPIRED`, a disponibilidade volta a 10 e o uso do cupom é devolvido.

**CA04 – Sem venda acima do estoque:** dado um tipo de ingresso com 1 unidade disponível, quando dois clientes pedem 1 ingresso ao mesmo tempo, então exatamente um pedido é criado e o outro recebe 409.

**CA05 – Pagamento de pedido expirado:** dado um pedido cuja reserva expirou, quando o cliente tenta pagar, então o sistema responde 409 e nenhum ingresso é emitido.

**CA06 – Limite por pedido:** dado um tipo de ingresso cujo organizador definiu limite de 4 ingressos por pedido, quando o cliente pede 5, então o sistema responde 400 e nenhum pedido é criado.

**CA07 – Usuário não autenticado:** dada uma requisição sem token, quando ela chama `POST /orders`, então o sistema responde 401 e nenhum pedido é criado.

**CA08 – Evento cancelado:** dado um evento cancelado, quando o cliente tenta comprar, então o sistema responde 404 e nenhum pedido é criado.

**CA09 – Cupom:** dado um cupom de 10% válido para o evento, quando o cliente compra 2 ingressos de R$ 100,00 com o cupom, então o pedido tem subtotal R$ 200,00, desconto R$ 20,00 e total R$ 180,00, e o cupom tem um uso a mais.

**CA10 – Preço registrado no pedido:** dado um pedido criado com ingresso a R$ 100,00, quando o organizador altera o preço para R$ 120,00, então o pedido existente continua com R$ 100,00 por ingresso.

**CA11 – Total calculado no servidor:** dado um tipo de ingresso de R$ 100,00, quando o cliente pede 2 ingressos, então o total do pedido é R$ 200,00, calculado pelo servidor.

**CA12 – Pedido de valor zero:** dado um tipo de ingresso gratuito, quando o cliente pede 1 ingresso, então o pedido é criado como `PAID`, com o ingresso emitido, sem pagamento e sem lançamento no extrato.

**CA13 – Idempotência:** dado um pedido criado com uma `Idempotency-Key`, quando o cliente repete a requisição com a mesma chave, então recebe o mesmo pedido e a disponibilidade não muda.

**CA14 – Último uso do cupom:** dado um cupom com um único uso restante, quando dois clientes compram com ele ao mesmo tempo, então apenas um pedido recebe o desconto e o outro recebe 422.

## 15. Cenários de teste

| ID | Tipo | Cenário | Entrada | Resultado esperado | Referência |
| --- | --- | --- | --- | --- | --- |
| CT01 | Positivo | Compra paga | 2 ingressos, pagamento confirmado | Pedido `PAID`; 2 ingressos `VALID`; crédito no extrato | Fluxo principal, CA01 |
| CT02 | Positivo | Compra com cupom | 2 ingressos, cupom de 10% | Desconto aplicado; uso do cupom contado | FA01, CA09 |
| CT03 | Positivo | Retomar pedido pendente | Pedido criado há 5 minutos | Pedido listado com `expiresAt`; pagamento aceito | FA05 |
| CT04 | Positivo | Preço alterado após o pedido | Preço do tipo muda depois da criação | Pedido mantém o preço original | CA10 |
| CT05 | Positivo | Pedido de valor zero | Lote gratuito ou cupom de 100% | Pedido `PAID` na criação, sem pagamento | FA02, CA12 |
| CT06 | Positivo | Repetição com a mesma chave | Mesma `Idempotency-Key` duas vezes | Mesmo pedido; estoque reservado uma vez | FA03, CA13 |
| CT07 | Positivo | Cancelamento antes de pagar | `DELETE /orders/{id}` em pedido `PENDING` | Pedido `CANCELLED`; unidades e cupom devolvidos | FA04 |
| CT08 | Negativo | Quantidade inválida | `quantity` = 0 | 400; nenhum pedido criado | FE01 |
| CT09 | Negativo | Limite por pedido excedido | Limite 4, pedido de 5 | 400; nenhum pedido criado | FE04, CA06 |
| CT10 | Negativo | Itens de eventos diferentes | Tipos de dois eventos | 400; nenhum pedido criado | FE03 |
| CT11 | Negativo | Sem token | Requisição sem `Authorization` | 401 | FE09, CA07 |
| CT12 | Negativo | Evento cancelado | Tipo de ingresso de evento cancelado | 404; nenhum pedido criado | FE02, CA08 |
| CT13 | Negativo | Pedido de outro cliente | `orderId` de outro usuário | 404 | FE11, RN05 |
| CT14 | Negativo | Cupom inválido | Código inexistente | 422; nenhuma unidade reservada | FE07 |
| CT15 | Negativo | Pedido já pago | Segundo `POST /orders/{id}/payment` | 409 | FE13 |
| CT16 | Exceção | Concorrência na última unidade | Vários pedidos simultâneos para 1 unidade | 1 pedido criado; os demais recebem 409 | FE05, CA04 |
| CT17 | Exceção | Concorrência no último uso do cupom | Dois pedidos simultâneos com o mesmo cupom | 1 pedido com desconto; o outro recebe 422 | FE07, CA14 |
| CT18 | Exceção | Expiração da reserva | Pedido sem pagamento por 15 minutos | Pedido `EXPIRED`; unidades e cupom devolvidos; pagamento posterior recebe 409 | FE12, CA03, CA05 |
| CT19 | Exceção | Falha durante a emissão | Erro simulado na emissão | Nada é gravado pela metade; pedido segue `PENDING` | FE15, RNF05 |

CT16, CT17 e CT19 devem ser testes de integração com PostgreSQL real (Testcontainers), porque dependem do comportamento de bloqueio e de transação do banco.

## 16. Resumo do Caso de Uso

| Item | Resumo |
| --- | --- |
| Caso de uso | UC01 – Realizar compra de ingresso |
| Ator principal | Cliente (qualquer perfil autenticado) |
| Atores secundários | Gateway de pagamento simulado, rotina de expiração |
| Entrada | Tipos de ingresso e quantidades de um evento, cupom opcional e chave de idempotência |
| Saída | Pedido `PAID` e ingressos `VALID` com código único |
| Endpoints | `GET /events/{id}`, `GET /events/{id}/ticket-types`, `POST /events/{id}/coupons/preview`, `POST /orders`, `POST /orders/{id}/payment`, `DELETE /orders/{id}`, `GET /orders`, `GET /orders/{id}` |
| Pontos críticos | Reserva por 15 minutos sem venda acima do estoque, cupom sem uso acima do limite, pedido único por chave de idempotência e emissão somente após a aprovação |
| Inclui | UC02 Autenticar usuário, UC03 Reservar ingressos, UC04 Processar pagamento, UC05 Emitir ingressos |
| Estende | UC06 Aplicar cupom |
| Pendências | I08 (compra de evento já iniciado), Q08 (pagamento após expiração, com o provedor real) |

O cliente escolhe os ingressos de um evento ativo, o Ticketfy reserva as unidades por 15 minutos e cria o pedido, aplicando o cupom se houver. O pagamento simulado é aprovado na hora e os ingressos são emitidos na mesma transação; pedidos de valor zero são confirmados na criação. Se o pedido não for pago no prazo, a reserva expira e as unidades e o cupom voltam à disponibilidade.
