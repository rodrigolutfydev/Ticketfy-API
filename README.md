# Ticketfy

API para um sistema de venda de ingressos para eventos, em Spring Boot. Projeto de estudo pessoal, com foco em boas práticas de backend.

## Sobre o projeto

O Ticketfy simula o backend de uma plataforma de venda de ingressos, no estilo do Sympla ou Eventbrite: qualquer pessoa se cadastra para comprar, usuários interessados em produzir eventos viram organizadores, e compradores navegam pelo catálogo e acompanham seus pedidos.

O foco não é só fazer funcionar, mas aplicar separação de responsabilidades, validação, segurança, tratamento centralizado de erros, versionamento de schema e documentação de API.

Projeto em desenvolvimento ativo — veja o [Roadmap](#roadmap).

## Papéis e autorização

| Papel | O que faz |
|---|---|
| **USER** | Compra ingressos e acompanha os próprios pedidos. Atribuído a todo mundo no cadastro |
| **ORGANIZER** | Cria e gerencia os próprios eventos. Qualquer usuário autenticado se torna um, sem aprovação |
| **ADMIN** | Administra a plataforma inteira |

O papel nunca é aceito no cadastro: é sempre definido pelo servidor.

A autorização tem duas camadas: **por papel** ("você pode criar evento?"), resolvida com `@PreAuthorize`; e **por propriedade** ("esse evento é seu?"), que papel nenhum responde — dois organizadores têm o mesmo papel — e por isso vive na camada de serviço.

## Tecnologias

Java 17 · Spring Boot 4 · Spring Data JPA · Spring Security · PostgreSQL · Flyway · JWT (java-jwt) · Bean Validation · Springdoc OpenAPI · Lombok · Maven · Docker Compose

## Funcionalidades

**Usuários e autenticação** — cadastro com validação, senha com BCrypt, id em UUID, login com JWT de expiração configurável, rotas protegidas por filtro stateless, auto-promoção a organizador com reemissão de token. A aplicação recusa iniciar com uma chave de assinatura fraca.

**Eventos** — CRUD completo com autorização nas duas camadas, listagem paginada e ordenada, busca por nome e filtro por cidade combináveis, atualização parcial, exclusão lógica (preservando a integridade dos pedidos futuros), validação de coerência entre início e fim, e timestamps de criação e alteração.

**Erros** — respostas padronizadas em JSON, validação detalhada por campo, mensagem idêntica para credenciais inválidas (evitando enumeração de usuários) e erros inesperados logados sem expor detalhes internos.

**Planejado** — tipos de ingresso e precificação, fluxo de pedidos e pagamento, emissão e validação de ingressos.

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

A `JWT_SECRET` assina os tokens e precisa de no mínimo 32 caracteres. Não tem valor padrão de propósito: um segredo versionado no repositório permitiria a qualquer pessoa forjar tokens válidos.

O Flyway aplica as migrations ao iniciar. A API sobe em `http://localhost:8080` e o Swagger UI em `/swagger-ui/index.html`.

## Endpoints

### Implementados

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/users` | Cadastra um usuário | Público |
| POST | `/login` | Autentica e retorna um token JWT | Público |
| POST | `/users/me/organizer` | Torna o usuário autenticado um organizador | Autenticado |
| POST | `/events` | Cadastra um evento | ORGANIZER ou ADMIN |
| GET | `/events` | Lista os eventos ativos, paginado | Público |
| GET | `/events/{id}` | Detalha um evento | Público |
| PUT | `/events/{id}` | Atualiza um evento (parcial) | Dono ou ADMIN |
| DELETE | `/events/{id}` | Desativa um evento | Dono ou ADMIN |

Rotas protegidas esperam o token no header `Authorization`, no formato `Bearer <token>`.

A listagem aceita `q` (trecho do nome), `city` (exata), `page` (padrão `0`), `size` (padrão `20`) e `sort` (padrão `startsAt,asc`). Exemplo: `GET /events?q=rock&size=10`. A resposta traz `content` e um objeto `page` com `size`, `number`, `totalElements` e `totalPages`.

### Planejados

`GET /users/me` · `GET` e `POST /events/{id}/ticket-types` · `POST /orders` · `GET /orders/me` · `GET /orders/{id}`

## Respostas de erro

Todo erro é um JSON com a mesma forma:

```json
{ "message": "This email is already registered" }
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
| Dados ou parâmetro de rota inválidos | 400 |
| Credenciais inválidas, ou rota protegida sem token | 401 |
| Papel insuficiente, ou recurso de outro usuário | 403 |
| Recurso não encontrado | 404 |
| Conflito (ex: email já cadastrado) | 409 |
| Erro inesperado | 500 |

## Estrutura

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/      # configuração de segurança e beans
│   ├── security/    # login, token JWT, filtro de autenticação
│   └── exception/   # tratamento centralizado de erros
├── user/            # cadastro, papéis
├── event/           # eventos e seus endereços
└── ApiApplication

src/main/resources/
└── db/migration/    # migrations do Flyway
```

Ainda virão os pacotes `tickettype`, `ticket`, `order` e `payment`.

O projeto é organizado por domínio, não por camada: cada pasta reúne entity, repository, service, controller e DTOs daquele conceito de negócio.

O endereço do evento não é uma entidade separada. Seguindo o modelo do Sympla, o organizador informa local, endereço e cidade diretamente no evento, já que um evento pode acontecer em qualquer lugar.

## Roadmap

- [x] Cadastro de usuário + criptografia de senha
- [x] Login + autenticação JWT
- [x] Proteção de rotas via filtro de segurança
- [x] Tratamento centralizado de erros
- [x] Papel de organizador e auto-promoção
- [x] Domínio de eventos (CRUD, paginação, busca, exclusão lógica)
- [x] Autorização por propriedade
- [ ] Domínio de tipos de ingresso
- [ ] Fluxo de pedidos e pagamento
- [ ] Emissão e validação de ingressos
- [ ] Testes automatizados no fluxo de compra

## Licença

Licenciado sob a licença MIT — veja [LICENSE](LICENSE).