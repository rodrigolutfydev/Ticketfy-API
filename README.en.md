# Ticketfy API

REST API for selling event tickets, built with Java and Spring Boot: from ticket tiers to sales, from QR Code to check-in.

<p align="center">
  <a href="https://ticketfy-6fd.pages.dev"><img src="https://img.shields.io/badge/Visit_the_website_%E2%86%92-FF5A1F?style=for-the-badge" alt="Visit the website" width="348"></a>
</p>

<p align="center"><sub><a href="README.md">Ler em português</a></sub></p>

<table>
  <tr>
    <td width="33%"><img src="docs/images/home.png" alt="Home page"><br><sub>Home page</sub></td>
    <td width="33%"><img src="docs/images/evento.png" alt="Event page"><br><sub>Event and ticket selection</sub></td>
    <td width="33%"><img src="docs/images/checkout.png" alt="Order review"><br><sub>Order review</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/ingresso.png" alt="Tickets with QR Code"><br><sub>Tickets with QR Code</sub></td>
    <td><img src="docs/images/painel-organizador.png" alt="Organizer dashboard"><br><sub>Organizer dashboard</sub></td>
    <td align="center"><img src="docs/images/celular-evento.png" alt="Event page on a phone" width="110"><br><sub>On a phone</sub></td>
  </tr>
</table>

<sub>Local frontend with the fictional events from <code>scripts/seed-demo.sh</code> and simulated payment. The interface is shown in Portuguese and is also available in English.</sub>

## About

Selling tickets is easy until two people fight over the last seat, someone double-clicks "pay" or the same QR Code is scanned at two gates. Ticketfy handles these cases in the database, with transactions and locks, for three roles: buyer, organizer and administrator.

## Features

- **Buyer:** search by name, city and category; checkout with coupons and a 15-min reservation; tickets with QR Code and `.ics`; transfers, refunds, Google sign-in and account export or deletion.
- **Organizer:** events, ticket tiers and coupons; check-in and attendee list; sales dashboard; cancellation with automatic refunds; ledger and payouts.
- **Admin:** payout review, account suspension, featured events and audit log.

## Technical highlights

- **Last ticket:** the reservation is a single conditional `UPDATE` on the stock, tested with 10 threads competing for the same tier.
- **Session:** 10-min access token and a rotating refresh token in an `HttpOnly` cookie; token reuse revokes the session (with a 30 s tolerance).
- **Google sign-in:** authorization code with PKCE, code exchanged on the server and `id_token` validated through JWKS.
- **Password reset:** 256-bit, single-use, 30-min token stored only as SHA-256.
- **Emails after commit:** `@TransactionalEventListener(AFTER_COMMIT)` on its own queue; a rolled-back purchase never sends an email.
- **3.6x faster startup:** AppCDS and C1-only JIT cut startup from 516 s to 142 s on 0.1 CPU.

