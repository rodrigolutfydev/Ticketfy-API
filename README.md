# Ticketfy API

API REST de venda de ingressos para eventos, construída com Java e Spring Boot. O projeto simula o backend de uma plataforma : organizadores publicam eventos e lotes, compradores fazem pedidos e pagam, e cada ingresso recebe um código único validado na entrada.

O foco está nos problemas reais de um sistema de ingressos: duas pessoas comprando a última vaga ao mesmo tempo, clique duplo, reserva abandonada, pagamento no instante da expiração e o mesmo ingresso lido por dois porteiros. Cada caso tem uma garantia no banco de dados, e não só um `if` no Java.

**Em produção:** API no Render, banco PostgreSQL no Neon, testes automatizados no GitHub Actions.

---

## Tecnologias

| Tecnologia | Detalhes |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 4.1 |
| Persistência | Spring Data JPA + Hibernate 7 |
| Banco de dados | PostgreSQL 16 |
| Migrations | Flyway |
| Validação | Jakarta Bean Validation |
| Segurança | Spring Security + JWT (java-jwt) + BCrypt |
| Agendamento | Spring Scheduling |
| Observabilidade | Spring Boot Actuator (health check) |
| Documentação | Springdoc OpenAPI (Swagger, perfil `dev`) |
| Testes | JUnit 5 + Testcontainers |
| Boilerplate | Lombok |
| Build | Maven (wrapper incluído) |
| Containerização | Docker (multi-stage) + Docker Compose |
| CI | GitHub Actions |
| Deploy | Render (API) + Neon (PostgreSQL) |

---

## Estrutura do projeto

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/         Segurança, CORS e agendamento
│   ├── security/       Login, token JWT, filtro de autenticação, limite de tentativas
│   └── exception/      Tratamento centralizado de erros
├── user/               Cadastro, papéis e perfil
├── event/              Eventos
├── tickettype/         Lotes e controle de estoque
├── order/              Pedidos, expiração e reembolso
├── payment/            Pagamento simulado
├── ticket/             Emissão de ingressos e check-in
└── ApiApplication

src/main/resources/
├── application.properties          Configuração comum
├── application-dev.properties      Perfil de desenvolvimento
├── application-prod.properties     Perfil de produção
└── db/migration/                   V1 a V6

src/test/java/com/lutfy/ticketfy/
├── IntegrationTestBase                 Container PostgreSQL compartilhado
└── OrderConcurrencyIntegrationTest     Disputa pelo último ingresso
```

O código é organizado por domínio, e não por camada: cada pasta reúne entidade, repositório, serviço, controller e DTOs daquele conceito de negócio.

---

## Decisões técnicas

| Problema | Solução |
|---|---|
| Venda simultânea do último ingresso | `UPDATE` condicional em `quantity_sold`, verificado por teste com 10 threads |
| Clique duplo em "comprar" | Header `Idempotency-Key` com constraint única, vinculada ao dono |
| Pagamento e expiração ao mesmo tempo | Lock otimista (`@Version`) no pedido |
| Dois pagamentos aprovados | Índice único parcial em `payments` |
| Check-in duplicado | `UPDATE ... WHERE status = 'VALID'` |
| Reembolso com ingresso já usado | `UPDATE` condicional + contagem; diverge, desfaz tudo com `409` |
| Excluir evento com vendas | Recusado com `409`, depois da checagem de dono |
| Comprar lote de evento excluído | Busca do lote exige lote e evento ativos |
| Dinheiro | `BigDecimal` e `NUMERIC(10,2)`; o valor cobrado vem do pedido |
| Fuso horário | `Instant` e `TIMESTAMPTZ` |
| Consultas N+1 | `@EntityGraph` no detalhe do pedido e na lista de ingressos |

O raciocínio completo de cada decisão está no documento de arquitetura do projeto.

---

## Endpoints

Rotas protegidas exigem o header `Authorization: Bearer <token>`. Datas trafegam em ISO 8601 UTC: `"2026-12-10T23:00:00Z"` é 20h em Brasília.

### Usuários e autenticação

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/users` | Cadastra um usuário | Público |
| POST | `/login` | Autentica e retorna um token JWT | Público |
| GET | `/users/me` | Dados e papel do usuário autenticado | Autenticado |
| POST | `/users/me/organizer` | Torna o usuário organizador e devolve um token novo | Autenticado |

