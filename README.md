# Ticketfy

API para um sistema de venda de ingressos para eventos, em Spring Boot. Projeto de estudo pessoal, com foco em boas práticas de backend.

## Sobre o projeto

O Ticketfy simula o backend de uma plataforma de venda de ingressos, no estilo do Sympla ou Eventbrite: qualquer pessoa se cadastra para comprar, usuários interessados em produzir eventos viram organizadores, e compradores escolhem ingressos, pagam, recebem um código único por ingresso e entram no evento com check-in.

O foco não é só fazer funcionar, mas resolver os problemas de um sistema de ingressos real: duas pessoas comprando a última vaga ao mesmo tempo, clique duplo gerando pedido duplicado, reserva abandonada segurando estoque, pagamento chegando junto com a expiração do pedido e o mesmo ingresso lido por dois porteiros. Cada caso tem uma garantia no banco, não só um `if` no Java — veja [Decisões técnicas](#decisões-técnicas).

Projeto em desenvolvimento ativo — veja o [Roadmap](#roadmap).

## Fluxo de compra

```
Organizador cria evento ──► cria lotes com preço e quantidade
                                    │
Comprador cria pedido ──────────────┘  estoque reservado por tempo limitado
        │
        ├── abandona ──► rotina expira o pedido e devolve o estoque
        ├── cancela ───► estoque devolvido
        └── paga ──────► pedido PAID + um ingresso emitido por unidade
                                    │
                         check-in na entrada ──► ingresso USED
```

## Papéis e autorização

| Papel | O que faz |
|---|---|
| **USER** | Compra ingressos e acompanha os próprios pedidos e ingressos. Atribuído a todo mundo no cadastro |
| **ORGANIZER** | Gerencia os próprios eventos e lotes e faz o check-in dos ingressos desses eventos. Qualquer usuário se torna um, sem aprovação |
| **ADMIN** | Administra a plataforma inteira |

O papel nunca é aceito no cadastro: é sempre definido pelo servidor.

A autorização tem duas camadas: **por papel** ("você pode criar evento?"), com `@PreAuthorize`; e **por propriedade** ("esse pedido é seu?"), que papel nenhum responde — dois compradores têm o mesmo papel — e por isso vive na camada de serviço.

## Tecnologias

Java 17 · Spring Boot 4 · Spring Data JPA · Hibernate 7 · Spring Security · PostgreSQL 16 · Flyway · JWT (java-jwt) · Bean Validation · Spring Scheduling · Springdoc OpenAPI · Lombok · Maven · Docker Compose

## Funcionalidades

**Usuários e autenticação** — cadastro com validação, senha com BCrypt, id em UUID, login com JWT de expiração configurável, filtro stateless e auto-promoção a organizador com reemissão de token. A aplicação recusa iniciar com uma chave de assinatura fraca.

**Eventos** — CRUD com autorização nas duas camadas, listagem paginada com busca por nome e filtro por cidade, atualização parcial, exclusão lógica, validação de início e fim, e timestamps de criação e alteração.

**Lotes de ingresso** — cada evento tem um ou mais lotes (Pista, VIP, Camarote), com preço, quantidade total e limite opcional por pedido. O estoque é contado pela quantidade vendida, e o disponível é sempre calculado, nunca armazenado — assim ampliar um lote não gera divergência.

**Pedidos** — vários itens por pedido, de lotes diferentes. O preço unitário é congelado no item no momento da compra. O pedido é uma máquina de estados (`PENDING`, `PAID`, `EXPIRED`, `CANCELLED`, `REFUNDED`), com prazo de reserva configurável, chave de idempotência opcional e cancelamento pelo dono enquanto pendente, devolvendo o estoque.

**Expiração de reservas** — uma rotina agendada roda a cada minuto, marca como `EXPIRED` os pedidos pendentes vencidos e devolve o estoque. Cada pedido expira na própria transação: um conflito afeta só aquele pedido.

**Pagamento** — simulado, pensado para ser trocado por um gateway real sem mudar o resto. Aprovar o pagamento, marcar o pedido como pago e emitir os ingressos acontecem na mesma transação. Pedidos expirados, cancelados, já pagos ou de outro usuário são recusados.

**Ingressos e check-in** — um ingresso por unidade comprada, com código de 16 caracteres gerado por `SecureRandom`, sem letras ambíguas e único no banco. O comprador lista os próprios ingressos, e o organizador do evento faz o check-in.

**Erros** — respostas padronizadas em JSON, validação detalhada por campo, mensagem idêntica para credenciais inválidas (evitando enumeração de usuários) e erros inesperados logados sem expor detalhes internos.

## Decisões técnicas

### Venda concorrente do último ingresso

Ler o estoque, conferir no Java e depois gravar deixa duas compras simultâneas passarem. A reserva é uma operação única no banco:

```sql
UPDATE ticket_types
SET quantity_sold = quantity_sold + :quantidade
WHERE id = :id AND quantity_sold + :quantidade <= quantity_total
```

Se nenhuma linha for afetada, não havia estoque e a compra falha. Sem consulta prévia e sem lock explícito.

### Compra duplicada por clique duplo

O cliente pode enviar o header `Idempotency-Key`. Uma requisição repetida com a mesma chave devolve o pedido já criado, sem reservar estoque de novo. Se duas chegarem juntas, a segunda bate na constraint única da chave e recebe o pedido da primeira. A chave confere o dono, então ninguém recupera o pedido de outra pessoa reusando uma chave.

### Expiração e pagamento ao mesmo tempo

Sem proteção, um pedido poderia ser pago no mesmo instante em que expira, ficando pago com o estoque já devolvido — e a vaga seria vendida duas vezes. O pedido tem lock otimista (`@Version`): quem grava primeiro vence, e a outra operação é desfeita por inteiro. Se o pagamento perder, o cliente recebe `409`.

### Pagamento aprovado duas vezes

Um pedido pode ter várias tentativas de pagamento, mas só uma aprovada, garantido por um índice único parcial:

```sql
CREATE UNIQUE INDEX uk_payments_order_approved
    ON payments (order_id)
    WHERE status = 'APPROVED';
```

### Check-in duplicado

O check-in é um `UPDATE ... WHERE status = 'VALID'`. Se dois porteiros lerem o mesmo código ao mesmo tempo, um recebe sucesso e o outro recebe `409`.

### Dinheiro

Valores monetários usam `BigDecimal` no Java e `NUMERIC(10,2)` no banco, nunca `double`. O valor do pagamento vem do pedido, nunca do cliente.

## Como rodar

Pré-requisitos: Java 17+, Maven (ou o `./mvnw` incluído) e Docker Compose.

```bash
git clone https://github.com/rodrigolutfydev/Ticketfy-API.git
cd Ticketfy-API
```

Crie um `.env` na raiz com a senha do banco (o arquivo não é versionado; o Compose o lê automaticamente):

```
DB_PASSWORD=sua_senha
```

Suba o banco, exporte as variáveis e rode:

```bash
docker compose up -d

export DB_PASSWORD=sua_senha
export JWT_SECRET=$(openssl rand -base64 32)

./mvnw spring-boot:run
```

A `JWT_SECRET` precisa de no mínimo 32 caracteres e não tem valor padrão de propósito: um segredo versionado permitiria a qualquer pessoa forjar tokens válidos.

O Flyway aplica as migrations ao iniciar. A API sobe em `http://localhost:8080` e o Swagger UI em `/swagger-ui/index.html`.

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketfy` | URL do banco |
| `DB_USERNAME` | `ticketfy_user` | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco (obrigatória) |
| `JWT_SECRET` | — | Chave de assinatura dos tokens (obrigatória) |
| `ORDER_RESERVATION_MINUTES` | `15` | Tempo que um pedido pendente segura o estoque |

Para testar a expiração sem esperar, rode com `ORDER_RESERVATION_MINUTES=1`.

## Endpoints

Rotas protegidas esperam o token no header `Authorization`, no formato `Bearer <token>`.

### Usuários e autenticação

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/users` | Cadastra um usuário | Público |
| POST | `/login` | Autentica e retorna um token JWT | Público |
| POST | `/users/me/organizer` | Torna o usuário autenticado um organizador | Autenticado |

### Eventos

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/events` | Cadastra um evento | ORGANIZER ou ADMIN |
| GET | `/events` | Lista os eventos ativos, paginado | Público |
| GET | `/events/{id}` | Detalha um evento | Público |
| PUT | `/events/{id}` | Atualiza um evento (parcial) | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento | Dono ou ADMIN |

A listagem aceita `q` (trecho do nome), `city` (exata), `page` (padrão `0`), `size` (padrão `20`) e `sort` (padrão `startsAt,asc`). Exemplo: `GET /events?q=rock&size=10`. A resposta traz `content` e um objeto `page` com `size`, `number`, `totalElements` e `totalPages`.

### Lotes de ingresso

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/events/{eventId}/ticket-types` | Cria um lote no evento | Organizador do evento ou ADMIN |
| GET | `/events/{eventId}/ticket-types` | Lista os lotes do evento, com o disponível | Público |

### Pedidos e pagamento

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/orders` | Cria um pedido e reserva o estoque | Autenticado |
| GET | `/orders` | Lista os pedidos do usuário, paginado | Autenticado |
| GET | `/orders/{id}` | Detalha um pedido | Dono ou ADMIN |
| DELETE | `/orders/{id}` | Cancela um pedido pendente | Dono ou ADMIN |
| POST | `/orders/{orderId}/payment` | Paga o pedido (simulado) e emite os ingressos | Dono |

`POST /orders` aceita o header opcional `Idempotency-Key`. Corpo:

```json
{
  "items": [
    { "ticketTypeId": "ffe5dc69-f145-484a-8b47-51700a8b5414", "quantity": 2 }
  ]
}
```

### Ingressos

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/tickets/me` | Lista os ingressos do usuário, paginado | Autenticado |
| POST | `/tickets/{code}/check-in` | Valida o ingresso na entrada | Organizador do evento ou ADMIN |

### Planejados

`POST /orders/{id}/refund` · `GET /users/me`

## Respostas de erro

Todo erro é um JSON com a mesma forma:

```json
{ "message": "Not enough tickets available for this ticket type" }
```

Erros de validação incluem os campos que falharam:

```json
{
  "message": "Validation failed",
  "errors": [
    { "field": "password", "message": "size must be between 8 and 72" }
  ]
}
```

| Situação | Status |
|----------|--------|
| Dados inválidos, ou quantidade acima do limite por pedido | 400 |
| Credenciais inválidas, ou rota protegida sem token | 401 |
| Papel insuficiente, ou recurso de outro usuário | 403 |
| Recurso não encontrado | 404 |
| Conflito: email já cadastrado, estoque insuficiente, estado inválido do pedido, ingresso já usado, alteração concorrente | 409 |
| Erro inesperado | 500 |

## Estrutura

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/      # segurança e agendamento
│   ├── security/    # login, token JWT, filtro de autenticação
│   └── exception/   # tratamento centralizado de erros
├── user/            # cadastro, papéis
├── event/           # eventos e seus endereços
├── tickettype/      # lotes, reserva e devolução de estoque
├── order/           # pedidos, máquina de estados, expiração
├── payment/         # pagamento simulado
├── ticket/          # emissão e check-in
└── ApiApplication

src/main/resources/
└── db/migration/
    ├── V1  users
    ├── V2  events
    ├── V3  ticket_types
    ├── V4  orders e order_items
    ├── V5  payments
    └── V6  tickets
```

O projeto é organizado por domínio, não por camada: cada pasta reúne entity, repository, service, controller e DTOs daquele conceito de negócio.

O endereço do evento não é uma entidade separada. Seguindo o modelo do Sympla, o organizador informa local, endereço e cidade no próprio evento.

Pedidos e pagamentos não têm setter de status: o estado só muda pelos métodos de transição (`markAsPaid()`, `cancel()`, `expire()`, `approve()`), que concentram as regras de cada mudança.

## Roadmap

- [x] Cadastro de usuário + criptografia de senha
- [x] Login + autenticação JWT
- [x] Proteção de rotas via filtro de segurança
- [x] Tratamento centralizado de erros
- [x] Papel de organizador e auto-promoção
- [x] Domínio de eventos (CRUD, paginação, busca, exclusão lógica)
- [x] Autorização por propriedade
- [x] Lotes de ingresso com reserva de estoque atômica
- [x] Pedidos com preço congelado e idempotência
- [x] Expiração automática de reservas
- [x] Pagamento simulado
- [x] Emissão de ingressos e check-in
- [x] Proteção contra condições de corrida
- [ ] Reembolso
- [ ] Testes automatizados no fluxo de compra, incluindo concorrência
- [ ] Perfis de desenvolvimento e produção
- [ ] Integração com gateway de pagamento real
- [ ] Containerização da API e deploy

## Licença

Licenciado sob a licença MIT — veja [LICENSE](LICENSE).
