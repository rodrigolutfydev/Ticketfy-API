# Ticketfy

API para um sistema de venda de ingressos para eventos, desenvolvida em Spring Boot. O projeto é construído como parte de um estudo pessoal/acadêmico, com foco em aplicar boas práticas de desenvolvimento backend.

## Sobre o projeto

O Ticketfy simula o backend de uma plataforma de venda de ingressos, no estilo de sistemas como Sympla ou Eventbrite. A proposta é permitir que qualquer pessoa se cadastre para comprar ingressos, que usuários interessados em produzir eventos se tornem organizadores e publiquem seus próprios eventos, e que os compradores naveguem pelo catálogo, escolham um tipo de ingresso e acompanhem seus pedidos.

Por ser um projeto acadêmico, o foco não é só "fazer funcionar", mas também aplicar conceitos importantes de backend: separação de responsabilidades (Controller → Service → Repository), validação de dados, segurança (senhas criptografadas, autenticação via token, autorização por papel e por propriedade), tratamento centralizado de erros, versionamento de schema de banco de dados e documentação de API.

O projeto está em desenvolvimento ativo. Veja o [Roadmap](#roadmap) para saber o que já está pronto e o que ainda falta.

## Papéis de usuário

O sistema trabalha com três papéis:

**USER** — o comprador. É o papel atribuído a todo mundo que se cadastra. Navega pelos eventos, compra ingressos e acompanha os próprios pedidos.

**ORGANIZER** — cria e gerencia os próprios eventos, e acompanha as vendas deles. Não tem acesso a eventos de outros organizadores. Seguindo o modelo do Sympla, qualquer usuário autenticado pode se tornar organizador, sem aprovação prévia.

**ADMIN** — administra a plataforma inteira: qualquer evento, qualquer usuário, qualquer pedido.

O papel nunca é aceito no cadastro: ele é sempre definido pelo servidor.

## Tecnologias utilizadas

- **Java 17**
- **Spring Boot 4**
- **Spring Data JPA / Hibernate**
- **Spring Security**
- **PostgreSQL**
- **Flyway** — versionamento de banco de dados (migrations)
- **JWT (java-jwt, da Auth0)** — autenticação baseada em token
- **Bean Validation (Jakarta Validation)** — validação dos dados recebidos pela API
- **Springdoc OpenAPI / Swagger UI** — documentação interativa da API
- **Lombok**
- **Maven**

## Funcionalidades

### Já implementado

**Usuários e autenticação**

- Cadastro de usuário com validação dos dados de entrada (`POST /users`)
- Criptografia de senha com BCrypt, com limite de tamanho alinhado ao máximo que o algoritmo processa
- Verificação de email duplicado no cadastro
- Identificador de usuário como UUID gerado aleatoriamente, em vez de id sequencial
- Papel do usuário definido sempre pelo servidor — nunca aceito do que o cliente envia
- Login com emissão de token JWT (`POST /login`), com expiração configurável
- Proteção de rotas via filtro de segurança, com autenticação stateless
- Validação da força da chave de assinatura na inicialização: a aplicação não sobe com um segredo fraco

**Tratamento de erros**

- Respostas de erro padronizadas em JSON para toda a API
- Erros de validação detalhados por campo
- Mensagem genérica e idêntica para credenciais inválidas, evitando enumeração de usuários
- Erros inesperados registrados no servidor e devolvidos sem expor detalhes internos

### Em desenvolvimento

- Domínio de eventos (CRUD, com autorização por papel e por propriedade)

### Planejado

- Auto-atribuição do papel de organizador
- Tipos de ingresso e precificação
- Fluxo de pedidos e pagamento
- Emissão e validação de ingressos

## Como rodar o projeto

### Pré-requisitos

- Java 17 ou superior
- Maven (ou usar o `./mvnw` incluído no projeto)
- PostgreSQL rodando localmente (ou acessível por string de conexão)

### Passo a passo

1. Clone o repositório

```bash
git clone https://github.com/rodrigolutfy/ticketfy.git
cd ticketfy
```

2. Crie um banco de dados PostgreSQL (nome esperado por padrão: `ticketfy`)

3. Configure as variáveis de ambiente. Nenhum segredo fica escrito no `application.yml`:

```bash
export DB_PASSWORD=sua_senha
export JWT_SECRET=$(openssl rand -base64 32)
```

A `JWT_SECRET` é a chave usada para assinar os tokens, e precisa ter no mínimo 32 caracteres — a aplicação recusa iniciar com um valor mais curto. Ela não tem valor padrão de propósito: um segredo versionado no repositório permitiria que qualquer pessoa forjasse tokens válidos.

4. Rode a aplicação

```bash
./mvnw spring-boot:run
```

O Flyway aplica as migrations do banco automaticamente ao iniciar.

A API estará disponível em `http://localhost:8080`, e a documentação interativa (Swagger UI) em `http://localhost:8080/swagger-ui/index.html`

## Endpoints da API

### Já implementados

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/users` | Cadastra um novo usuário | Público |
| POST | `/login` | Autentica e retorna um token JWT | Público |

As rotas protegidas esperam o token no header `Authorization`, no formato `Bearer <token>`.

### Planejados

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/users/me` | Retorna os dados do usuário autenticado | Autenticado |
| POST | `/users/me/organizer` | Torna o usuário autenticado um organizador | Autenticado |
| POST | `/events` | Cadastra um novo evento | ORGANIZER |
| GET | `/events` | Lista os eventos publicados (paginado) | Público |
| GET | `/events/{id}` | Detalha um evento específico | Público |
| PUT | `/events/{id}` | Atualiza um evento | Dono ou ADMIN |
| DELETE | `/events/{id}` | Cancela um evento | Dono ou ADMIN |
| GET | `/events/{id}/ticket-types` | Lista os tipos de ingresso de um evento | Público |
| POST | `/events/{id}/ticket-types` | Cadastra um tipo de ingresso | Dono ou ADMIN |
| POST | `/orders` | Cria um novo pedido de compra | USER |
| GET | `/orders/me` | Lista os pedidos do usuário autenticado | USER |
| GET | `/orders/{id}` | Detalha um pedido específico | Dono ou ADMIN |

## Formato das respostas de erro

Toda resposta de erro da API é um objeto JSON com a mesma forma:

```json
{
  "message": "This email is already registered"
}
```

Erros de validação incluem a lista de campos que falharam:

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
| Dados inválidos na requisição | 400 |
| Credenciais inválidas no login | 401 |
| Requisição sem token em rota protegida | 401 |
| Acesso a recurso de outro usuário | 403 |
| Recurso não encontrado | 404 |
| Conflito (ex: email já cadastrado) | 409 |
| Erro inesperado | 500 |

## Estrutura do projeto

### Estrutura atual

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/      # configuração de segurança e beans
│   ├── security/    # login, token JWT, filtro de autenticação
│   └── exception/   # tratamento centralizado de erros
├── user/            # cadastro, papéis
└── ApiApplication   # ponto de entrada

src/main/resources/
└── db/migration/    # migrations do Flyway
```

### Estrutura final pretendida

```
src/main/java/com/lutfy/ticketfy/
├── infra/
│   ├── config/
│   ├── security/
│   └── exception/
├── user/            # cadastro, papéis
├── event/           # eventos e seus endereços
├── tickettype/      # tipos e preços de ingresso
├── ticket/          # ingresso emitido
├── order/           # pedidos de compra
├── payment/         # pagamento dos pedidos
└── ApiApplication   # ponto de entrada

src/main/resources/
└── db/migration/    # migrations do Flyway
```

O projeto é organizado por domínio, não por camada: cada pasta reúne entity, repository, service, controller e DTOs daquele conceito de negócio.

O endereço do evento não é uma entidade separada. Seguindo o modelo do Sympla, o organizador informa local, endereço e cidade diretamente no evento, já que um evento pode acontecer em qualquer lugar.

## Roadmap

- [x] Cadastro de usuário + criptografia de senha
- [x] Login + autenticação JWT
- [x] Proteção de rotas via filtro de segurança
- [x] Tratamento centralizado de erros
- [ ] Domínio de eventos (CRUD)
- [ ] Papel de organizador e autorização por propriedade
- [ ] Domínio de tipos de ingresso
- [ ] Fluxo de pedidos e pagamento
- [ ] Emissão e validação de ingressos

## Licença

Este projeto está licenciado sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.