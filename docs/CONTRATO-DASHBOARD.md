# Contrato do dashboard para o frontend

Endpoints do dashboard do organizador (Tarefa 3) e a mudança de contrato da rota pública de lotes (Tarefa 2).
Os exemplos são respostas reais geradas a partir da fixture dos testes de integração (`DashboardIntegrationTest`), com ids encurtados.

> Nomes de campos são estáveis: qualquer mudança precisa ser combinada com o frontend antes.

## Regras comuns

| Assunto | Regra |
|---|---|
| Autenticação | `Authorization: Bearer <token>` |
| Dinheiro | Número JSON com 2 casas, mesmo formato de `price`: `430.00` |
| Percentuais | 0 a 100, 2 casas (`3.33`); `null` quando o denominador é 0 |
| Médias | `null` quando não há pedido pago |
| Data/hora | `Instant` em UTC: `"2026-09-02T15:00:00Z"` |
| `dailySales.date` | `LocalDate` (`"2026-08-31"`) no fuso informado em `timeZone` (configurável por `ticketfy.dashboard.time-zone` / `DASHBOARD_TIME_ZONE`, padrão `America/Sao_Paulo`) |
| Paginação | `?page=0&size=20` (máximo 100, valores acima são reduzidos a 100). Resposta no mesmo formato de `GET /events`: `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }` |
| Pedido com itens de vários eventos | Cada evento enxerga só os próprios itens: números, listagem e `total` |

### Erros (todos com corpo `{ "message": "..." }`)

| Código | Quando | `message` |
|---|---|---|
| 401 | Sem token ou token inválido | `Authentication required` |
| 403 | Usuário com papel `USER` | `Access denied` |
| 403 | Organizador que não é dono do evento | `You do not own this event` |
| 404 | Evento inexistente ou desativado | `Event not found` |
| 400 | `status` inválido (somente em `GET /events/{id}/orders`) | `Invalid value for parameter 'status'` |

---

## 1. `GET /events/{id}/dashboard`

Resumo de vendas de um evento.

- **Acesso:** dono do evento ou ADMIN.
- **Respostas:** 200, 401, 403, 404.

```json
{
  "eventId": "652d147a-…",
  "eventName": "Show de Rock",
  "startsAt": "2026-10-17T01:38:53.352870Z",
  "timeZone": "America/Sao_Paulo",
  "generatedAt": "2026-10-07T01:38:53.525836895Z",
  "totals": {
    "ticketsSold": 4,
    "ticketsReserved": 2,
    "capacity": 120,
    "remaining": 114,
    "percentSold": 3.33,
    "revenue": 430.00,
    "paidOrders": 2,
    "averageOrderValue": 215.00,
    "averageTicketPrice": 107.50
  },
  "ordersByStatus": [
    { "status": "PENDING",   "orders": 1, "tickets": 2 },
    { "status": "PAID",      "orders": 2, "tickets": 4 },
    { "status": "CANCELLED", "orders": 1, "tickets": 1 },
    { "status": "EXPIRED",   "orders": 1, "tickets": 3 },
    { "status": "REFUNDED",  "orders": 1, "tickets": 1 }
  ],
  "checkIn": { "checkedIn": 1, "issued": 4, "attendanceRate": 25.00 },
  "ticketTypes": [
    {
      "id": "661e1eb3-…", "name": "Pista", "price": 80.00, "quantityTotal": 100,
      "sold": 3, "reserved": 0, "remaining": 97, "revenue": 230.00, "percentSold": 3.00
    },
    {
      "id": "ff9f5ec3-…", "name": "VIP", "price": 200.00, "quantityTotal": 20,
      "sold": 1, "reserved": 2, "remaining": 17, "revenue": 200.00, "percentSold": 5.00
    }
  ],
  "dailySales": [
    { "date": "2026-08-31", "tickets": 3, "revenue": 360.00 },
    { "date": "2026-09-01", "tickets": 0, "revenue": 0.00 },
    { "date": "2026-09-02", "tickets": 1, "revenue": 70.00 }
  ]
}
```

No exemplo, a receita de 430.00 é 2 × 80 + 200 de um pedido mais 70 de outro (preço congelado em 70, embora o lote custe 80 hoje). Pedidos cancelados, expirados e reembolsados ficam fora, assim como os itens de outro evento que estão no mesmo pedido. O dia 2026-09-01 aparece zerado porque o único pagamento aprovado nesse dia é de um pedido depois reembolsado. O pagamento das 02:30 UTC de 01/09 conta em 31/08, por causa do fuso de São Paulo.

