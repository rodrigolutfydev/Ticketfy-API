# Mudanças de contrato da API

Registro das mudanças que exigem ajuste no frontend, da mais recente para a mais antiga.
O contrato completo do dashboard do organizador está em [CONTRATO-DASHBOARD.md](CONTRATO-DASHBOARD.md).

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