### Eventos — `/events`

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events` | Lista eventos ativos, com `q`, `city` e paginação | Público |
| GET | `/events/{id}` | Detalha um evento | Público |
| GET | `/events/mine` | Lista os eventos do usuário | Autenticado |
| POST | `/events` | Cria um evento | ORGANIZER ou ADMIN |
| PUT | `/events/{id}` | Atualiza um evento (parcial) | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento sem vendas | Dono ou ADMIN |

### Lotes — `/events/{eventId}/ticket-types`

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/events/{eventId}/ticket-types` | Lista os lotes ativos (esgotado/restante, sem revelar volume de vendas) | Público |
| POST | `/events/{eventId}/ticket-types` | Cria um lote | Dono do evento ou ADMIN |

### Pedidos — `/orders`

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| POST | `/orders` | Cria um pedido e reserva o estoque | Autenticado |
| GET | `/orders` | Lista os pedidos do usuário | Autenticado |
| GET | `/orders/{id}` | Detalha um pedido | Dono ou ADMIN |
| DELETE | `/orders/{id}` | Cancela um pedido pendente | Dono ou ADMIN |
| POST | `/orders/{id}/payment` | Paga (simulado) e emite os ingressos | Dono |
| POST | `/orders/{id}/refund` | Reembolsa dentro do prazo | Dono |