All 27 decisions, with reasoning and consequences, are in the [architecture document](docs/05%20-%20Documento%20de%20Arquitetura.md#9-decisões-arquiteturais) (in Portuguese).

## Stack

**Backend**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java"> | 17 | API language |
| <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot"> | 4.1.1 | Application base, REST, scheduled jobs |
| <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white" alt="Spring Security"> | 7.1.1 | Authentication, roles and session filters |
| <img src="https://img.shields.io/badge/java--jwt-000000?style=flat-square&logo=jsonwebtokens&logoColor=white" alt="java-jwt"> | 4.6.0 | 10-minute JWT access token |
| <img src="https://img.shields.io/badge/Nimbus%20JOSE-555555?style=flat-square" alt="Nimbus JOSE"> | 10.9.1 | Google `id_token` validation |
| <img src="https://img.shields.io/badge/springdoc--openapi-85EA2D?style=flat-square&logo=swagger&logoColor=black" alt="springdoc-openapi"> | 3.1.0 | Swagger and `openapi.json` generated at build time |
| <img src="https://img.shields.io/badge/ZXing-555555?style=flat-square" alt="ZXing"> | 3.5.3 | Ticket QR Codes in emails |
| <img src="https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Maven"> | 3.9.16 | Build and dependencies |

**Database**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL"> | 16 | Relational database (local and tests); Neon in production |
| <img src="https://img.shields.io/badge/Hibernate-59666C?style=flat-square&logo=hibernate&logoColor=white" alt="Hibernate"> | 7.4.5 | Entity mapping (JPA) |
| <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white" alt="Flyway"> | 12.4.0 | Versioned schema migrations |

**Frontend**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/React-20232A?style=flat-square&logo=react&logoColor=61DAFB" alt="React"> | 19.3.0 | Website UI |
| <img src="https://img.shields.io/badge/React%20Router-CA4245?style=flat-square&logo=reactrouter&logoColor=white" alt="React Router"> | 8.4.0 | Page routing |
| <img src="https://img.shields.io/badge/TanStack%20Query-FF4154?style=flat-square&logo=reactquery&logoColor=white" alt="TanStack Query"> | 5.104.0 | API calls and caching |
| <img src="https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript"> | 7.0.2 | Frontend typing |
| <img src="https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white" alt="Vite"> | 8.3.2 | Dev server and build |
| <img src="https://img.shields.io/badge/Tailwind%20CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white" alt="Tailwind CSS"> | 4.3.3 | Styling |

**Infrastructure and deployment**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker"> | — | Multi-stage image with AppCDS, and local Compose |
| <img src="https://img.shields.io/badge/Render-000000?style=flat-square&logo=render&logoColor=white" alt="Render"> | — | API hosting |
| <img src="https://img.shields.io/badge/Neon-00E599?style=flat-square&logo=neon&logoColor=black" alt="Neon"> | — | Managed PostgreSQL in production |
| <img src="https://img.shields.io/badge/Cloudflare%20Pages-F38020?style=flat-square&logo=cloudflarepages&logoColor=white" alt="Cloudflare Pages"> | Wrangler 4.149.0 | Frontend hosting and session proxy Functions |
| <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions"> | — | CI, coverage badge and report |

**Testing and quality**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/JUnit-25A162?style=flat-square&logo=junit5&logoColor=white" alt="JUnit"> | 6.0.3 | 531 API tests |
| <img src="https://img.shields.io/badge/Testcontainers-291A3F?style=flat-square" alt="Testcontainers"> | 2.0.5 | Real PostgreSQL in integration tests |
| <img src="https://img.shields.io/badge/JaCoCo-555555?style=flat-square" alt="JaCoCo"> | 0.8.15 | Coverage, with a minimum enforced by the build |
| <img src="https://img.shields.io/badge/Vitest-6E9F18?style=flat-square&logo=vitest&logoColor=white" alt="Vitest"> | 5.0.3 | Frontend tests |
| <img src="https://img.shields.io/badge/CodeQL-181717?style=flat-square&logo=github&logoColor=white" alt="CodeQL"> | — | Static security analysis |
| <img src="https://img.shields.io/badge/Dependabot-025E8C?style=flat-square&logo=dependabot&logoColor=white" alt="Dependabot"> | — | Weekly dependency updates |

**Integrations**

| Technology | Version | Used for |
|---|---|---|
| <img src="https://img.shields.io/badge/Google%20OAuth-4285F4?style=flat-square&logo=google&logoColor=white" alt="Google OAuth"> | OAuth 2.0 | Google sign-in (authorization code + PKCE) |
| <img src="https://img.shields.io/badge/Brevo-0B996E?style=flat-square&logo=brevo&logoColor=white" alt="Brevo"> | API v3 | Transactional emails with QR Code and `.ics` |

## Architecture

```mermaid
flowchart LR
    browser["Browser"]

    subgraph cf["Cloudflare Pages"]
        spa["React frontend (SPA)"]
        proxy["Pages Functions<br/>/api/auth/* proxy"]
    end

    subgraph render["Render (Docker)"]
        api["Ticketfy API<br/>Spring Boot"]
    end

    db[("PostgreSQL<br/>Neon")]
    google["Google OAuth"]
    brevo["Brevo<br/>transactional email"]

    browser --> spa
    browser -- "login, refresh and logout<br/>(HttpOnly cookie)" --> proxy
    proxy -- "X-Proxy-Secret" --> api
    browser -- "other routes<br/>(Bearer token)" --> api
    browser -. "authorization with PKCE" .-> google
    api -- "code exchange and JWKS" --> google
    api -- "emails after commit" --> brevo
    api --> db
```

## Quality

531 tests (59 integration test classes on a real PostgreSQL via Testcontainers), 95% coverage, and CI running the tests and minimum coverage on every push, plus CodeQL and Dependabot.

## Running locally

Requires Java 17+ and Docker. Create a `.env` with `DB_PASSWORD` and `JWT_SECRET` (32+ characters) and run:

```bash
docker compose up -d
set -a; source .env; set +a
./mvnw spring-boot:run          # API at http://localhost:8080, Swagger at /swagger-ui/index.html
./mvnw verify                   # tests (needs Docker)
```

For demo data, use an organizer account: `TICKETFY_EMAIL=... TICKETFY_PASSWORD=... scripts/seed-demo.sh`.

<details>
<summary>Environment variables</summary>

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection (`DB_PASSWORD` required) |
| `JWT_SECRET` | Access token signing (required) |
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` profile |
| `PAYOUT_ENCRYPTION_KEY`, `PROXY_SHARED_SECRET` | Encryption of tax ID and Pix key, and the frontend proxy secret (required in `prod`) |
| `CORS_ALLOWED_ORIGINS`, `SITE_BASE_URL` | Allowed frontend origins and the site address used in emails and `.ics` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URIS` | Google sign-in |
| `BREVO_API_KEY`, `MAIL_FROM_EMAIL`, `MAIL_FROM_NAME` | Email sending (without them, sends are only logged) |
| `LOGIN_*`, `COUPON_*`, `PAYOUT_*` and others | Limits, deadlines and fees, all with defaults |

The full list is in the [Config](docs/05%20-%20Documento%20de%20Arquitetura.md#config) section of the architecture document.

</details>

## Development

Built by me with the help of AI (Claude Code); the decisions, reviews and testing were carried out by me.

## Author

<p>
  <img src="https://img.shields.io/badge/Rodrigo_Lutfy-1F2328?style=for-the-badge" alt="Rodrigo Lutfy">
  <a href="mailto:rodrigolutfy.dev@gmail.com"><img src="https://img.shields.io/badge/rodrigolutfy.dev%40gmail.com-EA4335?style=for-the-badge&logo=gmail&logoColor=white" alt="rodrigolutfy.dev@gmail.com"></a>
  <a href="https://github.com/rodrigolutfydev"><img src="https://img.shields.io/badge/rodrigolutfydev-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub rodrigolutfydev"></a>
</p>

## Links

<p align="center">
  <a href="https://ticketfy-6fd.pages.dev"><img src="https://img.shields.io/badge/Visit_the_website_%E2%86%92-FF5A1F?style=for-the-badge" alt="Visit the website" width="348"></a>
  &nbsp;
  <a href="https://github.com/rodrigolutfydev/ticketfy-web"><img src="https://img.shields.io/badge/Frontend_code-1F2328?style=for-the-badge&logo=github&logoColor=white" alt="Frontend code" width="310"></a>
</p>

| | Link | What you'll find |
|:-:|---|---|
| 🌐 | **[Website](https://ticketfy-6fd.pages.dev)** | Ticketfy live: showcase, checkout, tickets and organizer dashboard. |
| 💻 | **[Frontend repository](https://github.com/rodrigolutfydev/ticketfy-web)** | The React website code, with the session proxy in Pages Functions. |
| 📘 | **[API documentation](docs/API.md)** | Authentication, errors, limits and every endpoint (in Portuguese). |
| 📄 | **[OpenAPI contract](docs/openapi.json)** | Contract generated at build time and checked in CI. |
| 🏛️ | **[Architecture and decisions](docs/05%20-%20Documento%20de%20Arquitetura.md)** | Layers, security, persistence and the 27 decisions with reasoning (in Portuguese). |
| 🧪 | **[Swagger](https://ticketfy-api.onrender.com/swagger-ui/index.html)** | Browse the endpoints in the browser, read-only ("Try it out" runs GET requests only). |
| 🔤 | **[Versão em português](README.md)** | This README in Portuguese. |