**Evento sem vendas:** contadores 0, `revenue: 0.00`, `percentSold: null` se não houver lotes, `averageOrderValue`, `averageTicketPrice` e `attendanceRate` em `null`, `ordersByStatus` com os 5 status zerados, `ticketTypes` com os lotes existentes e `dailySales: []`.

### Regras dos campos

| Campo | O que entra | O que fica fora |
|---|---|---|
| `totals.ticketsSold` | Ingressos dos itens deste evento em pedidos `PAID` | Todos os outros status |
| `totals.ticketsReserved` | Ingressos dos itens deste evento em pedidos `PENDING` | — |
| `totals.capacity` | Soma do `quantityTotal` de todos os lotes do evento | — |
| `totals.remaining` | `capacity − quantity_sold` (o `quantity_sold` já inclui reservas pendentes) | — |
| `totals.percentSold` | `ticketsSold / capacity × 100` | Reservados |
| `totals.revenue` | Soma de `unitPrice × quantity` dos itens `PAID` deste evento, pelo preço congelado no item | `PENDING`, `CANCELLED`, `EXPIRED`, `REFUNDED` e o preço atual do lote |
| `totals.paidOrders` | Pedidos `PAID` com pelo menos um item deste evento | — |
| `totals.averageOrderValue` | `revenue / paidOrders` | — |
| `totals.averageTicketPrice` | `revenue / ticketsSold` | — |
| `ordersByStatus` | Sempre os 5 status, na ordem `PENDING, PAID, CANCELLED, EXPIRED, REFUNDED`. `orders` são pedidos distintos com itens do evento; `tickets` são os ingressos desses itens | — |
| `checkIn.checkedIn` | Ingressos `USED` dos lotes do evento | — |
| `checkIn.issued` | Ingressos `VALID` + `USED` | `CANCELLED` (reembolsos) |
| `checkIn.attendanceRate` | `checkedIn / issued × 100` | — |
| `ticketTypes[].price` | Preço **atual** do lote | — |
| `ticketTypes[].sold` / `reserved` / `revenue` | Mesmas regras dos totais, por lote; inclui lotes sem venda | — |
| `ticketTypes[].remaining` | `quantityTotal − quantity_sold` | — |
| `ticketTypes[].percentSold` | `sold / quantityTotal × 100` | — |
| `dailySales` | Pedidos `PAID`, agrupados pela data de aprovação do pagamento no fuso `timeZone`. Série contínua do primeiro ao último dia com venda; dias sem venda com 0 | Pedidos reembolsados, mesmo com pagamento aprovado |
| `generatedAt` | Momento em que a resposta foi calculada | — |

---

## 2. `GET /events/{id}/orders`

Pedidos que contêm itens do evento.

- **Acesso:** dono do evento ou ADMIN.
- **Respostas:** 200, 400 (`status` inválido), 401, 403, 404.
- **Parâmetros (opcionais):**
  - `status`: `PENDING`, `PAID`, `CANCELLED`, `EXPIRED` ou `REFUNDED`.
  - `q`: busca parcial, sem diferenciar maiúsculas, no nome ou e-mail do comprador (`%` e `_` são tratados como texto).
  - `page`, `size`: padrão 20, máximo 100.
- **Ordem:** pedido mais recente primeiro (`createdAt` decrescente). O parâmetro `sort` é ignorado.

Exemplo `?page=1&size=2`:

```json
{
  "content": [
    {
      "orderId": "06e6da18-…",
      "status": "PENDING",
      "createdAt": "2026-09-03T10:00:00Z",
      "paidAt": null,
      "buyer": { "name": "João Souza", "email": "joao.886aafc9@mail.com" },
      "items": [
        { "ticketTypeId": "ff9f5ec3-…", "ticketTypeName": "VIP", "quantity": 2, "unitPrice": 200.00, "subtotal": 400.00 }
      ],
      "total": 400.00
    },
    {
      "orderId": "55d8e211-…",
      "status": "PAID",
      "createdAt": "2026-09-02T14:50:00Z",
      "paidAt": "2026-09-02T15:00:00Z",
      "buyer": { "name": "João Souza", "email": "joao.886aafc9@mail.com" },
      "items": [
        { "ticketTypeId": "661e1eb3-…", "ticketTypeName": "Pista", "quantity": 1, "unitPrice": 70.00, "subtotal": 70.00 }
      ],
      "total": 70.00
    }
  ],
  "page": { "size": 2, "number": 1, "totalElements": 6, "totalPages": 3 }
}
```

