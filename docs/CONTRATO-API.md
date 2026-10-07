# Mudanças de contrato da API

Registro das mudanças que exigem ajuste no frontend, da mais recente para a mais antiga.
O contrato completo do dashboard do organizador está em [CONTRATO-DASHBOARD.md](CONTRATO-DASHBOARD.md).

## Lotes: edição (Tarefa 11)

**Novo endpoint `PATCH /events/{eventId}/ticket-types/{ticketTypeId}`.** A edição é parcial: só mudam os campos enviados, e `null` ou ausente mantém o valor atual.

- **Acesso:** dono do evento ou ADMIN.

| Campo | Regras |
|---|---|
| `name` | Até 150 caracteres, não pode ser só espaços. Não pode repetir o nome de outro lote do evento (409) |
| `description` | Até 500 caracteres. `""` ou só espaços remove a descrição |
| `price` | Maior que zero. **Não afeta pedidos já feitos**, que mantêm o `unitPrice` da compra. Vale para os pedidos novos |
| `quantityTotal` | Maior que zero e **não pode ficar abaixo do vendido + reservado** (`quantitySold`) |
| `maxPerOrder` | Maior que zero. **Não dá para remover o limite** por esta rota: `null` mantém o valor atual |

Exemplo de corpo:

```json
{ "name": "Pista Premium", "price": 120.50, "quantityTotal": 150 }
```

**Respostas:**

| Código | Quando | Corpo |
|---|---|---|
| 200 | Lote atualizado | Mesmo formato de `GET /events/{id}/ticket-types/manage`, com os valores atuais do banco (`quantityTotal`, `quantitySold`, `available`) |
| 400 | `quantityTotal` abaixo do vendido + reservado | `{ "message": "quantityTotal cannot be lower than the 37 tickets already sold or reserved" }` |
| 400 | Validação de campo | `{ "message": "Validation failed", "errors": [ { "field", "message" } ] }` |
| 401 | Sem token | `{ "message": "Authentication required" }` |
| 403 | Não é dono do evento nem ADMIN | `{ "message": "You do not own this event" }` ou `{ "message": "Access denied" }` |
| 404 | Evento inexistente ou desativado | `{ "message": "Event not found" }` |
| 404 | Lote inexistente ou de outro evento | `{ "message": "Ticket type not found" }` |
| 409 | Nome já usado por outro lote do evento | `{ "message": "A ticket type with this name already exists for this event" }` |

**Outras regras:**
- Se qualquer regra falhar, nada é alterado, nem os campos válidos enviados junto.
- Lotes de eventos desativados não podem ser editados.

## Lotes: nome duplicado responde 409

`POST /events/{id}/ticket-types` e, desde a Tarefa 11, também a edição. Antes, esse caso respondia **500**.

Não podem existir dois lotes com o mesmo nome no mesmo evento. A comparação diferencia maiúsculas de minúsculas, como a constraint do banco. Tentar criar um nome repetido responde:

```http
409 Conflict
{ "message": "A ticket type with this name already exists for this event" }
```

O mesmo nome em eventos diferentes continua permitido.

## Eventos em destaque (Tarefa 9)

**Novo campo `featured` (boolean)** em:
- `GET /events` e `GET /events/mine`, no resumo de cada evento;
- `GET /events/{id}`, no detalhe.

Eventos novos começam com `false`.

**Novo filtro em `GET /events`:** `featured`, opcional.

| `featured` | Resultado |
|---|---|
| ausente | todos os eventos ativos (como antes) |
| `true` | só os eventos em destaque |
| `false` | só os eventos que não estão em destaque |
| outro valor | 400 `{ "message": "Invalid value for parameter 'featured'" }` |

O filtro combina com `q` e `city`.

**Novo endpoint `PATCH /events/{id}/featured`:**
- **Acesso:** somente ADMIN. O organizador, mesmo sendo dono do evento, recebe 403.
- **Corpo:** `{ "featured": true }` ou `{ "featured": false }`. O campo é obrigatório.
- **Respostas:**
  - 200 com o mesmo corpo de `GET /events/{id}`, já com o novo valor de `featured`;
  - 400 sem `featured`;
  - 401 sem token;
  - 403 para quem não é ADMIN;
  - 404 para evento inexistente ou desativado.