### Ingressos — `/tickets`

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/tickets/me` | Lista os ingressos do usuário | Autenticado |
| POST | `/tickets/{code}/check-in` | Valida o ingresso na entrada | Organizador do evento ou ADMIN |

### Saúde

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| GET | `/actuator/health` | Estado da API e da conexão com o banco | Público |

Listagens aceitam `page`, `size` (padrão 20, máximo 100) e `sort`.

---

## Como rodar

### Pré-requisitos

- Java 17+
- Docker e Docker Compose

### Configuração

Crie um `.env` na raiz (não versionado):

```
DB_PASSWORD=sua_senha
JWT_SECRET=gere_com_openssl_rand_base64_32
```

A `JWT_SECRET` precisa de 32 ou mais caracteres e não tem valor padrão, para que nenhum segredo fique no repositório.

### Rodar com Maven (banco no Docker)

```bash
git clone https://github.com/rodrigolutfydev/Ticketfy-API.git
cd Ticketfy-API
docker compose up -d
set -a; source .env; set +a
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`, o Flyway cria as tabelas, e o Swagger fica em `/swagger-ui/index.html`.

### Rodar tudo com Docker (perfil `prod`)

```bash
docker compose --profile app up -d --build
```

### Testes

```bash
./mvnw test
```

Os testes de integração sobem um PostgreSQL descartável com Testcontainers, então o Docker precisa estar rodando. Os mesmos testes rodam no GitHub Actions a cada push.

### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketfy` | URL do banco |
| `DB_USERNAME` | `ticketfy_user` | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco (obrigatória) |
| `JWT_SECRET` | — | Chave de assinatura dos tokens (obrigatória) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil (`dev` ou `prod`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origens autorizadas, separadas por vírgula |
| `ORDER_RESERVATION_MINUTES` | `15` | Duração da reserva de um pedido |
| `REFUND_DEADLINE_HOURS` | `48` | Antecedência mínima para reembolso |
| `LOGIN_MAX_ATTEMPTS` | `5` | Tentativas de login por janela |
| `LOGIN_WINDOW_SECONDS` | `60` | Duração da janela de tentativas |

---

## Exemplos de requisição

**Cadastrar usuário**
```json
POST /users

{
  "name": "Ana Souza",
  "email": "ana@email.com",
  "password": "senhaForte123"
}
```

**Login**
```json
POST /login

{
  "email": "ana@email.com",
  "password": "senhaForte123"
}
```

**Criar evento** (depois de `POST /users/me/organizer`)
```json
POST /events

{
  "name": "Festival de Verão",
  "description": "Três palcos e praça de alimentação",
  "venueName": "Marina da Glória",
  "address": "Av. Infante Dom Henrique, s/n",
  "city": "Rio de Janeiro",
  "state": "RJ",
  "startsAt": "2026-12-10T23:00:00Z",
  "endsAt": "2026-12-11T04:00:00Z"
}
```

**Criar lote**
```json
POST /events/{eventId}/ticket-types

{
  "name": "Pista",
  "description": "Primeiro lote",
  "price": 80.00,
  "quantityTotal": 500,
  "maxPerOrder": 4
}
```

**Listar lotes (público)**
```json
GET /events/{eventId}/ticket-types

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
`remaining` só é preenchido quando restam 10 ingressos ou menos; nos demais casos (inclusive esgotado) vem `null`. A quantidade total e a vendida não são expostas nesta rota.

**Comprar**
```json
POST /orders
Idempotency-Key: 9d4c1e77-2b1f-4c3a-9e0d-5a7b8c6d1e2f

{
  "items": [
    { "ticketTypeId": "ffe5dc69-f145-484a-8b47-51700a8b5414", "quantity": 2 }
  ]
}
```

**Erro: estoque insuficiente**
```json
409 Conflict

{
  "message": "Not enough tickets available for this ticket type"
}
```

**Erro de validação**
```json
400 Bad Request

{
  "message": "Validation failed",
  "errors": [
    { "field": "password", "message": "size must be between 8 and 72" }
  ]
}
```

---

## Roadmap

### Nível 1 — Fundação (concluído)
- [x] Cadastro com BCrypt, login JWT e filtro stateless
- [x] Papéis USER, ORGANIZER e ADMIN, com auto-promoção a organizador
- [x] Autorização por papel e por propriedade do recurso
- [x] CRUD de eventos com busca, filtro por cidade, paginação e exclusão lógica
- [x] Lotes com preço, quantidade e limite por pedido
- [x] Tratamento centralizado de erros com corpo padronizado

### Nível 2 — Regras de negócio (concluído)
- [x] Pedidos com vários itens e preço congelado
- [x] Reserva de estoque atômica por `UPDATE` condicional
- [x] Idempotência contra pedido duplicado
- [x] Expiração automática de reservas, com devolução de estoque
- [x] Pagamento simulado e emissão de ingressos na mesma transação
- [x] Check-in protegido contra leitura dupla
- [x] Reembolso com prazo e bloqueio para ingresso usado
- [x] Bloqueio de exclusão de evento com vendas e de compra em evento excluído

### Nível 3 — Pronto para produção (concluído)
- [x] Migrations com Flyway
- [x] Perfis `dev` e `prod`, com segredos em variáveis de ambiente
- [x] Imagem Docker multi-stage com usuário sem privilégios
- [x] Teste de integração de concorrência com Testcontainers
- [x] CI no GitHub Actions
- [x] Limite de tentativas de login
- [x] Datas com fuso horário (`Instant` e `TIMESTAMPTZ`)
- [x] Health check e encerramento gracioso
- [x] Deploy no Render com PostgreSQL no Neon

### Nível 4 — Próximos passos
- [ ] Frontend React publicado na Vercel
- [ ] Envio do ingresso por e-mail com QR code
- [ ] Recuperação de senha por e-mail
- [ ] Pagamento via Pix com webhook (Mercado Pago, sandbox)
- [ ] Padrão outbox para efeitos colaterais assíncronos
- [ ] Mais testes: pagamento x expiração, check-in, reembolso e controllers

### Futuro
- [ ] Confirmação de e-mail no cadastro
- [ ] Painel de vendas do organizador
- [ ] Virada de lote por data, meia-entrada e cupons
- [ ] Transferência de ingresso entre usuários
- [ ] Erros no padrão RFC 9457 (`ProblemDetail`)

---

## Licença

MIT. Veja [LICENSE](LICENSE).
