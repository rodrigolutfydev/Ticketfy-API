# Ticketfy API

API REST de venda de ingressos para eventos, em Java e Spring Boot: do lote à venda, do QR Code ao check-in.

<p align="center">
  <a href="https://ticketfy-6fd.pages.dev"><img src="https://img.shields.io/badge/Acessar_o_site_%E2%86%92-FF5A1F?style=for-the-badge" alt="Acessar o site" width="300"></a>
</p>

<p align="center"><sub><a href="README.en.md">Read in English</a></sub></p>

<table>
  <tr>
    <td width="33%"><img src="docs/images/home.png" alt="Página inicial"><br><sub>Página inicial</sub></td>
    <td width="33%"><img src="docs/images/evento.png" alt="Página do evento"><br><sub>Evento e escolha de lotes</sub></td>
    <td width="33%"><img src="docs/images/checkout.png" alt="Revisão do pedido"><br><sub>Revisão do pedido</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/ingresso.png" alt="Ingressos com QR Code"><br><sub>Ingressos com QR Code</sub></td>
    <td><img src="docs/images/painel-organizador.png" alt="Painel do organizador"><br><sub>Painel do organizador</sub></td>
    <td align="center"><img src="docs/images/celular-evento.png" alt="Página do evento no celular" width="110"><br><sub>No celular</sub></td>
  </tr>
</table>

<sub>Front local com os eventos fictícios do <code>scripts/seed-demo.sh</code> e pagamento simulado.</sub>

## Sobre

Vender ingresso é fácil até duas pessoas disputarem o último lugar, alguém clicar duas vezes em "pagar" ou o mesmo QR Code ser lido em duas portas. O Ticketfy resolve esses casos no banco, com transações e travas, para três perfis: comprador, organizador e administrador.

## Funcionalidades

- **Comprador:** busca por nome, cidade e categoria; compra com cupom e reserva de 15 min; ingresso com QR Code e `.ics`; transferência, reembolso, login com Google e exportação ou exclusão da conta.
- **Organizador:** eventos, lotes e cupons; check-in e lista de participantes; painel de vendas; cancelamento com reembolso automático; extrato e saques.
- **Admin:** análise de saques, suspensão de contas, destaque de eventos e auditoria.

## Destaques técnicos

- **Último ingresso:** a reserva é um único `UPDATE` condicional no estoque, testado com 10 threads disputando o mesmo lote.
- **Sessão:** access token de 10 min e refresh token rotativo em cookie `HttpOnly`; reuso de token revoga a sessão (com 30 s de tolerância).
- **Login com Google:** authorization code com PKCE, código trocado no servidor e `id_token` validado por JWKS.
- **Recuperação de senha:** token de 256 bits, uso único e 30 min, guardado só como SHA-256.
- **E-mails depois do commit:** `@TransactionalEventListener(AFTER_COMMIT)` em fila própria; compra desfeita nunca gera e-mail.
- **Inicialização 3,6x mais rápida:** AppCDS e JIT só C1 levaram a subida de 516 s para 142 s com 0,1 CPU.