O segundo pedido também tem 2 ingressos de outro evento (100.00). Eles não aparecem aqui e não entram no `total`.

| Campo | Regra |
|---|---|
| `paidAt` | Data de aprovação do pagamento; `null` se não houver pagamento aprovado |
| `buyer` | Somente `name` e `email`. Nenhum outro dado pessoal, nem o id do comprador |
| `items` | Somente os itens deste evento, com o `unitPrice` congelado na compra |
| `items[].subtotal` | `unitPrice × quantity` |
| `total` | Soma dos `subtotal` deste evento (não é o total do pedido) |

---

## 3. `GET /events/{id}/ticket-types/manage`

Lotes com dados internos, para a tela de gestão.

- **Acesso:** dono do evento ou ADMIN.
- **Respostas:** 200, 401, 403, 404.
- Traz todos os lotes do evento, inclusive inativos, do mais antigo para o mais novo.

```json
[
  {
    "id": "661e1eb3-…", "name": "Pista", "description": "Lote Pista", "price": 80.00,
    "quantityTotal": 100, "quantitySold": 3, "available": 97, "maxPerOrder": 4, "active": true
  },
  {
    "id": "ff9f5ec3-…", "name": "VIP", "description": "Lote VIP", "price": 200.00,
    "quantityTotal": 20, "quantitySold": 3, "available": 17, "maxPerOrder": 4, "active": true
  }
]
```

| Campo | Regra |
|---|---|
| `quantitySold` | Valor do banco: ingressos pagos + reservados em pedidos pendentes |
| `available` | `quantityTotal − quantitySold` |

A resposta de `POST /events/{id}/ticket-types` usa o mesmo formato (ganhou o campo `quantitySold`).

---

## 4. `GET /organizer/dashboard`

Visão geral dos eventos do usuário logado.

- **Acesso:** ORGANIZER ou ADMIN; cada um vê só os próprios eventos.
- **Respostas:** 200, 401, 403 (papel `USER`).
- **Parâmetros:** `page`, `size` (padrão 20, máximo 100).
- **Ordem:** `startsAt` crescente.

```json
{
  "totals": { "events": 2, "ticketsSold": 6, "revenue": 530.00 },
  "events": {
    "content": [
      {
        "eventId": "652d147a-…", "name": "Show de Rock", "startsAt": "2026-10-17T01:38:53.352870Z",
        "imageUrl": "https://cdn.example.com/show-de-rock.jpg",
        "ticketsSold": 4, "capacity": 120, "percentSold": 3.33, "revenue": 430.00
      },
      {
        "eventId": "48f754ec-…", "name": "Festival de Jazz", "startsAt": "2026-10-27T01:38:53.353995Z",
        "imageUrl": "https://cdn.example.com/festival-de-jazz.jpg",
        "ticketsSold": 2, "capacity": 50, "percentSold": 4.00, "revenue": 100.00
      }
    ],
    "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 }
  }
}
```

| Campo | Regra |
|---|---|
| `totals` | Soma de **todos** os eventos ativos do organizador, não só os da página. `events` é o número de eventos ativos |
| Eventos desativados | Ficam fora da lista e dos totais |
| `events.content[].ticketsSold` / `revenue` | Mesmas regras do dashboard do evento (`PAID`, preço congelado, itens do evento) |
| `events.content[].capacity` | Soma do `quantityTotal` dos lotes do evento |
| `events.content[].percentSold` | `ticketsSold / capacity × 100`; `null` se o evento não tiver lotes |

---

## 5. Mudança de contrato (Tarefa 2): `GET /events/{id}/ticket-types` (pública)

| Antes | Agora |
|---|---|
| `available: 37` (número exato restante) | `soldOut: boolean` |
| — | `remaining: number \| null`: preenchido só quando restam de 1 a 10; `null` acima disso e quando esgotado |
| — | `description: string \| null` |
| — | `maxPerOrder: number \| null` |

```json
[
  {
    "id": "661e1eb3-…", "name": "Pista", "description": "Lote Pista", "price": 80.00,
    "maxPerOrder": 4, "soldOut": false, "remaining": null
  }
]
```

A rota pública não expõe `quantityTotal`, `quantitySold` nem a quantidade exata restante acima de 10.

**Ajustes no frontend:**
- Trocar `available` por `soldOut` para desabilitar a compra.
- Mostrar "Últimos N ingressos" quando `remaining != null`.
- Limitar o seletor de quantidade a `maxPerOrder`, quando preenchido.