O `PUT /events/{id}` não altera `featured`.

## CORS: PATCH liberado e `Retry-After` exposto (Tarefa 8)

**Origens permitidas:** vêm de `CORS_ALLOWED_ORIGINS`, uma lista separada por vírgula. O padrão é `http://localhost:3000,http://localhost:5173`.
- A comparação é exata: para produção, inclua o domínio da Vercel, por exemplo `https://ticketfy.vercel.app`.
- Deploys de preview, com subdomínio aleatório, não são aceitos.

**Métodos:** `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- O `PATCH` foi liberado agora. Antes, o navegador bloqueava `PATCH /users/me/avatar` no preflight.

**Cabeçalhos aceitos na requisição:** `Authorization`, `Content-Type`, `Idempotency-Key`.

**Cabeçalhos expostos ao JavaScript:** `Retry-After`.

### 429: limite de tentativas de login

Depois de `LOGIN_MAX_ATTEMPTS` tentativas (padrão 5) em `LOGIN_WINDOW_SECONDS` (padrão 60) vindas do mesmo IP, o `POST /login` responde:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 42
Access-Control-Expose-Headers: Retry-After
Content-Type: application/json

{ "message": "Too many login attempts. Try again later." }
```

`Retry-After` traz os segundos até a janela reabrir, no mínimo 1. O frontend pode ler esse valor com `response.headers.get("Retry-After")` e mostrar "tente novamente em N segundos".

## Erros de corpo da requisição: 400 e 415 (Tarefa 7)

Valem para todos os endpoints que recebem corpo JSON. Antes, esses casos respondiam **500**.

| Situação | Código | Corpo |
|---|---|---|
| JSON malformado, corpo ausente ou campo com tipo errado (ex.: `"quantity": "abc"`, data inválida) | 400 | `{ "message": "Malformed request body" }` |
| `Content-Type` diferente de `application/json` (ex.: `text/plain`) | 415 | `{ "message": "Unsupported media type" }` |

- A resposta nunca traz detalhes do parser (linha, coluna, tipo esperado).
- Erros de validação de campos (`@NotBlank`, `@Size`, `@URL`...) continuam com o formato de sempre: 400 com `{ "message": "Validation failed", "errors": [ { "field", "message" } ] }`.

**Login:** e-mail inexistente e senha errada respondem igual, 401 `{ "message": "Invalid credentials" }`. Isso já funcionava assim e agora tem teste.

## Eventos: `imageUrl` na criação e na edição (Tarefa 4)

`POST /events` e `PUT /events/{id}`.

| `imageUrl` no corpo | `POST /events` | `PUT /events/{id}` |
|---|---|---|
| ausente ou `null` | evento sem foto | **mantém** a foto atual |
| `""` ou só espaços | evento sem foto (201) | **remove** a foto (200) |
| URL `https://` válida | salva | substitui a foto |
| URL `http://` ou inválida | 400 | 400, a foto atual é mantida |

**Antes:**
- `"imageUrl": ""` respondia **500**, tanto na criação quanto na edição.
- Um `PUT` sem `imageUrl` **apagava** a foto.

**Ajuste no frontend:**
- Para remover a foto, envie `"imageUrl": ""`.
- Omitir o campo, ou mandar `null`, agora mantém a foto atual, como já acontece com os outros campos do `PUT`.

## Lotes: rota pública `GET /events/{id}/ticket-types` (Tarefa 2)

`available` foi substituído por `soldOut` e `remaining`, e a resposta ganhou `description` e `maxPerOrder`. Detalhes na seção 5 de [CONTRATO-DASHBOARD.md](CONTRATO-DASHBOARD.md#5-mudança-de-contrato-tarefa-2-get-eventsidticket-types-pública).