As 27 decisões, com motivo e consequência, estão no [documento de arquitetura](docs/05%20-%20Documento%20de%20Arquitetura.md#9-decisões-arquiteturais).

## Stack

**Backend**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java"> | 17 | Linguagem da API |
| <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot"> | 4.1.1 | Base da aplicação, REST, rotinas agendadas |
| <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white" alt="Spring Security"> | 7.1.1 | Autenticação, perfis e filtros de sessão |
| <img src="https://img.shields.io/badge/java--jwt-000000?style=flat-square&logo=jsonwebtokens&logoColor=white" alt="java-jwt"> | 4.6.0 | Access token JWT de 10 minutos |
| <img src="https://img.shields.io/badge/Nimbus%20JOSE-555555?style=flat-square" alt="Nimbus JOSE"> | 10.9.1 | Validação do `id_token` do Google |
| <img src="https://img.shields.io/badge/springdoc--openapi-85EA2D?style=flat-square&logo=swagger&logoColor=black" alt="springdoc-openapi"> | 3.1.0 | Swagger e `openapi.json` gerado no build |
| <img src="https://img.shields.io/badge/ZXing-555555?style=flat-square" alt="ZXing"> | 3.5.3 | QR Code dos ingressos nos e-mails |
| <img src="https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Maven"> | 3.9.16 | Build e dependências |

**Banco de dados**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL"> | 16 | Banco relacional (local e testes); Neon em produção |
| <img src="https://img.shields.io/badge/Hibernate-59666C?style=flat-square&logo=hibernate&logoColor=white" alt="Hibernate"> | 7.4.5 | Mapeamento das entidades (JPA) |
| <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white" alt="Flyway"> | 12.4.0 | Migrations versionadas do esquema |

**Frontend**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/React-20232A?style=flat-square&logo=react&logoColor=61DAFB" alt="React"> | 19.3.0 | Interface do site |
| <img src="https://img.shields.io/badge/React%20Router-CA4245?style=flat-square&logo=reactrouter&logoColor=white" alt="React Router"> | 8.4.0 | Rotas das páginas |
| <img src="https://img.shields.io/badge/TanStack%20Query-FF4154?style=flat-square&logo=reactquery&logoColor=white" alt="TanStack Query"> | 5.104.0 | Cache e chamadas à API |
| <img src="https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript"> | 7.0.2 | Tipagem do código do front |
| <img src="https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white" alt="Vite"> | 8.3.2 | Servidor de desenvolvimento e build |
| <img src="https://img.shields.io/badge/Tailwind%20CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white" alt="Tailwind CSS"> | 4.3.3 | Estilos |

**Infraestrutura e deploy**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker"> | — | Imagem multi-stage com AppCDS e Compose local |
| <img src="https://img.shields.io/badge/Render-000000?style=flat-square&logo=render&logoColor=white" alt="Render"> | — | Hospedagem da API |
| <img src="https://img.shields.io/badge/Neon-00E599?style=flat-square&logo=neon&logoColor=black" alt="Neon"> | — | PostgreSQL gerenciado em produção |
| <img src="https://img.shields.io/badge/Cloudflare%20Pages-F38020?style=flat-square&logo=cloudflarepages&logoColor=white" alt="Cloudflare Pages"> | Wrangler 4.149.0 | Hospedagem do front e Functions de proxy da sessão |
| <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions"> | — | CI, selo e relatório de cobertura |

**Testes e qualidade**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/JUnit-25A162?style=flat-square&logo=junit5&logoColor=white" alt="JUnit"> | 6.0.3 | 531 testes da API |
| <img src="https://img.shields.io/badge/Testcontainers-291A3F?style=flat-square" alt="Testcontainers"> | 2.0.5 | PostgreSQL real nos testes de integração |
| <img src="https://img.shields.io/badge/JaCoCo-555555?style=flat-square" alt="JaCoCo"> | 0.8.15 | Cobertura, com mínimo exigido no build |
| <img src="https://img.shields.io/badge/Vitest-6E9F18?style=flat-square&logo=vitest&logoColor=white" alt="Vitest"> | 5.0.3 | Testes do front |
| <img src="https://img.shields.io/badge/CodeQL-181717?style=flat-square&logo=github&logoColor=white" alt="CodeQL"> | — | Análise estática de segurança |
| <img src="https://img.shields.io/badge/Dependabot-025E8C?style=flat-square&logo=dependabot&logoColor=white" alt="Dependabot"> | — | Atualização semanal de dependências |

**Integrações**

| Tecnologia | Versão | Uso no Ticketfy |
|---|---|---|
| <img src="https://img.shields.io/badge/Google%20OAuth-4285F4?style=flat-square&logo=google&logoColor=white" alt="Google OAuth"> | OAuth 2.0 | Login com Google (authorization code + PKCE) |
| <img src="https://img.shields.io/badge/Brevo-0B996E?style=flat-square&logo=brevo&logoColor=white" alt="Brevo"> | API v3 | E-mails transacionais com QR Code e `.ics` |

## Arquitetura

```mermaid
flowchart LR
    browser["Navegador"]

    subgraph cf["Cloudflare Pages"]
        spa["Front React (SPA)"]
        proxy["Pages Functions<br/>proxy de /api/auth/*"]
    end

    subgraph render["Render (Docker)"]
        api["Ticketfy API<br/>Spring Boot"]
    end

    db[("PostgreSQL<br/>Neon")]
    google["Google OAuth"]
    brevo["Brevo<br/>e-mail transacional"]

    browser --> spa
    browser -- "login, refresh e logout<br/>(cookie HttpOnly)" --> proxy
    proxy -- "X-Proxy-Secret" --> api
    browser -- "demais rotas<br/>(Bearer token)" --> api
    browser -. "autorização com PKCE" .-> google
    api -- "troca do código e JWKS" --> google
    api -- "e-mails depois do commit" --> brevo
    api --> db
```

## Qualidade

531 testes (59 classes de integração com PostgreSQL real via Testcontainers), 95% de cobertura, e CI com testes e cobertura mínima em todo push, além de CodeQL e Dependabot.

## Como rodar localmente

Requer Java 17+ e Docker. Crie um `.env` com `DB_PASSWORD` e `JWT_SECRET` (32+ caracteres) e rode:

```bash
docker compose up -d
set -a; source .env; set +a
./mvnw spring-boot:run          # API em http://localhost:8080, Swagger em /swagger-ui/index.html
./mvnw verify                   # testes (precisa do Docker)
```

Para dados de demonstração, use uma conta organizadora: `TICKETFY_EMAIL=... TICKETFY_PASSWORD=... scripts/seed-demo.sh`.

<details>
<summary>Variáveis de ambiente</summary>

| Variável | Para que serve |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão com o PostgreSQL (`DB_PASSWORD` obrigatória) |
| `JWT_SECRET` | Assinatura dos access tokens (obrigatória) |
| `SPRING_PROFILES_ACTIVE` | Perfil `dev` ou `prod` |
| `PAYOUT_ENCRYPTION_KEY`, `PROXY_SHARED_SECRET` | Cifra de documento e chave Pix, e segredo do proxy do front (obrigatórias em `prod`) |
| `CORS_ALLOWED_ORIGINS`, `SITE_BASE_URL` | Origens do front autorizadas e endereço do site nos e-mails e no `.ics` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URIS` | Login com Google |
| `BREVO_API_KEY`, `MAIL_FROM_EMAIL`, `MAIL_FROM_NAME` | Envio de e-mails (sem elas, só registra no log) |
| `LOGIN_*`, `COUPON_*`, `PAYOUT_*` e outras | Limites, prazos e taxas, todos com padrão |

A lista completa está na seção [Config](docs/05%20-%20Documento%20de%20Arquitetura.md#config) do documento de arquitetura.

</details>

## Desenvolvimento

Desenvolvido por mim com auxílio de IA (Claude Code); as decisões, revisões e testes foram conduzidos por mim.

## Autor

<p>
  <img src="https://img.shields.io/badge/Rodrigo_Lutfy-1F2328?style=for-the-badge" alt="Rodrigo Lutfy">
  <a href="mailto:rodrigolutfy.dev@gmail.com"><img src="https://img.shields.io/badge/rodrigolutfy.dev%40gmail.com-EA4335?style=for-the-badge&logo=gmail&logoColor=white" alt="rodrigolutfy.dev@gmail.com"></a>
  <a href="https://github.com/rodrigolutfydev"><img src="https://img.shields.io/badge/rodrigolutfydev-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub rodrigolutfydev"></a>
</p>

## Links

<p align="center">
  <a href="https://ticketfy-6fd.pages.dev"><img src="https://img.shields.io/badge/Acessar_o_site_%E2%86%92-FF5A1F?style=for-the-badge" alt="Acessar o site" width="300"></a>
  &nbsp;
  <a href="https://github.com/rodrigolutfydev/ticketfy-web"><img src="https://img.shields.io/badge/C%C3%B3digo_do_front--end-1F2328?style=for-the-badge&logo=github&logoColor=white" alt="Código do front-end" width="408"></a>
</p>

| | Link | O que tem |
|:-:|---|---|
| 🌐 | **[Site](https://ticketfy-6fd.pages.dev)** | O Ticketfy no ar: vitrine, compra, ingressos e painel do organizador. |
| 💻 | **[Repositório do front](https://github.com/rodrigolutfydev/ticketfy-web)** | Código do site em React, com o proxy de sessão em Pages Functions. |
| 📘 | **[Documentação da API](docs/API.md)** | Autenticação, erros, limites e todos os endpoints. |
| 📄 | **[Contrato OpenAPI](docs/openapi.json)** | Contrato gerado no build e conferido no CI. |
| 🏛️ | **[Arquitetura e decisões](docs/05%20-%20Documento%20de%20Arquitetura.md)** | Camadas, segurança, persistência e as 27 decisões com motivo e consequência. |
| 🧪 | **[Swagger](https://ticketfy-api.onrender.com/swagger-ui/index.html)** | Explorar os endpoints no navegador, em modo só leitura (o "Try it out" executa apenas GET). |
| 🔤 | **[English version](README.en.md)** | Este README em inglês. |
