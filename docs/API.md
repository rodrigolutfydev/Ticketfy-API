# Referência da API do Ticketfy

Esta é a referência de uso da API REST do Ticketfy: autenticação, formato de erros, paginação, idempotência e todos os endpoints, agrupados por assunto. As decisões por trás de cada regra estão no [documento de arquitetura](05%20-%20Documento%20de%20Arquitetura.md).

O contrato gerado a partir do código fica em [`openapi.json`](openapi.json). O build atualiza esse arquivo, e o CI falha se a versão commitada estiver desatualizada.

## Sumário

- [Visão geral](#visão-geral)
- [Autenticação](#autenticação)
- [Erros](#erros)
- [Formato dos CSVs](#formato-dos-csvs)
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
  - [Participantes](#participantes)
  - [Painel do organizador](#painel-do-organizador)
  - [Financeiro do organizador](#financeiro-do-organizador)
  - [Administração](#administração)
  - [Saúde](#saúde)

## Visão geral

| Item | Valor |
|---|---|
| Produção | `https://ticketfy-api.onrender.com` |
| Local | `http://localhost:8080` |
| Swagger UI | [`https://ticketfy-api.onrender.com/swagger-ui/index.html`](https://ticketfy-api.onrender.com/swagger-ui/index.html) (em produção, o "Try it out" executa só `GET`); local em `http://localhost:8080/swagger-ui/index.html` |
| Formato | JSON em UTF-8; erros em `application/problem+json` |
| Datas | ISO 8601 em UTC: `"2026-12-10T23:00:00Z"` é 20h em Brasília. Filtros por dia (`from`, `to`) usam `AAAA-MM-DD` no fuso `America/Sao_Paulo` |
| Dinheiro | Número decimal com 2 casas, em reais: `80.00` |
| Identificadores | UUID |
| Rastreio | Toda resposta traz `X-Request-Id`. O cliente pode enviar o próprio (letras, números e hífen, até 64 caracteres) |
| CORS | Só as origens de `CORS_ALLOWED_ORIGINS`, sem credenciais. O navegador consegue ler `Retry-After`, `X-Request-Id` e `Content-Disposition` (nome do arquivo nos downloads de CSV, JSON e `.ics`) |

Na coluna **Acesso** das tabelas de endpoints:

- **Público**: sem token.
- **Autenticado**: qualquer usuário logado.
- **ORGANIZER**, **ADMIN**: exige o papel.
- **Dono**: o recurso precisa pertencer ao usuário. Pedido ou ingresso de outra pessoa responde 404, sem revelar que existe.

## Autenticação

### Access token

`POST /auth/login` e `POST /auth/google` devolvem um access token JWT que vale **10 minutos**. Rotas protegidas exigem o header:

```http
Authorization: Bearer <token>
```

A cada requisição, a API confere no banco a sessão que emitiu o token. Logout, troca de senha e exclusão da conta derrubam o token na hora, sem esperar ele vencer.

### Refresh token em cookie

O refresh token nunca aparece no corpo da resposta. Ele vai no cookie `__Host-ticketfy_rt` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`), vale **30 minutos sem uso** e é trocado a cada renovação. A sessão dura no máximo **12 horas**. Reapresentar um refresh token já usado encerra a sessão inteira, com uma exceção: se ele foi trocado há menos de **30 segundos** e o sucessor ainda não foi usado, a API entende que a resposta anterior se perdeu (o cliente desistiu da requisição, por exemplo com a API acordando), invalida o sucessor que nunca chegou ao navegador e devolve um par novo normalmente. Um sucessor invalidado dessa forma, se aparecer depois, encerra a sessão.

| Rota | O que faz |
|---|---|
| `POST /auth/login` | Confere e-mail e senha, abre uma sessão, devolve o access token e grava o cookie; conta suspensa responde 403 `account-suspended`, só depois de a senha conferir |
| `POST /auth/google` | Login com Google: troca o código do Google, valida a identidade e abre a mesma sessão do login com senha (ver [Login com Google](#login-com-google)) |
| `POST /auth/refresh` | Troca o cookie por um novo e devolve outro access token; sem sessão válida, 401 `session-expired` e o cookie é apagado; conta suspensa, 403 `account-suspended` e o cookie é apagado |
| `POST /auth/logout` | Encerra a sessão do cookie e apaga o cookie; responde 204 mesmo sem cookie |
| `POST /auth/logout-all` | Encerra todas as sessões do usuário; exige também `Authorization` |
| `POST /auth/password-reset/request` | Pede o link de recuperação de senha por e-mail; sempre 202 (ver [Recuperação de senha](#recuperação-de-senha)) |
| `POST /auth/password-reset/confirm` | Troca a senha com o token do link; 204 e todas as sessões da conta encerradas |

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

E-mail ou senha errados respondem 401 `invalid-credentials`; conta criada pelo Google que ainda não definiu senha também. Cinco tentativas por IP em 60 segundos, somando login com senha e com Google, levam a 429 `too-many-login-attempts`.

**Renovar**

```http
POST /auth/refresh
Origin: http://localhost:5173
X-Ticketfy-Auth: 1
Cookie: __Host-ticketfy_rt=<refresh token>
```

A resposta tem o mesmo formato do login e grava um cookie novo.

### Login com Google

Fluxo de código com PKCE (authorization code + PKCE). O front gera um `code_verifier` aleatório, manda o usuário ao Google com o `code_challenge` (SHA-256 do verifier) e recebe de volta um código na redirect URI. Depois chama a API, pelo mesmo proxy das outras rotas `/auth/**` (com `Origin` e `X-Ticketfy-Auth: 1`):

```http
POST /auth/google
Origin: http://localhost:5173
X-Ticketfy-Auth: 1
Content-Type: application/json

{
  "code": "<código devolvido pelo Google>",
  "codeVerifier": "<code_verifier do PKCE, 43 a 128 caracteres>",
  "redirectUri": "http://localhost:5173/auth/google/callback"
}
```

A resposta é a mesma do login com senha: 200 com `token` e `expiresIn`, e o cookie `__Host-ticketfy_rt`.

O que a API faz:

1. Confere se `redirectUri` está, exatamente, na lista de `GOOGLE_REDIRECT_URIS`; fora dela, 400 `google-redirect-uri-not-allowed`, sem chamar o Google.
2. Troca o código no endpoint de token do Google, com client id, client secret, `code_verifier` e `redirect_uri`. Código recusado (inválido, vencido, já usado ou verifier errado) responde 401 `google-authorization-failed`; Google fora do ar ou sem resposta em 10 s, 502 `google-unavailable`.
3. Valida o `id_token` recebido: assinatura pelas chaves públicas do Google, `iss` (`https://accounts.google.com` ou `accounts.google.com`), `aud` igual ao client id e `exp` (60 s de tolerância de relógio). Qualquer falha responde 401 `google-authorization-failed`. E-mail não verificado no Google (`email_verified` diferente de `true`) responde 403 `google-email-not-verified`.
4. Acha o usuário:
   - pelo identificador do Google (`sub`), se a conta já está ligada;
   - senão, pelo e-mail (sem diferenciar maiúsculas) de uma conta ativa, e liga essa conta ao Google. Na ligação, a senha da conta é **apagada** e as sessões que ela já tinha abertas são encerradas: o cadastro com senha não confirma o e-mail, então a senha pode ter sido criada por outra pessoa antes de o dono entrar pelo Google. A partir daí a conta entra pelo Google, e o dono pode definir uma senha nova em `PATCH /users/me/password` sem a senha atual (ver [Definir a primeira senha](#usuários-e-conta)); a senha antiga responde 401 `invalid-credentials`. A ligação vai para a auditoria (`GOOGLE_ACCOUNT_LINKED`, com `revokedSessions` e `credentialRemoved`, que indica a senha apagada). Se o e-mail já estiver ligado a outra conta Google, 409 `google-account-conflict`;
   - senão, cria um usuário `USER` com o nome e o e-mail do Google e **sem senha**.
5. Abre a sessão como no login com senha. Conta suspensa responde 403 `account-suspended`, e nesse caso nada é ligado.

Sem `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e `GOOGLE_REDIRECT_URIS`, a rota responde 503 `google-login-unavailable` e o resto da API funciona normalmente.

### Recuperação de senha

**Pedir o link**

```http
POST /auth/password-reset/request
Origin: https://ticketfy.example
X-Ticketfy-Auth: 1
Content-Type: application/json

{ "email": "ana@example.com" }
```

Responde **202 sem corpo**, exista a conta ou não, para não revelar quem tem cadastro. Só conta ativa (não suspensa nem excluída) recebe e-mail. O pedido:

1. gera um token aleatório de 256 bits (base64url, 43 caracteres) e guarda só o hash SHA-256 dele;
2. invalida os tokens anteriores da conta que ainda não foram usados;
3. envia, depois do commit e fora da requisição, um e-mail no idioma da conta com o link `SITE_BASE_URL/reset-password?token=<token>`, avisando que ele vale **30 minutos** e que pode ser ignorado por quem não pediu.

Conta criada só pelo Google também pode pedir: o e-mail fala em definir a senha, e a confirmação cria a primeira senha da conta. Sem `SITE_BASE_URL`, o token é gerado mas o e-mail não sai (fica um aviso no log).

**Trocar a senha**

```http
POST /auth/password-reset/confirm
Origin: https://ticketfy.example
X-Ticketfy-Auth: 1
Content-Type: application/json

{ "token": "Vx3…", "newPassword": "senha-nova-456" }
```

Responde **204**. A senha segue as regras do cadastro (8 a 72 caracteres; senão 400 `validation-failed`, e o token continua valendo). O token passa a usado, todas as sessões da conta são encerradas e nenhuma sessão nova é aberta: o front leva a pessoa ao login.

| Erro | Quando |
|---|---|
| 400 `password-reset-token-invalid` | Token desconhecido ou malformado, invalidado por um pedido mais novo, ou de conta suspensa ou excluída |
| 400 `password-reset-token-expired` | Mais de 30 minutos desde o pedido |
| 400 `password-reset-token-used` | Token já usado |
| 429 `too-many-password-reset-attempts` | Limite de pedidos ou de confirmações (ver [Limites de tentativas](#limites-de-tentativas)), com `Retry-After` |

As duas rotas ficam em `/auth`, então exigem `Origin` e `X-Ticketfy-Auth` como o login e passam pelo proxy do front. A auditoria registra `PASSWORD_RESET_REQUESTED` (com `invalidatedTokens` e `hasPassword`) e `PASSWORD_RESET` (com `revokedSessions` e `firstPassword`), sem token nem senha.

### Confirmação de senha

Ações sensíveis pedem a senha de novo no corpo (`password` ou `currentPassword`):

- trocar senha;
- transferir ingresso;
- salvar dados de recebimento;
- pedir saque;
- baixar os próprios dados;
- excluir a conta.

Senha errada responde 403 `invalid-password`. Cinco erros em 15 minutos bloqueiam a confirmação com 429 `too-many-password-attempts` e `Retry-After`.

Conta sem senha (criada pelo Google) responde 409 `password-not-set` nessas ações, com duas exceções: trocar a senha vira definir a primeira senha, sem `currentPassword` (ver [Usuários e conta](#usuários-e-conta)), e excluir a conta é confirmado com o próprio e-mail (ver [Privacidade](#privacidade-lgpd)). `GET /users/me` informa `hasPassword` e `googleLinked`.

## Erros

Toda resposta de erro segue a RFC 7807 (`application/problem+json`):

| Campo | Conteúdo |
|---|---|
| `type` | URI estável do tipo de erro: `https://ticketfy-api.onrender.com/problems/<slug>`. Use o slug para decidir o que mostrar |
| `title` | Resumo fixo do tipo |
| `status` | Código HTTP |
| `detail` | Mensagem em inglês, específica da ocorrência, para desenvolvedores. Não é traduzida pela API nem deve ser mostrada ao usuário |
| `reason` | Presente em alguns tipos: motivo estável dentro do tipo, para o cliente escolher a mensagem (ver [Motivos](#motivos)) |
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

Erros de validação trazem `errors`, com um item por campo. Cada item tem `field`, `message` (em inglês), `code` (regra violada, estável) e `params` (parâmetros da regra, objeto possivelmente vazio). O cliente monta a frase no idioma do usuário a partir de `code` e `params`:

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
    { "field": "password", "message": "Password must be at least 8 characters long", "code": "size", "params": { "min": 8, "max": 72 } },
    { "field": "email", "message": "must be a well-formed email address", "code": "email", "params": {} }
  ]
}
```

### Códigos de validação

| `code` | Quando | `params` |
|---|---|---|
| `required` | Campo ausente, nulo ou em branco | — |
| `size` | Texto fora do tamanho | `min` e/ou `max` (só os que a regra define) |
| `count` | Lista vazia ou fora do tamanho (por exemplo `items` do pedido) | `min` e/ou `max` |
| `email` | E-mail malformado | — |
| `positive` | Número precisa ser maior que zero | — |
| `positive-or-zero` | Número não pode ser negativo | — |
| `digits` | Número com dígitos demais | `integer`, `fraction` |
| `future` | Data precisa estar no futuro | — |
| `url` | URL inválida | `protocol` (por exemplo `https`) |
| `one-of` | Valor fora da lista aceita | `allowed` (lista) |
| `coupon-code` | Código de cupom fora do formato (letras, dígitos, `-` e `_`) | `min`, `max` |
| `confirmation` | Texto de confirmação diferente dos aceitos | `allowed` (lista) |
| `invalid-document` | CPF ou CNPJ inválido | `documentType` |
| `invalid-pix-key` | Chave Pix inválida para o tipo | `pixKeyType` |
| `invalid` | Qualquer outra regra | — |

Campos de listas aparecem com índice, como `items[0].quantity`.

### Motivos

Os tipos abaixo trazem `reason`, com um dos valores da tabela. A lista também está em `docs/problem-types.json` (campo `reasons` de cada tipo). Um motivo novo pode aparecer no futuro: o cliente deve cair na mensagem geral do tipo quando não conhecer o valor.

| Tipo | `reason` | Quando | Propriedades extras |
|---|---|---|---|
| `invalid-coupon-settings` | `percent-out-of-range` | Percentual fora de 0,01 a 100 | `min`, `max` |
| | `ends-before-starts` | Fim da validade não é depois do início | — |
| | `max-uses-below-used` | Limite de usos abaixo dos usos já feitos | `usesCount` |
| `coupon-already-used` | `discount-locked` | Mudar tipo ou valor de cupom já usado | — |
| | `delete-blocked` | Excluir cupom já usado | — |
| `invalid-ticket-type-quantity` | `below-sold` | Quantidade total abaixo do vendido e reservado | `quantitySold` |
| `invalid-ticket-type-state` | `already-active` | Reativar lote já ativo | — |
| | `already-inactive` | Desativar lote já desativado | — |
| `invalid-event-state` | `cancelled` | Evento cancelado | — |
| | `ended` | Cancelar evento já encerrado | — |
| `invalid-order-state` | `expired` | Pagar pedido com a reserva vencida | — |
| | `refund-period-ended` | Reembolso fora do prazo | — |
| | `tickets-used` | Reembolso de pedido com ingresso já usado | — |
| | `not-pending` | Pagar ou cancelar pedido que não está pendente | — |
| | `not-paid` | Reembolsar pedido que não está pago | — |
| `invalid-payment-state` | `already-paid` | Pagar pedido já pago | — |
| | `not-pending` | Aprovar ou recusar pagamento que não está pendente | — |
| | `not-approved` | Estornar pagamento que não foi aprovado | — |
| `invalid-ticket-state` | `event-cancelled` | Check-in em evento cancelado | — |
| | `already-used` | Check-in de ingresso já usado | `usedAt` |
| | `not-valid` | Ingresso cancelado (check-in) ou já usado ou cancelado (transferência, cancelamento) | — |
| | `transfer-unavailable` | O ingresso mudou durante a transferência | — |
| `invalid-suspension-state` | `already-suspended` | Suspender conta já suspensa | — |
| | `not-suspended` | Reativar conta que não está suspensa | — |
| `invalid-payout-state` | `already-processing` | Cancelar saque que entrou em processamento | — |
| | `not-cancellable` | Cancelar saque que não está solicitado nem em análise | — |
| | `not-under-review` | Aprovar ou recusar saque que não está em análise | — |
| | `changed-concurrently` | O saque mudou por outra ação ao mesmo tempo | — |
| `invalid-payout-block-state` | `already-blocked` | Bloquear saques já bloqueados | — |
| | `not-blocked` | Desbloquear saques que não estão bloqueados | — |

### Tipos de erro

| Slug | Status | Quando acontece |
|---|---|---|
| `validation-failed` | 400 | Campo inválido no corpo ou cabeçalho inválido (`Idempotency-Key` em `POST /orders`); ver `errors` |
| `malformed-request-body` | 400 | JSON malformado ou com tipo errado |
| `missing-parameter` | 400 | Parâmetro de query obrigatório ausente |
| `missing-header` | 400 | Header obrigatório ausente |
| `invalid-parameter` | 400 | Parâmetro com valor inválido: UUID, enum, data, página, `sort`, intervalo de datas ou `Idempotency-Key` em `POST /organizer/payouts` |
| `bad-request` | 400 | Requisição inválida sem tipo mais específico |
| `invalid-event-dates` | 400 | Fim do evento não é depois do início |
| `invalid-ticket-type-quantity` | 400 | Quantidade total do lote abaixo do vendido e reservado; traz `reason` e `quantitySold` |
| `invalid-ticket-type-state` | 409 | Desativar lote já desativado, ou reativar lote já ativo |
| `mixed-events-order` | 400 | Pedido com lotes de eventos diferentes |
| `max-per-order-exceeded` | 400 | Quantidade acima do limite por pedido do lote |
| `max-per-buyer-exceeded` | 409 | Compra passaria do limite por comprador do lote; traz `ticketTypeId`, `maxPerBuyer` e `remaining` |
| `self-transfer` | 400 | Transferência para o próprio e-mail |
| `invalid-coupon-settings` | 400 | Percentual acima de 100, fim antes do início ou limite abaixo dos usos; traz `reason` |
| `pix-key-not-found` | 400 | Chave Pix não encontrada no provedor |
| `pix-key-holder-mismatch` | 400 | Chave Pix de outro documento |
| `payout-below-minimum` | 400 | Saque abaixo do mínimo; traz `minAmount` |
| `email-not-allowed` | 400 | Cadastro com domínio reservado (`deleted.ticketfy.invalid`), ou e-mail do Google com mais de 150 caracteres |
| `google-redirect-uri-not-allowed` | 400 | `redirectUri` fora da lista `GOOGLE_REDIRECT_URIS` no login com Google |
| `password-reset-token-invalid` | 400 | Token de recuperação desconhecido, malformado, invalidado por pedido mais novo ou de conta suspensa ou excluída |
| `password-reset-token-expired` | 400 | Token de recuperação com mais de 30 minutos |
| `password-reset-token-used` | 400 | Token de recuperação já usado |
| `invalid-credentials` | 401 | E-mail ou senha errados no login, ou conta sem senha (inclusive a senha apagada ao ligar a conta ao Google) |
| `google-authorization-failed` | 401 | Código recusado pelo Google ou `id_token` inválido (assinatura, `iss`, `aud` ou `exp`) |
| `authentication-required` | 401 | Rota protegida sem token ou com token inválido |
| `session-expired` | 401 | Sessão vencida, revogada ou conta excluída |
| `access-denied` | 403 | Papel insuficiente |
| `event-access-denied` | 403 | O evento não é do usuário |
| `auth-request-rejected` | 403 | `POST /auth/**` sem `Origin` permitido ou sem `X-Ticketfy-Auth: 1` |
| `invalid-password` | 403 | Senha de confirmação errada |
| `email-confirmation-mismatch` | 403 | Exclusão de conta sem senha com e-mail diferente do da conta |
| `google-email-not-verified` | 403 | E-mail não verificado na conta Google |
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
| `sales-closed` | 409 | Compra ou prévia de cupom depois do início do evento |
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
| `account-suspended` | 403 | Login, renovação ou exclusão de conta suspensa; a mensagem não traz o motivo da suspensão |
| `admin-account-suspension` | 409 | Suspensão de conta ADMIN, inclusive a própria |
| `invalid-suspension-state` | 409 | Suspender conta já suspensa, ou reativar conta que não está suspensa |
| `account-has-payout-block` | 409 | Exclusão com saques bloqueados |
| `account-has-payout-in-progress` | 409 | Exclusão com saque em andamento |
| `account-has-balance` | 409 | Exclusão com saldo diferente de zero; traz `pending`, `available` e `held` |
| `account-has-active-events` | 409 | Exclusão com evento com vendas ou pedidos pendentes; traz `eventIds` |
| `account-has-upcoming-tickets` | 409 | Exclusão com ingresso válido de evento futuro; traz `ticketCount` |
| `password-not-set` | 409 | Ação que pede a senha numa conta sem senha (criada pelo Google) |
| `google-account-conflict` | 409 | Login com Google cujo e-mail já está ligado a outra conta Google, ou usado por mais de uma conta |
| `unsupported-media-type` | 415 | `Content-Type` diferente de JSON |
| `transfer-recipient-unavailable` | 422 | Destinatário da transferência não pode receber (mensagem genérica) |
| `invalid-coupon` | 422 | Cupom inválido, expirado, inativo, esgotado ou de outro evento (mesma mensagem para todos) |
| `coupon-buyer-limit-reached` | 422 | O comprador já usou o cupom o máximo de vezes permitido; traz `maxUsesPerBuyer` e não conta como tentativa inválida |
| `too-many-login-attempts` | 429 | Limite de login por IP |
| `too-many-password-reset-attempts` | 429 | Limite de pedidos de recuperação de senha (por IP e por e-mail) ou de confirmações (por IP) |
| `too-many-password-attempts` | 429 | Limite de senha de confirmação errada |
| `too-many-transfer-attempts` | 429 | Limite de destinatários inexistentes |
| `too-many-coupon-attempts` | 429 | Limite de cupons inválidos |
| `too-many-data-exports` | 429 | Limite de exportações de dados |
| `internal-error` | 500 | Erro inesperado; `detail` é sempre `"Internal server error"` e traz `requestId` |
| `google-unavailable` | 502 | Google fora do ar ou sem resposta no login com Google |
| `google-login-unavailable` | 503 | Login com Google não configurado (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e `GOOGLE_REDIRECT_URIS`) |

## Formato dos CSVs

O extrato (`/organizer/ledger.csv`) e a lista de participantes (`/events/{eventId}/participants.csv`) saem no idioma salvo na conta de quem pede (`language`). Os dois idiomas usam UTF-8 com BOM, linhas em CRLF, datas no fuso do painel (`DASHBOARD_TIME_ZONE`) e a mesma proteção contra fórmulas.

| | `pt-BR` | `en` |
|---|---|---|
| Separador | `;` | `,` |
| Valores | `1234,50` | `1234.50` (sem separador de milhar) |
| Data e hora | `dd/MM/yyyy HH:mm:ss` | `yyyy-MM-dd HH:mm:ss` |
| Extrato: cabeçalho | `Data;Tipo;Valor;Evento;Pedido;Saque;Lançamento` | `Date,Type,Amount,Event,Order,Withdrawal,Entry` |
| Extrato: tipos | Venda, Reembolso, Saque, Estorno de saque | Sale, Refund, Withdrawal, Withdrawal reversal |
| Participantes: cabeçalho | `Participante;Lote;Situação;Check-in;Transferido;Final do código` | `Attendee,Batch,Status,Check-in,Transferred,Code ending` |
| Participantes: situação | Válido, Utilizado, Cancelado; Sim/Não | Valid, Used, Cancelled; Yes/No |
| Nome do arquivo | `extrato-ticketfy-<data>.csv`, `participantes-ticketfy-<data>.csv` | `ticketfy-statement-<data>.csv`, `ticketfy-attendees-<data>.csv` |

Texto que contém o separador, aspas ou quebra de linha vai entre aspas. Os termos em inglês são os mesmos do site.

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

Em `POST /orders`, a chave precisa ter de 1 a 100 caracteres ASCII visíveis (sem espaço, acento ou caractere de controle). Fora disso, a resposta é 400 `validation-failed` com `field` igual a `Idempotency-Key` e `code` `size` (com `params.min` e `params.max`) ou `invalid`, antes de qualquer acesso ao banco:

```json
{
  "type": "https://ticketfy-api.onrender.com/problems/validation-failed",
  "status": 400,
  "errors": [
    { "field": "Idempotency-Key", "message": "Idempotency-Key must have between 1 and 100 characters", "code": "size", "params": { "min": 1, "max": 100 } }
  ]
}
```

Em `POST /organizer/payouts`, a chave vazia ou com mais de 100 caracteres responde 400 `invalid-parameter`.

## Limites de tentativas

Os limites ficam em memória, por instância da aplicação, exceto o de exportação, que é contado pela auditoria.

| O que | Limite | Resposta |
|---|---|---|
| Login (senha e Google somados) | 5 por IP em 60 s | 429 `too-many-login-attempts` |
| Pedido de recuperação de senha | 10 por IP e 3 por e-mail em 15 min; conta o pedido exista a conta ou não, e o recusado também conta | 429 `too-many-password-reset-attempts` |
| Confirmação de recuperação de senha | 10 por IP em 15 min | 429 `too-many-password-reset-attempts` |
| Senha de confirmação errada | 5 por usuário em 15 min | 429 `too-many-password-attempts` |
| Transferência para e-mail não cadastrado | 5 por usuário em 15 min | 429 `too-many-transfer-attempts` |
| Cupom inválido (pré-visualização e pedido somados) | 10 por usuário em 15 min | 429 `too-many-coupon-attempts` |
| Exportação de dados | 3 por usuário em 24 h | 429 `too-many-data-exports` |

## Endpoints

### Usuários e conta

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/users` | Cadastra um usuário com papel `USER` | Público |
| GET | `/users/me` | Dados do usuário autenticado, com o idioma preferido, se tem senha (`hasPassword`) e se está ligado ao Google (`googleLinked`) | Autenticado |
| PATCH | `/users/me/language` | Troca o idioma preferido (`pt-BR` ou `en`) | Autenticado |
| PATCH | `/users/me/avatar` | Troca a foto (`avatarUrl` https); `""` ou `null` remove | Autenticado |
| PATCH | `/users/me/password` | Troca a senha (ou define a primeira, em conta sem senha), encerra todas as sessões e abre uma nova | Autenticado |
| POST | `/users/me/organizer` | Torna o usuário organizador; responde 204 e o token atual continua valendo | Autenticado |
| POST | `/auth/login` | Ver [Autenticação](#autenticação) | Público |
| POST | `/auth/google` | Ver [Login com Google](#login-com-google) | Público |
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
  "password": "senhaForte123",
  "language": "pt-BR"
}
```

```http
HTTP/1.1 201 Created

{
  "id": "3f0b6c1e-8a5d-4f7e-9c2b-1d4e5f6a7b8c",
  "name": "Ana Souza",
  "email": "ana@email.com",
  "role": "USER",
  "avatarUrl": null,
  "language": "pt-BR",
  "hasPassword": true,
  "googleLinked": false
}
```

A senha tem de 8 a 72 caracteres. E-mail repetido responde 409 `email-already-exists`. `language` é opcional: sem ele, a conta fica em `pt-BR`.

**Trocar o idioma**

```http
PATCH /users/me/language
Authorization: Bearer <token>
Content-Type: application/json

{ "language": "en" }
```

A resposta tem o formato de `GET /users/me`. Valores aceitos: `pt-BR` e `en`, exatamente assim. Outro valor responde 400 `validation-failed` com `code` `one-of` e `params.allowed`; sem o campo, `code` `required`. O idioma serve ao frontend e, no futuro, aos e-mails: a API continua respondendo em inglês no `detail`, qualquer que seja o idioma da conta.

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

A resposta tem o mesmo formato do login e grava um cookie novo. As outras sessões são encerradas. Sem `currentPassword` numa conta que tem senha, a resposta é 400 `validation-failed` com `code` `required`.

**Definir a primeira senha (conta criada pelo Google)**

```http
PATCH /users/me/password
Authorization: Bearer <token>
Content-Type: application/json

{ "newPassword": "outraSenhaForte456" }
```

Vale só enquanto `hasPassword` é `false` (conta criada pelo Google, ou conta antiga cuja senha foi apagada ao ser ligada ao Google): não pede a senha atual e, como na troca, encerra as sessões e abre uma nova. Vai para a auditoria como `PASSWORD_SET` (a troca continua `PASSWORD_CHANGED`). Depois disso, a conta entra também com e-mail e senha e passa a confirmar ações com a senha.

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
  "profile": { "id": "…", "name": "Ana Souza", "email": "ana@email.com", "role": "ORGANIZER", "avatarUrl": null, "language": "pt-BR", "googleLinked": false, "createdAt": "…", "updatedAt": "…" },
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

- substitui nome, e-mail, foto e senha por valores sem identificação e desfaz a ligação com o Google;
- apaga os dados de recebimento;
- cancela pedidos pendentes;
- desativa os eventos do usuário;
- revoga todas as sessões, então o token atual passa a receber 401.

Pedidos, pagamentos, extrato, saques e auditoria continuam existindo, ligados à conta anonimizada, e o e-mail original pode ser usado num novo cadastro.

Conta sem senha (criada pelo Google) confirma com o próprio e-mail no lugar da senha:

```json
{
  "email": "ana@gmail.com",
  "confirmation": "EXCLUIR"
}
```

O e-mail é comparado sem diferenciar maiúsculas e sem os espaços das pontas; diferente do da conta, a resposta é 403 `email-confirmation-mismatch`. Quem tem senha continua confirmando com `password`. O campo exigido pela conta (`password` ou `email`) faltando responde 400 `validation-failed` com `code` `required`.

`confirmation` aceita `"EXCLUIR"` ou `"DELETE"`, exatamente assim, para o cliente pedir a palavra no idioma do usuário. Outro valor, ou a falta dele, responde 400 `validation-failed`, com `code` `confirmation` e `params.allowed`. Conta suspensa responde 403 `account-suspended`. A exclusão é recusada com 409 nos casos abaixo, verificados nesta ordem:

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
| GET | `/events` | Lista eventos ativos, não cancelados e que ainda não terminaram. Filtros `q` (nome), `city`, `category` (um ou mais), `featured` (com `true`, só eventos que ainda não começaram), `soldOut`; ordem padrão `startsAt` | Público |
| GET | `/events/categories` | Todas as categorias, com a quantidade de eventos visíveis na vitrine em cada uma | Público |
| GET | `/events/{id}` | Detalha um evento ativo, inclusive já realizado; `salesClosed` indica se ainda é possível comprar | Público |
| GET | `/events/{id}/calendar.ics` | Arquivo de agenda (iCalendar, RFC 5545) do evento | Público |
| GET | `/events/mine` | Eventos ativos do usuário; ordem padrão `startsAt` | Autenticado |
| POST | `/events` | Cria um evento | ORGANIZER ou ADMIN |
| PUT | `/events/{id}` | Atualiza um evento; campos ausentes ficam como estão | Dono ou ADMIN |
| GET | `/events/{id}/cancellation-preview` | Quantos pedidos e ingressos o cancelamento afeta, por tipo | Dono ou ADMIN |
| POST | `/events/{id}/cancel` | Cancela o evento e reembolsa os pedidos pagos | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento sem vendas nem reservas | Dono ou ADMIN |
| PATCH | `/events/{id}/featured` | Liga ou desliga o destaque (`{"featured": true}`) | ADMIN |

**Adicionar à agenda**

```http
GET /events/8c1d…/calendar.ics
```

```http
HTTP/1.1 200 OK
Content-Type: text/calendar;charset=UTF-8
Content-Disposition: attachment; filename="ticketfy-event-8c1d….ics"
Cache-Control: no-cache

BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Ticketfy//Events//EN
CALSCALE:GREGORIAN
METHOD:PUBLISH
BEGIN:VEVENT
UID:event-8c1d…@ticketfy
DTSTAMP:20261012T170000Z
SEQUENCE:0
LAST-MODIFIED:20261001T120000Z
DTSTART:20261210T230000Z
DTEND:20261211T040000Z
SUMMARY:Festival de Verão
LOCATION:Marina da Glória\, Av. Infante Dom Henrique\, s/n\, Rio de Janeiro - RJ
DESCRIPTION:Três palcos e praça de alimentação\n\nhttps://ticketfy.example/
 events/8c1d…
STATUS:CONFIRMED
URL:https://ticketfy.example/events/8c1d…
END:VEVENT
END:VCALENDAR
```

Regras do arquivo:

- Linhas terminam em CRLF e são dobradas em 75 octetos (a continuação começa com um espaço). Datas em UTC.
- Textos vindos do organizador são escapados (`\\`, `\;`, `\,` e quebras de linha como `\n`), então não criam propriedades novas.
- `UID` é fixo por evento e `SEQUENCE` cresce a cada alteração, para o calendário atualizar o evento já adicionado.
- Sem término, não há `DTEND`. Evento cancelado sai com `STATUS:CANCELLED` e o motivo no início da descrição.
- `URL` e o link na descrição usam `SITE_BASE_URL`; sem ela configurada, o arquivo sai sem link.
- Evento inexistente ou desativado responde 404 `event-not-found`.

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
  "endsAt": "2026-12-11T04:00:00Z",
  "category": "MUSIC"
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
  "cancellationReason": null,
  "salesClosed": false,
  "category": "MUSIC"
}
```

Regras de imagem e datas:

- `imageUrl` precisa começar com `https://`.
- No `PUT`, `imageUrl` ausente ou `null` mantém a imagem, e `""` remove.
- `startsAt` e `endsAt` precisam estar no futuro, e o fim depois do início.
- As vendas encerram no início do evento: a partir de `startsAt`, `salesClosed` passa a `true`, e `POST /orders` e a prévia de cupom respondem 409 `sales-closed`. Um evento sem `endsAt` sai da busca no início.

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
      "cancelledAt": null,
      "category": "MUSIC"
    }
  ],
  "page": { "size": 12, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

`minPrice` é o menor preço entre os lotes com estoque. Evento sem lote ativo vem com `minPrice: null`.

**Categorias**

A categoria é obrigatória na criação (`category`) e pode mudar no `PUT`; ausente no `PUT`, a atual é mantida. Os códigos são fixos: `MUSIC` (Música), `THEATER` (Teatro), `COMEDY` (Humor), `PARTIES` (Festas), `SPORTS` (Esportes), `COURSES` (Cursos e workshops), `TALKS` (Palestras e congressos), `ARTS` (Arte e exposições), `FOOD` (Gastronomia), `KIDS` (Infantil) e `OTHER` (Outros). A lista também é publicada em `docs/event-categories.json`, gerado no build. Os nomes exibidos ficam no front.

- A busca aceita várias categorias, repetindo o parâmetro (`?category=MUSIC&category=COMEDY`) ou separando por vírgula, e devolve eventos de qualquer uma delas. Valor fora da lista responde 400 `invalid-parameter`.
- `GET /events/categories` conta só os eventos que a vitrine mostra: ativos, não cancelados, não terminados e de organizador não suspenso. Todas as categorias vêm na resposta, inclusive as que têm zero.

```json
[
  { "category": "MUSIC", "events": 12 },
  { "category": "THEATER", "events": 3 },
  { "category": "OTHER", "events": 0 }
]
```

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
  "cancelledFreeOrders": 2,
  "cancelledOrders": 3,
  "pendingOrders": 0,
  "requiresManualAction": 1,
  "usedTickets": 4
}
```

O corpo é opcional, e o motivo tem no máximo 500 caracteres. O cancelamento só vale antes do fim do evento. Cada pedido é tratado em transação própria, e uma rotina reprocessa o que falhar:

| Campo | Pedidos |
|---|---|
| `refundedOrders` | Pagos com valor acima de zero, sem ingresso usado: reembolsados (`REFUNDED`) |
| `cancelledFreeOrders` | Gratuitos (total R$ 0,00): ficam `CANCELLED`, sem reembolso; ingressos válidos cancelados, os usados continuam `USED` |
| `cancelledOrders` | Pendentes: cancelados |
| `pendingOrders` | Falharam agora e serão refeitos pela rotina |
| `requiresManualAction` | Pagos com valor acima de zero e com ingresso usado: continuam `PAID` e precisam de reembolso manual |

`usedTickets` é o número de ingressos do evento que já passaram pelo check-in. Pedido gratuito nunca entra em `requiresManualAction`, mesmo com ingresso usado.

**Prévia do cancelamento**

```http
GET /events/{id}/cancellation-preview
Authorization: Bearer <token>
```

```json
{
  "eventId": "8c1d…",
  "ordersToRefund": 12,
  "freeOrdersToCancel": 2,
  "pendingOrdersToCancel": 3,
  "ordersRequiringManualAction": 1,
  "usedTickets": 4
}
```

Usa as mesmas regras do cancelamento, para a tela de confirmação. Os números são do momento da consulta. O `ordersByStatus` do dashboard conta todos os pedidos `PAID`, inclusive os gratuitos e os com ingresso usado, e não serve para essa contagem.

### Lotes

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/ticket-types` | Lotes ativos, com `soldOut` e `remaining`, sem revelar volume de vendas | Público |
| GET | `/events/{eventId}/ticket-types/manage` | Todos os lotes com total, vendido e disponível | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/ticket-types` | Cria um lote | Dono do evento ou ADMIN |
| PATCH | `/events/{eventId}/ticket-types/{ticketTypeId}` | Altera nome, descrição, preço, quantidade total, limite por pedido e limite por comprador, inclusive de lote desativado | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/ticket-types/{ticketTypeId}/deactivate` | Desativa o lote para novas vendas | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/ticket-types/{ticketTypeId}/reactivate` | Reativa o lote | Dono do evento ou ADMIN |

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
  "maxPerOrder": 4,
  "maxPerBuyer": 6
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
    "remaining": 7,
    "maxPerBuyer": 6
  }
]
```

`remaining` só é preenchido quando restam 10 ingressos ou menos; nos demais casos, inclusive esgotado, vem `null`.

Regras de edição:

- `quantityTotal` não pode ficar abaixo do vendido mais o reservado (400 `invalid-ticket-type-quantity`).
- Mudar o preço não altera pedidos já feitos.
- Lotes de evento cancelado não podem ser editados (409 `invalid-event-state`).

Desativar e reativar:

- Lote desativado não aceita novas reservas (a condição está no próprio `UPDATE` da reserva) e some da lista pública, da busca e do preço mínimo. Continua na gestão (`/manage`, com `active: false`) e nos pedidos e ingressos já existentes.
- Pedidos pendentes com o lote podem ser pagos dentro do prazo da reserva, e os ingressos pagos continuam válidos.
- Desativar é permitido mesmo depois do início do evento e mesmo que seja o último lote ativo. Reativar depois do início responde 409 `sales-closed`.
- Evento cancelado responde 409 `invalid-event-state`; lote já desativado (ou já ativo) responde 409 `invalid-ticket-type-state`.
- As duas ações ficam na auditoria (`TICKET_TYPE_DEACTIVATED` e `TICKET_TYPE_REACTIVATED`).
- Nome repetido no mesmo evento responde 409 `ticket-type-name-already-exists`.
- No `PATCH`, campo ausente ou `null` não muda nada, então `maxPerOrder` não pode ser removido depois de definido. Para `maxPerBuyer`, `0` remove o limite.

Limite por comprador (`maxPerBuyer`, opcional, vale para lote pago ou gratuito):

- Contam os ingressos daquele lote em pedidos pendentes e pagos do comprador. Pedido expirado, cancelado ou reembolsado devolve.
- Ingresso recebido por transferência não conta para quem recebe, e quem transfere não recupera o limite.
- Uma compra que passaria do limite responde 409 `max-per-buyer-exceeded`, com `remaining` (quanto o comprador ainda pode comprar).
- Diminuir o limite abaixo do que um comprador já comprou não cancela nada; só impede novas compras.
- O limite é por conta: várias contas contornam.

### Cupons

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/coupons` | Cupons do evento com usos, pedidos pagos e desconto concedido | Dono do evento ou ADMIN |
| POST | `/events/{eventId}/coupons` | Cria um cupom percentual (`PERCENT`) ou de valor fixo (`FIXED`) | Dono do evento ou ADMIN |
| PUT | `/events/{eventId}/coupons/{couponId}` | Substitui tipo, valor, limite, limite por comprador, validade e ativo | Dono do evento ou ADMIN |
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
  "active": true,
  "maxUsesPerBuyer": 1
}
```

Regras do cupom:

- O código tem de 3 a 30 letras, números, `-` ou `_`. É gravado em maiúsculas, é único no evento e não muda depois de criado.
- Depois do primeiro uso, tipo e valor ficam fixos e o cupom não pode ser apagado, só desativado (409 `coupon-already-used`).
- `maxUsesPerBuyer` (opcional) limita os usos por comprador. Contam os pedidos pendentes, pagos e reembolsados com o cupom: expiração e cancelamento devolvem o uso, reembolso não, como no limite global (`maxUses`). Acima do limite, o pedido e a pré-visualização respondem 422 `coupon-buyer-limit-reached`. No `PUT`, `null` remove o limite.

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

Cupom inválido, expirado, inativo, esgotado ou de outro evento responde 422 `invalid-coupon`, sempre com a mesma mensagem. Depois do início do evento, a prévia responde 409 `sales-closed`.

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
  "eventCancelled": false,
  "eventId": "8c1d…",
  "eventName": "Festival de Inverno",
  "eventStartsAt": "2026-11-20T22:00:00Z"
}
```

`GET /orders` traz, em cada pedido, `id`, `status`, `totalAmount`, `createdAt`, `eventId`, `eventName` e `eventStartsAt`.

Quando o pedido fica `PAID` (pagamento aprovado ou pedido gratuito), o comprador recebe por e-mail os dados do evento, os ingressos (lote e código), o total pago e o link para `/tickets` do site, com o QR Code de cada ingresso (PNG) e o `.ics` do evento em anexo. O e-mail sai depois da confirmação, no idioma da conta, e uma falha no envio não afeta o pedido.

Regras do pedido:

- Todos os itens precisam ser do mesmo evento.
- O preço de cada item fica congelado no pedido.
- Pedido com total zero (lote gratuito ou desconto integral) já sai `PAID`, com os ingressos emitidos e sem pagamento.
- As vendas encerram no início do evento: a partir de `startsAt`, a criação do pedido responde 409 `sales-closed`.
- O pedido pendente expira sozinho depois de 15 minutos e devolve estoque e uso do cupom.
- Um pedido criado antes do início pode ser pago depois dele, enquanto a reserva não expirar.

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
  "eventId": "8c1d…",
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

Depois da transferência, quem recebe ganha um e-mail com quem transferiu, o evento, o lote e o QR Code novo em anexo; quem transferiu recebe uma confirmação de que o QR antigo deixou de valer. Cada e-mail sai no idioma da conta de quem recebe.

A transferência só vale para ingresso `VALID`, de evento não cancelado que ainda não começou, até 3 vezes por ingresso. Erros possíveis:

- 403 `invalid-password`;
- 404 `ticket-not-found`;
- 400 `self-transfer`;
- 409 `invalid-ticket-state`, `invalid-event-state`, `ticket-transfer-closed` ou `ticket-transfer-limit-reached`;
- 422 `transfer-recipient-unavailable`;
- 429 `too-many-transfer-attempts`.

**Check-in**

`POST /tickets/{code}/check-in` responde com o ingresso marcado como `USED`. Um código só passa uma vez. A segunda leitura responde 409 `invalid-ticket-state` com `reason` `already-used` e o momento do primeiro check-in em `usedAt`:

```json
{
  "type": "https://ticketfy-api.onrender.com/problems/invalid-ticket-state",
  "title": "Invalid ticket state",
  "status": 409,
  "detail": "Ticket was already used",
  "instance": "/tickets/K7Q2M9X4T8B3N6PA/check-in",
  "reason": "already-used",
  "usedAt": "2026-10-08T21:14:03.512Z"
}
```

Ingresso cancelado responde 409 `invalid-ticket-state` com `reason` `not-valid`, sem `usedAt`.

### Participantes

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/participants` | Ingressos do evento, paginados; filtros `ticketTypeId`, `status` (`VALID`, `USED`, `CANCELLED`) e `q` (nome do dono) | Dono do evento ou ADMIN |
| GET | `/events/{eventId}/participants.csv` | Mesma lista, com os mesmos filtros, em CSV | Dono do evento ou ADMIN |

```json
{
  "content": [
    {
      "ticketId": "7c1e…",
      "ticketTypeId": "ffe5…",
      "ticketTypeName": "Pista",
      "status": "USED",
      "checkedInAt": "2026-12-10T23:41:00Z",
      "transferred": true,
      "holderName": "Carla Dias",
      "codeSuffix": "AB12"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Regras da lista:

- Qualquer pessoa que não seja o dono do evento ou um ADMIN recebe 404 `event-not-found`.
- Só aparecem o nome do dono atual e os 4 últimos caracteres do código; o código inteiro, que é o próprio ingresso, nunca sai da API. E-mail e dados do comprador ficam de fora. Contas excluídas aparecem como `Usuário excluído`.
- A ordem é fixa, por nome e id do ingresso. A busca por nome não diferencia maiúsculas e trata `%` e `_` como texto.
- O CSV sai no idioma salvo na conta de quem pede (ver [Formato dos CSVs](#formato-dos-csvs)), protege contra fórmulas (células iniciadas por `=`, `+`, `-`, `@`, tabulação ou quebra de linha ganham um `'`) e é registrado na auditoria (`PARTICIPANTS_EXPORTED`, com a quantidade de linhas).
- Não há check-in a partir da lista: o check-in exige o código inteiro (`POST /tickets/{code}/check-in`).

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
| GET | `/organizer/ledger.csv` | Extrato em CSV no idioma da conta (ver [Formato dos CSVs](#formato-dos-csvs)), com os mesmos filtros | ORGANIZER |
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
| GET | `/admin/users?email=...` | Encontra qualquer conta não excluída, com suspensão e bloqueio de saques | ADMIN |
| POST | `/admin/users/{id}/suspension` | Suspende a conta (`{"reason": "..."}`) | ADMIN |
| DELETE | `/admin/users/{id}/suspension` | Reativa a conta | ADMIN |
| GET | `/admin/organizers?email=...` | Situação de saques de um organizador | ADMIN |
| POST | `/admin/organizers/{id}/payout-block` | Bloqueia os saques do organizador (`{"reason": "..."}`) | ADMIN |
| DELETE | `/admin/organizers/{id}/payout-block` | Desbloqueia os saques | ADMIN |
| GET | `/admin/audit` | Auditoria paginada; filtros `targetType`, `targetId`, `actorId`, `from`, `to` | ADMIN |
| PATCH | `/events/{id}/featured` | Destaque de evento (ver [Eventos](#eventos)) | ADMIN |

**Suspensão de conta**

```http
POST /admin/users/{id}/suspension
Authorization: Bearer <token>
Content-Type: application/json

{ "reason": "Chargebacks em análise" }
```

```json
{
  "id": "5a1e…",
  "name": "Bruno Lima",
  "email": "bruno@email.com",
  "role": "ORGANIZER",
  "createdAt": "2026-09-01T12:00:00Z",
  "suspended": true,
  "suspendedAt": "2026-10-12T15:00:00Z",
  "suspensionReason": "Chargebacks em análise",
  "suspendedBy": "9c2d…",
  "payoutsBlocked": false,
  "payoutBlockReason": null,
  "payoutBlockedAt": null
}
```

Regras da suspensão:

- O motivo é obrigatório, com até 500 caracteres, e fica só para o ADMIN e a auditoria; o usuário suspenso vê apenas que a conta está suspensa.
- A suspensão vale na hora: todas as sessões são revogadas, e login e renovação respondem 403 `account-suspended`. Não há data de término; a conta fica suspensa até a reativação.
- Pedidos pendentes do usuário são cancelados, com estoque e uso de cupom devolvidos. Ingressos pagos continuam válidos.
- Organizador suspenso: as vendas dos eventos dele ficam pausadas (`POST /orders` e prévia de cupom respondem 409 `sales-closed`, e os eventos saem da busca e dos destaques, com `salesClosed: true` no detalhe), e os saques solicitados não são processados até a reativação. `payoutsBlocked` continua indicando só o bloqueio manual.
- Conta ADMIN não é suspensa (409 `admin-account-suspension`). Suspender duas vezes, ou reativar quem não está suspenso, responde 409 `invalid-suspension-state`. Conta inexistente ou excluída responde 404 `user-not-found`.
- Suspensão e reativação ficam na auditoria (`ACCOUNT_SUSPENDED` e `ACCOUNT_REACTIVATED`), com o motivo.

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
