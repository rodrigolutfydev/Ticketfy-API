# Roadmap

O que já está pronto na API do Ticketfy e o que vem a seguir. Os detalhes de cada item concluído estão na [referência da API](API.md) e no [documento de arquitetura](05%20-%20Documento%20de%20Arquitetura.md).

## Concluído

### Fundação
- [x] Cadastro com BCrypt e papéis USER, ORGANIZER e ADMIN, com auto-promoção a organizador
- [x] Autorização por papel e por propriedade do recurso
- [x] Eventos com busca por nome e cidade, paginação, destaque e exclusão lógica
- [x] Lotes com preço, quantidade, limite por pedido e edição sem afetar pedidos feitos
- [x] Foto de perfil e capa de evento por endereço https

### Compra e ingressos
- [x] Pedidos com vários itens e preço congelado
- [x] Reserva de estoque atômica por `UPDATE` condicional, com teste de concorrência
- [x] Idempotência contra pedido duplicado
- [x] Expiração automática de reservas, com devolução de estoque
- [x] Pagamento simulado e emissão de ingressos na mesma transação
- [x] Check-in protegido contra leitura dupla
- [x] Reembolso com prazo e bloqueio para ingresso usado ou transferido
- [x] Cancelamento de evento com reembolso em lote
- [x] Transferência de ingresso entre usuários, com código novo e limite por ingresso
- [x] Cupons de desconto por evento e pedidos de valor zero

### Organizador e financeiro
- [x] Painel de vendas do evento e do organizador
- [x] Taxa da plataforma e extrato imutável do organizador, com exportação CSV
- [x] Dados de recebimento com documento e chave Pix cifrados
- [x] Saques com análise, bloqueio por administrador e rotina de processamento
- [x] Auditoria append-only das ações sensíveis

### Segurança, privacidade e operação
- [x] Sessões revogáveis com access token curto e refresh token rotativo em cookie
- [x] Limites de tentativas de login, senha de confirmação, transferência e cupom
- [x] Erros no padrão RFC 7807 com `type` estável
- [x] `X-Request-Id` em toda resposta e logs estruturados em produção
- [x] Exportação dos próprios dados e exclusão de conta por anonimização (LGPD, com revisão jurídica pendente)
- [x] Migrations com Flyway e perfis `dev` e `prod`, com segredos em variáveis de ambiente
- [x] Imagem Docker multi-stage com usuário sem privilégios
- [x] Datas com fuso horário (`Instant` e `TIMESTAMPTZ`)
- [x] Health check e encerramento gracioso
- [x] Testes de integração com Testcontainers, cobertura, CodeQL e Dependabot no GitHub Actions
- [x] Contrato OpenAPI gerado no build e conferido no CI
- [x] Deploy no Render com PostgreSQL no Neon
- [x] Frontend React com proxy de autenticação em Cloudflare Pages Functions

## Próximos passos

- [ ] Idioma preferido salvo na conta
- [ ] Adicionar o evento à agenda com arquivo `.ics`
- [ ] E-mails transacionais com Brevo
- [ ] Pagamento via Pix com Asaas
- [ ] Publicar o frontend na Cloudflare Pages
- [ ] Login com Google
- [ ] Monitoramento de erros com Sentry
- [ ] Upload de foto de perfil e de capa de evento
- [ ] Teste de carga com k6

## Possíveis melhorias

Lacunas encontradas na revisão dos documentos de engenharia contra o código. Ainda não têm prioridade definida.

- [ ] Desativar um lote para novas vendas sem excluí-lo (a coluna `active` já existe em `ticket_types`, mas não há endpoint)
- [ ] Lista de participantes por ingresso para o organizador, com o dono atual e a situação de cada ingresso
- [ ] Suspensão e reativação de contas pelo administrador, com revogação das sessões
