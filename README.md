# Ticketfy

API de venda de ingressos para eventos em Spring Boot. Projeto pessoal de estudo, com foco em boas práticas de backend.

## Sobre o projeto

Simula o backend de uma plataforma como Sympla ou Eventbrite: usuários compram ingressos, organizadores criam eventos e cada ingresso tem um código único validado no check-in.

O foco são os problemas reais de um sistema de ingressos: compra simultânea da última vaga, clique duplo, reserva abandonada, pagamento no instante da expiração e ingresso lido por dois porteiros. Cada caso é garantido no banco, não só no Java. Veja [Decisões técnicas](#decisões-técnicas).

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
                                    ├── check-in na entrada ──► ingresso USED
                                    └── reembolso (dentro do prazo) ──► pedido REFUNDED
```

## Papéis e autorização

| Papel | O que faz |
|---|---|
| **USER** | Compra ingressos e acompanha os próprios pedidos. Atribuído no cadastro |
| **ORGANIZER** | Gerencia os próprios eventos e lotes e faz o check-in deles. Qualquer usuário pode virar organizador |
| **ADMIN** | Administra a plataforma inteira |

O papel é sempre definido pelo servidor, nunca aceito no cadastro.

A autorização tem duas camadas: **por papel** ("pode criar evento?"), com `@PreAuthorize`, e **por propriedade** ("esse pedido é seu?"), que o papel não resolve e por isso fica no serviço, comparando o dono do recurso com o usuário autenticado.

## Tecnologias

Java 17 · Spring Boot 4 · Spring Data JPA · Hibernate 7 · Spring Security · PostgreSQL 16 · Flyway · JWT (java-jwt) · Bean Validation · Spring Scheduling · Springdoc OpenAPI · Lombok · Maven · Docker · Docker Compose · JUnit 5 · Testcontainers

## Funcionalidades

**Usuários.** Cadastro validado, senha com BCrypt, login com JWT, auto-promoção a organizador e limite de 5 tentativas de login por minuto por IP. A aplicação não inicia com chave de assinatura fraca.

**Eventos.** CRUD com busca, filtro por cidade, paginação, atualização parcial e exclusão lógica. Eventos com ingressos vendidos ou reservados não podem ser excluídos.

**Lotes.** Cada evento tem lotes com preço, quantidade e limite opcional por pedido. O disponível é calculado a partir do vendido, nunca armazenado.

**Pedidos.** Vários itens por pedido, preço congelado no momento da compra, chave de idempotência opcional e máquina de estados (`PENDING`, `PAID`, `EXPIRED`, `CANCELLED`, `REFUNDED`).

**Expiração.** Uma rotina expira a cada minuto os pedidos pendentes vencidos e devolve o estoque, um pedido por transação.

**Pagamento.** Simulado e isolado, para ser trocado por um gateway real. Aprovar, marcar como pago e emitir os ingressos acontecem na mesma transação.

**Ingressos.** Um por unidade comprada, com código único de 16 caracteres gerado por `SecureRandom`. O organizador do evento faz o check-in.

**Reembolso.** Permitido até um prazo configurável antes do evento. Cancela os ingressos e marca o pedido como `REFUNDED`. É recusado se algum ingresso já foi usado.

**Erros.** JSON padronizado, validação por campo e mensagem única para credenciais inválidas, o que evita descobrir quais emails estão cadastrados.

## Decisões técnicas

### Venda concorrente do último ingresso

Ler, conferir e gravar em passos separados deixa duas compras passarem. A reserva é uma operação única:

```sql
UPDATE ticket_types
SET quantity_sold = quantity_sold + :quantidade
WHERE id = :id AND quantity_sold + :quantidade <= quantity_total
```

Nenhuma linha afetada significa estoque insuficiente. Um teste com 10 threads disputando o último ingresso confirma que só uma compra passa.

### Clique duplo

Com o header `Idempotency-Key`, uma repetição devolve o pedido já criado. Se duas chegarem juntas, a constraint única barra a segunda, que recebe o pedido da primeira. A chave é vinculada ao dono.

### Expiração e pagamento simultâneos

Sem proteção, o pedido poderia ser pago com o estoque já devolvido. O lock otimista (`@Version`) faz a primeira gravação vencer e desfaz a outra. Se o pagamento perder, retorna `409`.

### Pagamento aprovado duas vezes

Um índice único parcial permite só um pagamento aprovado por pedido:

```sql
CREATE UNIQUE INDEX uk_payments_order_approved
    ON payments (order_id)
    WHERE status = 'APPROVED';
```

### Check-in duplicado

O check-in é um `UPDATE ... WHERE status = 'VALID'`. Com dois porteiros ao mesmo tempo, um recebe sucesso e o outro `409`.

### Reembolso com ingresso usado

Os ingressos são cancelados por um `UPDATE` que só atinge os `VALID`. Se o número de linhas afetadas for menor que o total do pedido, algum já foi usado, e tudo é desfeito com `409`.

### Exclusão de evento com vendas

Antes de desativar, o serviço verifica se algum lote tem vendas, incluindo reservas pendentes. A checagem vem depois da de dono, para que terceiros recebam `403` sem descobrir se há vendas.

### Datas e fuso horário

Datas são `Instant` no Java e `TIMESTAMPTZ` no banco. Assim o horário é um momento absoluto e não muda quando servidor e banco estão em fusos diferentes.

### Dinheiro

`BigDecimal` no Java e `NUMERIC(10,2)` no banco, nunca `double`. O valor cobrado vem do pedido, nunca do cliente.

### Consultas N+1

Detalhe do pedido e listagem de ingressos usam `@EntityGraph` para buscar tudo numa consulta com join.

## Como rodar

Pré-requisitos: Java 17+ e Docker. O `./mvnw` dispensa instalar o Maven.

```bash
git clone https://github.com/rodrigolutfydev/Ticketfy-API.git
cd Ticketfy-API
```

Crie um `.env` na raiz (não versionado):

```
DB_PASSWORD=sua_senha
JWT_SECRET=gere_com_openssl_rand_base64_32
```

A `JWT_SECRET` precisa de 32+ caracteres e não tem padrão, para não existir segredo versionado. Gere com `openssl rand -base64 32`.

**Opção 1: API pelo Maven**

```bash
docker compose up -d
set -a; source .env; set +a
./mvnw spring-boot:run
```

**Opção 2: tudo pelo Docker** (perfil `prod`)

```bash
docker compose --profile app up -d --build
```

A API sobe em `http://localhost:8080`, com o Flyway aplicando as migrations. No perfil `dev`, o Swagger fica em `/swagger-ui/index.html`.

### Testes

```bash
./mvnw test
```

Usam Testcontainers com um PostgreSQL descartável. O Docker precisa estar rodando.

### Perfis

| Perfil | Uso | Diferenças |
|---|---|---|
| `dev` (padrão) | Local | SQL no log, Swagger habilitado |
| `prod` | Docker e deploy | Sem Swagger, sem stack trace nas respostas, suporte a proxy reverso |

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketfy` | URL do banco |
| `DB_USERNAME` | `ticketfy_user` | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco (obrigatória) |
| `JWT_SECRET` | — | Chave dos tokens (obrigatória) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil da aplicação |
| `CORS_ALLOWED_ORIGINS` | — | Endereço do frontend autorizado |
| `ORDER_RESERVATION_MINUTES` | `15` | Duração da reserva de um pedido pendente |

O prazo de reembolso fica em `ticketfy.refund.deadline-hours`. Para testar a expiração rápido, use `ORDER_RESERVATION_MINUTES=1`.

## Endpoints

Rotas protegidas usam o header `Authorization: Bearer <token>`. Datas trafegam em ISO 8601 com fuso, em UTC: `"2026-12-10T23:00:00Z"` é 20h em Brasília.

### Usuários e autenticação

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/users` | Cadastra um usuário | Público |
| POST | `/login` | Autentica e retorna um token JWT | Público |
| GET | `/users/me` | Dados e papel do usuário autenticado | Autenticado |
| POST | `/users/me/organizer` | Torna o usuário organizador e devolve um token novo | Autenticado |

### Eventos

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/events` | Cadastra um evento | ORGANIZER ou ADMIN |
| GET | `/events` | Lista os eventos ativos, paginado | Público |
| GET | `/events/mine` | Lista os eventos do usuário, paginado | Autenticado |
| GET | `/events/{id}` | Detalha um evento, com o organizador | Público |
| PUT | `/events/{id}` | Atualiza um evento (parcial) | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento sem vendas | Dono ou ADMIN |

A listagem aceita `q` (nome), `city`, `page`, `size` (padrão `20`, máximo `100`) e `sort` (padrão `startsAt,asc`). Exemplo: `GET /events?q=rock&size=10`.

### Lotes de ingresso

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/events/{eventId}/ticket-types` | Cria um lote no evento | Organizador do evento ou ADMIN |
| GET | `/events/{eventId}/ticket-types` | Lista os lotes, com o disponível | Público |

### Pedidos, pagamento e reembolso

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/orders` | Cria um pedido e reserva o estoque | Autenticado |
| GET | `/orders` | Lista os pedidos do usuário, paginado | Autenticado |
| GET | `/orders/{id}` | Detalha um pedido | Dono ou ADMIN |
| DELETE | `/orders/{id}` | Cancela um pedido pendente | Dono ou ADMIN |
| POST | `/orders/{orderId}/payment` | Paga (simulado) e emite os ingressos | Dono |
| POST | `/orders/{id}/refund` | Reembolsa um pedido pago, dentro do prazo | Dono |

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

## Respostas de erro

Todo erro segue o mesmo formato:

```json
{ "message": "Not enough tickets available for this ticket type" }
```

Erros de validação listam os campos:

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
| Dados inválidos ou quantidade acima do limite | 400 |
| Credenciais inválidas ou sem token | 401 |
| Papel insuficiente ou recurso de outro usuário | 403 |
| Recurso ou rota inexistente | 404 |
| Método HTTP não suportado | 405 |
| Conflito: email em uso, estoque insuficiente, estado inválido do pedido, ingresso já usado, reembolso negado, evento com vendas, alteração concorrente | 409 |
| Excesso de tentativas de login | 429 |
| Erro inesperado | 500 |

## Estrutura

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/      # segurança, CORS e agendamento
│   ├── security/    # login, token JWT e filtro de autenticação
│   └── exception/   # tratamento centralizado de erros
├── user/            # cadastro e papéis
├── event/           # eventos e endereços
├── tickettype/      # lotes e estoque
├── order/           # pedidos, expiração e reembolso
├── payment/         # pagamento simulado
├── ticket/          # emissão e check-in
└── ApiApplication

src/main/resources/
├── application.properties        # configuração comum
├── application-dev.properties
├── application-prod.properties
└── db/migration/                 # V1 a V6: users, events, ticket_types,
                                  # orders/order_items, payments, tickets

src/test/java/com/lutfy/ticketfy/
├── IntegrationTestBase                 # container PostgreSQL compartilhado
└── OrderConcurrencyIntegrationTest     # disputa pelo último ingresso
```

O código é organizado por domínio, não por camada. O endereço fica no próprio evento, como no Sympla. Pedidos e pagamentos não têm setter de status: o estado só muda por métodos de transição como `markAsPaid()`, `cancel()` e `expire()`.

## Roadmap

- [x] Cadastro, login e autenticação JWT
- [x] Tratamento centralizado de erros
- [x] Papel de organizador e autorização por propriedade
- [x] Eventos e lotes com reserva de estoque atômica
- [x] Pedidos com preço congelado, idempotência e expiração
- [x] Pagamento simulado, ingressos e check-in
- [x] Reembolso
- [x] Proteção contra condições de corrida, com teste de concorrência
- [x] Perfis dev e prod e containerização
- [x] Limite de tentativas de login
- [x] Datas com fuso horário
- [x] Bloqueio de exclusão de eventos com vendas
- [ ] Health check e graceful shutdown
- [ ] Integração contínua (CI)
- [ ] Deploy
- [ ] Mais testes automatizados
- [ ] Gateway de pagamento real

## Licença

MIT. Veja [LICENSE](LICENSE).
