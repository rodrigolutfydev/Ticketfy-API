# Referência da API do Ticketfy

Esta é a referência de uso da API REST do Ticketfy: autenticação, formato de erros, paginação, idempotência e todos os endpoints, agrupados por assunto. As decisões por trás de cada regra estão no [documento de arquitetura](05%20-%20Documento%20de%20Arquitetura.md).

O contrato gerado a partir do código fica em [`openapi.json`](openapi.json). O build atualiza esse arquivo, e o CI falha se a versão commitada estiver desatualizada.

## Sumário

- [Visão geral](#visão-geral)
- [Autenticação](#autenticação)
- [Erros](#erros)
- [Paginação e ordenação](#paginação-e-ordenação)
- [Idempotência](#idempotência)
- [Limites de tentativas](#limites-de-tentativas)
- [Endpoints](#endpoints)
  - [Usuários e conta](#usuários-e-conta)
  - [Privacidade (LGPD)](#privacidade-lgpd)
  - [Eventos](#eventos)
  - [Lotes](#lotes)
  - [Cupons](#cupons)
  - [Pedidos e pagamento](#pedidos-e-pagamento)
  - [Ingressos e transferência](#ingressos-e-transferência)
  - [Painel do organizador](#painel-do-organizador)
  - [Financeiro do organizador](#financeiro-do-organizador)
  - [Administração](#administração)
  - [Saúde](#saúde)

## Visão geral

| Item | Valor |
|---|---|
| Produção | `https://ticketfy-api.onrender.com` |
| Local | `http://localhost:8080` |
| Swagger UI | `/swagger-ui/index.html`, só no perfil `dev` (desligado em produção) |
| Formato | JSON em UTF-8; erros em `application/problem+json` |
| Datas | ISO 8601 em UTC: `"2026-12-10T23:00:00Z"` é 20h em Brasília. Filtros por dia (`from`, `to`) usam `AAAA-MM-DD` no fuso `America/Sao_Paulo` |
| Dinheiro | Número decimal com 2 casas, em reais: `80.00` |
| Identificadores | UUID |
| Rastreio | Toda resposta traz `X-Request-Id`. O cliente pode enviar o próprio (letras, números e hífen, até 64 caracteres) |

Na coluna **Acesso** das tabelas de endpoints:

- **Público**: sem token.
- **Autenticado**: qualquer usuário logado.
- **ORGANIZER**, **ADMIN**: exige o papel.
- **Dono**: o recurso precisa pertencer ao usuário. Pedido ou ingresso de outra pessoa responde 404, sem revelar que existe.

## Autenticação

### Access token

`POST /auth/login` devolve um access token JWT que vale **10 minutos**. Rotas protegidas exigem o header:

```http
Authorization: Bearer <token>
```

A cada requisição, a API confere no banco a sessão que emitiu o token. Logout, troca de senha e exclusão da conta derrubam o token na hora, sem esperar ele vencer.

### Refresh token em cookie

O refresh token nunca aparece no corpo da resposta. Ele vai no cookie `__Host-ticketfy_rt` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`), vale **30 minutos sem uso** e é trocado a cada renovação. A sessão dura no máximo **12 horas**. Reapresentar um refresh token já usado encerra a sessão inteira.

| Rota | O que faz |
|---|---|
| `POST /auth/login` | Confere e-mail e senha, abre uma sessão, devolve o access token e grava o cookie |
| `POST /auth/refresh` | Troca o cookie por um novo e devolve outro access token; sem sessão válida, 401 `session-expired` e o cookie é apagado |
| `POST /auth/logout` | Encerra a sessão do cookie e apaga o cookie; responde 204 mesmo sem cookie |
| `POST /auth/logout-all` | Encerra todas as sessões do usuário; exige também `Authorization` |

### `Origin` e `X-Ticketfy-Auth`

Todo `POST /auth/**` exige:

- o header `Origin` idêntico a uma das origens de `CORS_ALLOWED_ORIGINS`;
- o header `X-Ticketfy-Auth: 1`.

Sem isso, a resposta é 403 `auth-request-rejected`. O frontend publicado chama essas rotas pelo próprio domínio, por um proxy em Cloudflare Pages Functions que repassa o IP real em `X-Client-IP`; a API só confia nesse header quando ele vem com o segredo `X-Proxy-Secret`.

**Login**

```http
POST /auth/login
Origin: http://localhost:5173
X-Ticketfy-Auth: 1
Content-Type: application/json

{
  "email": "ana@email.com",
  "password": "senhaForte123"
}
```

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Set-Cookie: __Host-ticketfy_rt=<refresh token>; Path=/; Max-Age=1800; Secure; HttpOnly; SameSite=Strict

{
  "token": "<access token JWT>",
  "expiresIn": 600
}
```

E-mail ou senha errados respondem 401 `invalid-credentials`. Cinco tentativas por IP em 60 segundos levam a 429 `too-many-login-attempts`.

**Renovar**

```http
POST /auth/refresh
Origin: http://localhost:5173
X-Ticketfy-Auth: 1
Cookie: __Host-ticketfy_rt=<refresh token>
```

A resposta tem o mesmo formato do login e grava um cookie novo.

### Confirmação de senha

Ações sensíveis pedem a senha de novo no corpo (`password` ou `currentPassword`):

- trocar senha;
- transferir ingresso;
- salvar dados de recebimento;
- pedir saque;
- baixar os próprios dados;
- excluir a conta.

Senha errada responde 403 `invalid-password`. Cinco erros em 15 minutos bloqueiam a confirmação com 429 `too-many-password-attempts` e `Retry-After`.

## Erros

Toda resposta de erro segue a RFC 7807 (`application/problem+json`):

| Campo | Conteúdo |
|---|---|
| `type` | URI estável do tipo de erro: `https://ticketfy-api.onrender.com/problems/<slug>`. Use o slug para decidir o que mostrar |
| `title` | Resumo fixo do tipo |
| `status` | Código HTTP |
| `detail` | Mensagem em inglês, específica da ocorrência. Não é traduzida pela API |
| `instance` | Caminho da requisição |

Alguns tipos trazem propriedades extras, listadas na tabela abaixo. Erros 5xx trazem `requestId`, igual ao header `X-Request-Id`. Respostas 429 trazem o header `Retry-After` em segundos.

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://ticketfy-api.onrender.com/problems/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Not enough tickets available for this ticket type",
  "instance": "/orders"
}
```

Erros de validação trazem `errors`, com um item por campo:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://ticketfy-api.onrender.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/users",
  "errors": [
    { "field": "password", "message": "Password must be at least 8 characters long" }
  ]
}
```

### Tipos de erro

| Slug | Status | Quando acontece |
|---|---|---|
| `validation-failed` | 400 | Campo inválido no corpo; ver `errors` |
| `malformed-request-body` | 400 | JSON malformado ou com tipo errado |
| `missing-parameter` | 400 | Parâmetro de query obrigatório ausente |
| `missing-header` | 400 | Header obrigatório ausente |
| `invalid-parameter` | 400 | Parâmetro com valor inválido: UUID, enum, data, página, `sort`, intervalo de datas ou `Idempotency-Key` |
| `bad-request` | 400 | Requisição inválida sem tipo mais específico |
| `invalid-event-dates` | 400 | Fim do evento não é depois do início |
| `invalid-ticket-type-quantity` | 400 | Quantidade total do lote abaixo do vendido e reservado |
| `mixed-events-order` | 400 | Pedido com lotes de eventos diferentes |
| `max-per-order-exceeded` | 400 | Quantidade acima do limite por pedido do lote |
| `self-transfer` | 400 | Transferência para o próprio e-mail |
| `invalid-coupon-settings` | 400 | Percentual acima de 100, fim antes do início ou limite abaixo dos usos |
| `pix-key-not-found` | 400 | Chave Pix não encontrada no provedor |
| `pix-key-holder-mismatch` | 400 | Chave Pix de outro documento |
| `payout-below-minimum` | 400 | Saque abaixo do mínimo; traz `minAmount` |
| `email-not-allowed` | 400 | Cadastro com domínio reservado (`deleted.ticketfy.invalid`) |
| `invalid-credentials` | 401 | E-mail ou senha errados no login |
| `authentication-required` | 401 | Rota protegida sem token ou com token inválido |
| `session-expired` | 401 | Sessão vencida, revogada ou conta excluída |
| `access-denied` | 403 | Papel insuficiente |
| `event-access-denied` | 403 | O evento não é do usuário |
| `auth-request-rejected` | 403 | `POST /auth/**` sem `Origin` permitido ou sem `X-Ticketfy-Auth: 1` |
| `invalid-password` | 403 | Senha de confirmação errada |
| `user-not-found` | 404 | Usuário não encontrado |
| `event-not-found` | 404 | Evento inexistente ou desativado |
| `ticket-type-not-found` | 404 | Lote inexistente ou fora de venda |
| `order-not-found` | 404 | Pedido inexistente ou de outra pessoa |
| `ticket-not-found` | 404 | Ingresso inexistente ou de outra pessoa |
| `coupon-not-found` | 404 | Cupom inexistente |
| `payout-account-not-found` | 404 | Organizador sem dados de recebimento |
| `payout-not-found` | 404 | Saque inexistente ou de outro organizador |
| `organizer-not-found` | 404 | Nenhum organizador com o e-mail ou id informado |
| `resource-not-found` | 404 | Rota inexistente |
| `method-not-allowed` | 405 | Método não suportado; traz o header `Allow` |
| `email-already-exists` | 409 | E-mail já cadastrado |
| `invalid-role-change` | 409 | Conta já é organizadora ou administradora |
| `ticket-type-name-already-exists` | 409 | Nome de lote repetido no evento |
| `invalid-order-state` | 409 | Pedido no estado errado para a ação, prazo de reembolso encerrado ou ingresso já usado |
| `insufficient-stock` | 409 | Estoque insuficiente no lote |
| `order-has-transferred-tickets` | 409 | Reembolso de pedido com ingresso transferido |
| `invalid-payment-state` | 409 | Pedido já pago |
| `invalid-ticket-state` | 409 | Ingresso não está válido para a ação |
| `ticket-transfer-closed` | 409 | Transferência depois do início do evento |
| `ticket-transfer-limit-reached` | 409 | Ingresso chegou ao limite de transferências |
| `invalid-event-state` | 409 | Evento cancelado ou já encerrado |
| `event-has-sales` | 409 | Exclusão de evento com ingressos vendidos ou reservados |
| `payout-account-required` | 409 | Saque sem dados de recebimento cadastrados |
| `payout-account-cooldown` | 409 | Saque nas 48 h depois de trocar a chave Pix; traz `blockedUntil` |
| `payout-in-progress` | 409 | Já existe saque em análise, solicitado ou em processamento |
| `negative-available-balance` | 409 | Saldo disponível negativo; traz `available` |
| `payout-exceeds-available` | 409 | Saque acima do disponível; traz `available` |
| `invalid-payout-state` | 409 | Saque no estado errado para a ação |
| `payouts-blocked` | 409 | Saques do organizador bloqueados |
| `invalid-payout-block-state` | 409 | Bloquear quem já está bloqueado, ou desbloquear quem não está |
| `coupon-code-already-exists` | 409 | Código de cupom repetido no evento |
| `coupon-already-used` | 409 | Mudar tipo ou valor, ou excluir, cupom já usado |
| `admin-account-deletion` | 409 | Exclusão de conta ADMIN |
| `account-has-payout-block` | 409 | Exclusão com saques bloqueados |
| `account-has-payout-in-progress` | 409 | Exclusão com saque em andamento |
| `account-has-balance` | 409 | Exclusão com saldo diferente de zero; traz `pending`, `available` e `held` |
| `account-has-active-events` | 409 | Exclusão com evento com vendas ou pedidos pendentes; traz `eventIds` |
| `account-has-upcoming-tickets` | 409 | Exclusão com ingresso válido de evento futuro; traz `ticketCount` |
| `unsupported-media-type` | 415 | `Content-Type` diferente de JSON |
| `transfer-recipient-unavailable` | 422 | Destinatário da transferência não pode receber (mensagem genérica) |
| `invalid-coupon` | 422 | Cupom inválido, expirado, inativo, esgotado ou de outro evento (mesma mensagem para todos) |
| `too-many-login-attempts` | 429 | Limite de login por IP |
| `too-many-password-attempts` | 429 | Limite de senha de confirmação errada |
| `too-many-transfer-attempts` | 429 | Limite de destinatários inexistentes |
| `too-many-coupon-attempts` | 429 | Limite de cupons inválidos |
| `too-many-data-exports` | 429 | Limite de exportações de dados |
| `internal-error` | 500 | Erro inesperado; `detail` é sempre `"Internal server error"` e traz `requestId` |

## Paginação e ordenação

Listagens paginadas aceitam:

| Parâmetro | Padrão | Regra |
|---|---|---|
| `page` | `0` | Começa em zero |
| `size` | `20` | Máximo 100; valores maiores são reduzidos para 100 |
| `sort` | Depende da rota | `campo,asc` ou `campo,desc`; pode repetir. Campo inexistente ou com `.` responde 400 `invalid-parameter` |

`sort` vale nas listagens de eventos, pedidos e ingressos. Extrato, auditoria, saques e pedidos do evento têm ordem fixa (mais recente primeiro) e ignoram `sort`.

Formato da resposta:

```json
{
  "content": [ ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

## Idempotência

Duas rotas aceitam o header opcional `Idempotency-Key` (1 a 100 caracteres; recomendado um UUID por tentativa):

| Rota | Comportamento |
|---|---|
| `POST /orders` | Repetir a chave devolve o mesmo pedido em vez de criar outro. A chave usada por outro usuário responde 404 `order-not-found` |
| `POST /organizer/payouts` | Repetir a chave do mesmo organizador devolve o mesmo saque. Sem chave, um segundo saque enquanto o primeiro está em andamento responde 409 `payout-in-progress` |

## Limites de tentativas

Os limites ficam em memória, por instância da aplicação, exceto o de exportação, que é contado pela auditoria.

| O que | Limite | Resposta |
|---|---|---|
| Login | 5 por IP em 60 s | 429 `too-many-login-attempts` |
| Senha de confirmação errada | 5 por usuário em 15 min | 429 `too-many-password-attempts` |
| Transferência para e-mail não cadastrado | 5 por usuário em 15 min | 429 `too-many-transfer-attempts` |
| Cupom inválido (pré-visualização e pedido somados) | 10 por usuário em 15 min | 429 `too-many-coupon-attempts` |
| Exportação de dados | 3 por usuário em 24 h | 429 `too-many-data-exports` |

## Endpoints

### Usuários e conta

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/users` | Cadastra um usuário com papel `USER` | Público |
| GET | `/users/me` | Dados do usuário autenticado | Autenticado |
| PATCH | `/users/me/avatar` | Troca a foto (`avatarUrl` https); `""` ou `null` remove | Autenticado |
| PATCH | `/users/me/password` | Troca a senha, encerra todas as sessões e abre uma nova | Autenticado |
| POST | `/users/me/organizer` | Torna o usuário organizador; responde 204 e o token atual continua valendo | Autenticado |
| POST | `/auth/login` | Ver [Autenticação](#autenticação) | Público |
| POST | `/auth/refresh` | Ver [Autenticação](#autenticação) | Público (cookie) |
| POST | `/auth/logout` | Ver [Autenticação](#autenticação) | Público (cookie) |
| POST | `/auth/logout-all` | Ver [Autenticação](#autenticação) | Autenticado |

**Cadastrar**

```http
POST /users
Content-Type: application/json

{
  "name": "Ana Souza",
  "email": "ana@email.com",
  "password": "senhaForte123"
}
```

```http
HTTP/1.1 201 Created

{
  "id": "3f0b6c1e-8a5d-4f7e-9c2b-1d4e5f6a7b8c",
  "name": "Ana Souza",
  "email": "ana@email.com",
  "role": "USER",
  "avatarUrl": null
}
```

A senha tem de 8 a 72 caracteres. E-mail repetido responde 409 `email-already-exists`.

**Trocar a senha**

```http
PATCH /users/me/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "currentPassword": "senhaForte123",
  "newPassword": "outraSenhaForte456"
}
```

A resposta tem o mesmo formato do login e grava um cookie novo. As outras sessões são encerradas.

### Privacidade (LGPD)

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/users/me/data-export` | Baixa um JSON com os dados do próprio usuário | Autenticado |
| POST | `/users/me/deletion` | Exclui a conta por anonimização | Autenticado |

**Exportar os próprios dados**

```http
POST /users/me/data-export
Authorization: Bearer <token>
Content-Type: application/json

{ "password": "senhaForte123" }
```

```http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Disposition: attachment; filename="ticketfy-meus-dados-2026-10-08.json"
Cache-Control: no-store

{
  "schemaVersion": 1,
  "generatedAt": "2026-10-08T17:00:00Z",
  "profile": { "id": "…", "name": "Ana Souza", "email": "ana@email.com", "role": "ORGANIZER", "avatarUrl": null, "createdAt": "…", "updatedAt": "…" },
  "orders": [ { "id": "…", "status": "PAID", "subtotalAmount": 100.00, "discountAmount": 10.00, "totalAmount": 90.00, "couponCode": "AMIGO10", "event": { "id": "…", "name": "Festival de Verão", "startsAt": "…" }, "items": [ ], "payments": [ ] } ],
  "tickets": [ ],
  "transfers": [ { "ticketId": "…", "direction": "SENT", "event": { }, "ticketTypeName": "Pista", "transferredAt": "…" } ],
  "organizer": { "events": [ ], "balance": { }, "ledger": [ ], "payouts": [ ], "payoutAccount": { } }
}
```

Regras da exportação:

- `organizer` só aparece para organizadores ou para quem tem eventos.
- Documento e chave Pix saem completos.
- Dados de outras pessoas não entram. Numa transferência aparecem só direção, data, evento e lote. O ingresso enviado não sai com o código, e os eventos trazem só totais de vendas, sem compradores.
- Limite de 3 exportações a cada 24 h.

**Excluir a conta**

```http
POST /users/me/deletion
Authorization: Bearer <token>
Content-Type: application/json

{
  "password": "senhaForte123",
  "confirmation": "EXCLUIR"
}
```

Responde 204 e apaga o cookie. Numa única transação, a exclusão:

- substitui nome, e-mail, foto e senha por valores sem identificação;
- apaga os dados de recebimento;
- cancela pedidos pendentes;
- desativa os eventos do usuário;
- revoga todas as sessões, então o token atual passa a receber 401.

Pedidos, pagamentos, extrato, saques e auditoria continuam existindo, ligados à conta anonimizada, e o e-mail original pode ser usado num novo cadastro.

Sem `confirmation: "EXCLUIR"`, a resposta é 400 `validation-failed`. A exclusão é recusada com 409 nos casos abaixo, verificados nesta ordem:

| Tipo | Motivo |
|---|---|
| `admin-account-deletion` | Conta ADMIN |
| `account-has-payout-block` | Saques bloqueados por um administrador |
| `account-has-payout-in-progress` | Saque em análise, solicitado ou em processamento |
| `account-has-balance` | Saldo a liberar, disponível ou retido diferente de zero |
| `account-has-active-events` | Evento não cancelado com ingresso vendido antes do fim, ou com pedido pendente |
| `account-has-upcoming-tickets` | Ingresso válido de evento não cancelado que ainda não terminou |

### Eventos

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events` | Lista eventos ativos e não cancelados. Filtros `q` (nome), `city`, `featured`, `soldOut`; ordem padrão `startsAt` | Público |
| GET | `/events/{id}` | Detalha um evento ativo | Público |
| GET | `/events/mine` | Eventos ativos do usuário; ordem padrão `startsAt` | Autenticado |
| POST | `/events` | Cria um evento | ORGANIZER ou ADMIN |
| PUT | `/events/{id}` | Atualiza um evento; campos ausentes ficam como estão | Dono ou ADMIN |
| POST | `/events/{id}/cancel` | Cancela o evento e reembolsa os pedidos pagos | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento sem vendas nem reservas | Dono ou ADMIN |
| PATCH | `/events/{id}/featured` | Liga ou desliga o destaque (`{"featured": true}`) | ADMIN |

**Criar evento** (depois de `POST /users/me/organizer`)

```http
POST /events
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Festival de Verão",
  "description": "Três palcos e praça de alimentação",
  "imageUrl": "https://cdn.exemplo.com/festival.jpg",
  "venueName": "Marina da Glória",
  "address": "Av. Infante Dom Henrique, s/n",
  "city": "Rio de Janeiro",
  "state": "RJ",
  "startsAt": "2026-12-10T23:00:00Z",
  "endsAt": "2026-12-11T04:00:00Z"
}
```

```http
HTTP/1.1 201 Created

{
  "id": "8c1d…",
  "name": "Festival de Verão",
  "description": "Três palcos e praça de alimentação",
  "imageUrl": "https://cdn.exemplo.com/festival.jpg",
  "venueName": "Marina da Glória",
  "address": "Av. Infante Dom Henrique, s/n",
  "city": "Rio de Janeiro",
  "state": "RJ",
  "startsAt": "2026-12-10T23:00:00Z",
  "endsAt": "2026-12-11T04:00:00Z",
  "organizerName": "Ana Souza",
  "organizerId": "3f0b…",
  "createdAt": "2026-10-08T17:00:00Z",
  "featured": false,
  "cancelledAt": null,
  "cancellationReason": null
}
```

Regras de imagem e datas:

- `imageUrl` precisa começar com `https://`.
- No `PUT`, `imageUrl` ausente ou `null` mantém a imagem, e `""` remove.
- `startsAt` e `endsAt` precisam estar no futuro, e o fim depois do início.

**Listar eventos**

```http
GET /events?q=festival&city=Rio%20de%20Janeiro&page=0&size=12
```

```json
{
  "content": [
    {
      "id": "8c1d…",
      "name": "Festival de Verão",
      "imageUrl": "https://cdn.exemplo.com/festival.jpg",
      "venueName": "Marina da Glória",
      "city": "Rio de Janeiro",
      "startsAt": "2026-12-10T23:00:00Z",
      "featured": false,
      "minPrice": 80.00,
      "soldOut": false,
      "cancelledAt": null
    }
  ],
  "page": { "size": 12, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

`minPrice` é o menor preço entre os lotes com estoque. Evento sem lote ativo vem com `minPrice: null`.

**Cancelar evento**

```http
POST /events/{id}/cancel
Authorization: Bearer <token>
Content-Type: application/json

{ "reason": "Chuva forte" }
```

```json
{
  "eventId": "8c1d…",
  "cancelledAt": "2026-10-08T17:00:00Z",
  "refundedOrders": 12,
  "cancelledOrders": 3,
  "pendingOrders": 0,
  "requiresManualAction": 0
}
```

O corpo é opcional, e o motivo tem no máximo 500 caracteres. O cancelamento só vale antes do fim do evento. Os pedidos pagos são reembolsados e os pendentes, cancelados, cada um em transação própria. Uma rotina reprocessa o que falhar.

### Lotes

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/ticket-types` | Lotes ativos, com `soldOut` e `remaining`, sem revelar volume de vendas | Público |
| GET | `/events/{eventId}/ticket-types/manage` | Todos os lotes com total, vendido e disponível | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/ticket-types` | Cria um lote | Dono do evento ou ADMIN |
| PATCH | `/events/{eventId}/ticket-types/{ticketTypeId}` | Altera nome, descrição, preço, quantidade total e limite por pedido | Dono do evento ou ADMIN |

**Criar lote**

```http
POST /events/{eventId}/ticket-types
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Pista",
  "description": "Primeiro lote",
  "price": 80.00,
  "quantityTotal": 500,
  "maxPerOrder": 4
}
```

**Listar lotes (público)**

```http
GET /events/{eventId}/ticket-types
```

```json
[
  {
    "id": "ffe5dc69-f145-484a-8b47-51700a8b5414",
    "name": "Pista",
    "description": "Primeiro lote",
    "price": 80.00,
    "maxPerOrder": 4,
    "soldOut": false,
    "remaining": 7
  }
]
```

`remaining` só é preenchido quando restam 10 ingressos ou menos; nos demais casos, inclusive esgotado, vem `null`.

Regras de edição:

- `quantityTotal` não pode ficar abaixo do vendido mais o reservado (400 `invalid-ticket-type-quantity`).
- Mudar o preço não altera pedidos já feitos.
- Lotes de evento cancelado não podem ser editados (409 `invalid-event-state`).
- Nome repetido no mesmo evento responde 409 `ticket-type-name-already-exists`.

### Cupons

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/coupons` | Cupons do evento com usos, pedidos pagos e desconto concedido | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/coupons` | Cria um cupom percentual (`PERCENT`) ou de valor fixo (`FIXED`) | Dono do evento ou ADMIN |
| PUT | `/events/{eventId}/coupons/{couponId}` | Substitui tipo, valor, limite, validade e ativo | Dono do evento ou ADMIN |
| DELETE | `/events/{eventId}/coupons/{couponId}` | Apaga um cupom nunca usado | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/coupons/preview` | Calcula subtotal, desconto e total sem consumir o cupom | Autenticado |

**Criar cupom**

```http
POST /events/{eventId}/coupons
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "amigo10",
  "discountType": "PERCENT",
  "discountValue": 10,
  "maxUses": 100,
  "startsAt": null,
  "endsAt": "2026-12-01T03:00:00Z",
  "active": true
}
```

Regras do cupom:

- O código tem de 3 a 30 letras, números, `-` ou `_`. É gravado em maiúsculas, é único no evento e não muda depois de criado.
- Depois do primeiro uso, tipo e valor ficam fixos e o cupom não pode ser apagado, só desativado (409 `coupon-already-used`).

**Pré-visualizar**

```http
POST /events/{eventId}/coupons/preview
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "AMIGO10",
  "items": [ { "ticketTypeId": "ffe5dc69-…", "quantity": 2 } ]
}
```

```json
{
  "code": "AMIGO10",
  "discountType": "PERCENT",
  "discountValue": 10.00,
  "subtotal": 160.00,
  "discount": 16.00,
  "total": 144.00
}
```

Cupom inválido, expirado, inativo, esgotado ou de outro evento responde 422 `invalid-coupon`, sempre com a mesma mensagem.

### Pedidos e pagamento

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/orders` | Cria o pedido e reserva o estoque por 15 minutos; aceita `couponCode` e `Idempotency-Key` | Autenticado |
| GET | `/orders` | Pedidos do usuário | Autenticado |
| GET | `/orders/{id}` | Detalha o pedido com os ingressos | Dono ou ADMIN |
| DELETE | `/orders/{id}` | Cancela um pedido pendente e devolve o estoque | Dono ou ADMIN |
| POST | `/orders/{orderId}/payment` | Paga (simulado) e emite os ingressos | Dono |
| POST | `/orders/{id}/refund` | Reembolsa até 48 horas antes do início do evento | Dono ou ADMIN |

**Comprar**

```http
POST /orders
Authorization: Bearer <token>
Idempotency-Key: 9d4c1e77-2b1f-4c3a-9e0d-5a7b8c6d1e2f
Content-Type: application/json

{
  "items": [
    { "ticketTypeId": "ffe5dc69-f145-484a-8b47-51700a8b5414", "quantity": 2 }
  ],
  "couponCode": "AMIGO10"
}
```

```http
HTTP/1.1 201 Created

{
  "id": "b7a2…",
  "status": "PENDING",
  "subtotalAmount": 160.00,
  "discountAmount": 16.00,
  "totalAmount": 144.00,
  "couponCode": "AMIGO10",
  "expiresAt": "2026-10-08T17:15:00Z",
  "createdAt": "2026-10-08T17:00:00Z",
  "items": [
    {
      "ticketTypeId": "ffe5dc69-f145-484a-8b47-51700a8b5414",
      "ticketTypeName": "Pista",
      "unitPrice": 80.00,
      "quantity": 2,
      "subtotal": 160.00
    }
  ],
  "tickets": [],
  "eventCancelled": false
}
```

Regras do pedido:

- Todos os itens precisam ser do mesmo evento.
- O preço de cada item fica congelado no pedido.
- Pedido com total zero (lote gratuito ou desconto integral) já sai `PAID`, com os ingressos emitidos e sem pagamento.
- O pedido pendente expira sozinho depois de 15 minutos e devolve estoque e uso do cupom.

**Pagar**

```http
POST /orders/{orderId}/payment
Authorization: Bearer <token>
```

```json
{
  "id": "e41f…",
  "orderId": "b7a2…",
  "status": "APPROVED",
  "method": "SIMULATED",
  "amount": 144.00,
  "approvedAt": "2026-10-08T17:02:00Z",
  "refundedAt": null,
  "createdAt": "2026-10-08T17:02:00Z"
}
```

Depois do pagamento, `GET /orders/{id}` traz os ingressos:

```json
"tickets": [
  { "id": "…", "ticketTypeName": "Pista", "status": "VALID", "code": "K7Q2M9X4T8B3N6PA", "transferred": false },
  { "id": "…", "ticketTypeName": "Pista", "status": "VALID", "code": null, "transferred": true }
]
```

Ingresso já transferido aparece com `transferred: true` e sem `code`.

O reembolso devolve o pagamento, cancela os ingressos e devolve o estoque. Ele é recusado nestes casos:

- fora do prazo (409 `invalid-order-state`);
- com ingresso usado (409 `invalid-order-state`);
- com ingresso transferido (409 `order-has-transferred-tickets`).

### Ingressos e transferência

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/tickets/me` | Ingressos de que o usuário é o dono atual, com `transferable` | Autenticado |
| POST | `/tickets/{id}/transfer` | Transfere para outro usuário cadastrado e gera um código novo | Dono atual |
| POST | `/tickets/{code}/check-in` | Valida o ingresso na entrada | Organizador do evento ou ADMIN |

```json
{
  "id": "1a9c…",
  "code": "K7Q2M9X4T8B3N6PA",
  "status": "VALID",
  "eventName": "Festival de Verão",
  "eventStartsAt": "2026-12-10T23:00:00Z",
  "ticketTypeName": "Pista",
  "usedAt": null,
  "createdAt": "2026-10-08T17:02:00Z",
  "transferable": true
}
```

**Transferir**

```http
POST /tickets/{id}/transfer
Authorization: Bearer <token>
Content-Type: application/json

{
  "recipientEmail": "bruno@email.com",
  "password": "senhaForte123"
}
```

```json
{ "ticketId": "1a9c…", "transferredAt": "2026-10-08T17:10:00Z" }
```

A resposta não traz o código novo. O código antigo deixa de valer na mesma hora.

A transferência só vale para ingresso `VALID`, de evento não cancelado que ainda não começou, até 3 vezes por ingresso. Erros possíveis:

- 403 `invalid-password`;
- 404 `ticket-not-found`;
- 400 `self-transfer`;
- 409 `invalid-ticket-state`, `invalid-event-state`, `ticket-transfer-closed` ou `ticket-transfer-limit-reached`;
- 422 `transfer-recipient-unavailable`;
- 429 `too-many-transfer-attempts`.

**Check-in**

`POST /tickets/{code}/check-in` responde com o ingresso marcado como `USED`. Um código só passa uma vez; a segunda leitura responde 409 `invalid-ticket-state`.

### Painel do organizador

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{id}/dashboard` | Resumo do evento: totais, pedidos por status, check-in, lotes, vendas por dia e cupons | Dono do evento ou ADMIN |
| GET | `/events/{id}/orders` | Pedidos do evento, paginado, com filtros `status` e `q` (nome ou e-mail do comprador) | Dono do evento ou ADMIN |
| GET | `/organizer/dashboard` | Totais e vendas por evento de todos os eventos ativos do organizador, paginado | ORGANIZER ou ADMIN |

Regras dos números:

- **Receita (`revenue`):** só pedidos `PAID`, pelo valor de face congelado nos itens.
- **Descontos:** aparecem em `discounts`.
- **Percentuais:** de 0 a 100, com 2 casas, e `null` quando o denominador é zero.
- **Vendas por dia:** agrupadas no fuso `America/Sao_Paulo`, numa série contínua.
- **Pedidos do evento (`/events/{id}/orders`):** `total` é o valor pago, já com desconto.

```json
{
  "eventId": "8c1d…",
  "eventName": "Festival de Verão",
  "startsAt": "2026-12-10T23:00:00Z",
  "timeZone": "America/Sao_Paulo",
  "generatedAt": "2026-10-08T17:00:00Z",
  "totals": {
    "ticketsSold": 120, "ticketsReserved": 4, "capacity": 500, "remaining": 376, "percentSold": 24.00,
    "revenue": 9600.00, "platformFee": 456.00, "netRevenue": 8664.00, "paidOrders": 70,
    "averageOrderValue": 130.29, "averageTicketPrice": 80.00, "discounts": 480.00
  },
  "ordersByStatus": [ { "status": "PAID", "orders": 70, "tickets": 120 } ],
  "checkIn": { "checkedIn": 0, "issued": 120, "attendanceRate": 0.00 },
  "ticketTypes": [ ],
  "dailySales": [ ],
  "coupons": [ ]
}
```

### Financeiro do organizador

O saldo é derivado de um extrato imutável. Cada venda paga credita o valor líquido (valor pago menos a taxa da plataforma, 5% por padrão). O crédito fica disponível 2 dias depois do fim do evento.

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/organizer/balance` | Saldo a liberar, disponível, em saque, retido e total | ORGANIZER |
| GET | `/organizer/ledger` | Extrato paginado; filtros `eventId`, `from`, `to` (`AAAA-MM-DD`) | ORGANIZER |
| GET | `/organizer/ledger.csv` | Extrato em CSV (UTF-8 com BOM, `;`, decimais com vírgula), com os mesmos filtros | ORGANIZER |
| GET | `/organizer/payout-account` | Dados de recebimento, com documento e chave Pix mascarados | ORGANIZER |
| PUT | `/organizer/payout-account` | Cadastra ou troca os dados de recebimento (pede senha) | ORGANIZER |
| POST | `/organizer/payouts` | Pede um saque do saldo disponível (pede senha; aceita `Idempotency-Key`) | ORGANIZER |
| GET | `/organizer/payouts` | Saques do organizador, do mais recente para o mais antigo | ORGANIZER |
| POST | `/organizer/payouts/{id}/cancel` | Cancela um saque em análise ou solicitado | ORGANIZER |

ADMIN recebe 403 nessas rotas.

**Saldo**

```json
{
  "pending": 950.00,
  "available": 1200.00,
  "inPayout": 0.00,
  "held": 0.00,
  "total": 2150.00,
  "releaseDelayDays": 2,
  "minPayoutAmount": 20.00,
  "payoutsBlocked": false
}
```

O saldo retido (`held`) vem de eventos cancelados com reembolsos ainda em processamento.

**Extrato**

```json
{
  "content": [
    {
      "id": "…",
      "type": "SALE_CREDIT",
      "amount": 136.80,
      "eventId": "8c1d…",
      "eventName": "Festival de Verão",
      "orderId": "b7a2…",
      "payoutId": null,
      "createdAt": "2026-10-08T17:02:00Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Tipos de lançamento: `SALE_CREDIT`, `REFUND_DEBIT`, `PAYOUT_DEBIT`, `PAYOUT_REVERSAL`.

**Dados de recebimento**

```http
PUT /organizer/payout-account
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentType": "CPF",
  "document": "529.982.247-25",
  "holderName": "Ana Souza",
  "pixKeyType": "EMAIL",
  "pixKey": "ana@email.com",
  "password": "senhaForte123"
}
```

```json
{
  "documentType": "CPF",
  "document": "***.982.247-**",
  "holderName": "Ana Souza",
  "pixKeyType": "EMAIL",
  "pixKey": "a***@email.com",
  "payoutsBlockedUntil": null,
  "updatedAt": "2026-10-08T17:00:00Z"
}
```

Regras dos dados de recebimento:

- `documentType`: `CPF` ou `CNPJ`.
- `pixKeyType`: `CPF`, `CNPJ`, `EMAIL`, `PHONE` ou `RANDOM`.
- A chave precisa pertencer ao documento informado.
- Trocar a chave bloqueia novos saques por 48 horas (`payoutsBlockedUntil`).

**Pedir saque**

```http
POST /organizer/payouts
Authorization: Bearer <token>
Idempotency-Key: 5b8e2c1a-…
Content-Type: application/json

{ "amount": 500.00, "password": "senhaForte123" }
```

```http
HTTP/1.1 201 Created

{
  "id": "c3d4…",
  "amount": 500.00,
  "status": "UNDER_REVIEW",
  "pixKeyType": "EMAIL",
  "pixKey": "a***@email.com",
  "holderName": "Ana Souza",
  "requestedAt": "2026-10-08T17:00:00Z",
  "processingStartedAt": null,
  "finishedAt": null,
  "failureReason": null,
  "rejectionReason": null
}
```

Regras do saque:

- **Estados:** `UNDER_REVIEW` → `REQUESTED` → `PROCESSING` → `PAID` ou `FAILED`. Fora desse caminho, o saque pode terminar `CANCELLED` (pelo organizador) ou `REJECTED` (pela análise).
- **Análise:** o primeiro saque e saques acima de R$ 5.000,00 passam por análise.
- **Valores:** o mínimo é R$ 20,00, e só existe um saque em andamento por vez.

### Administração

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/admin/payouts` | Saques por status (padrão `UNDER_REVIEW`), paginado | ADMIN |
| POST | `/admin/payouts/{id}/approve` | Aprova um saque em análise | ADMIN |
| POST | `/admin/payouts/{id}/reject` | Recusa um saque em análise (`{"reason": "..."}`) e estorna o valor | ADMIN |
| GET | `/admin/organizers?email=...` | Situação de saques de um organizador | ADMIN |
| POST | `/admin/organizers/{id}/payout-block` | Bloqueia os saques do organizador (`{"reason": "..."}`) | ADMIN |
| DELETE | `/admin/organizers/{id}/payout-block` | Desbloqueia os saques | ADMIN |
| GET | `/admin/audit` | Auditoria paginada; filtros `targetType`, `targetId`, `actorId`, `from`, `to` | ADMIN |
| PATCH | `/events/{id}/featured` | Destaque de evento (ver [Eventos](#eventos)) | ADMIN |

**Auditoria**

```json
{
  "content": [
    {
      "id": "…",
      "actorType": "USER",
      "actorId": "3f0b…",
      "actorName": "Ana Souza",
      "actorEmail": "ana@email.com",
      "action": "PAYOUT_REQUESTED",
      "targetType": "PAYOUT",
      "targetId": "c3d4…",
      "details": { "organizerId": "3f0b…", "amount": 500.00, "status": "UNDER_REVIEW" },
      "correlationId": "7e1c…",
      "createdAt": "2026-10-08T17:00:00Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Regras dos registros de auditoria:

- `actorType` é `SYSTEM` nas rotinas agendadas, e aí `actorId` vem `null`.
- `correlationId` é o `X-Request-Id` da requisição ou o id da execução da rotina.
- Os detalhes nunca contêm documento, chave Pix ou senha.

### Saúde

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/actuator/health` | Estado da API e da conexão com o banco (`{"status": "UP"}`) | Público |
