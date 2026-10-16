#!/usr/bin/env bash
# Popula o Ticketfy com eventos de demonstração (apresentação e portfólio).
#
# Usa só a API HTTP pública, logado como organizador. Não mexe no banco.
# Pode rodar quantas vezes quiser: evento que o organizador já tem com o
# mesmo nome é pulado (só os lotes que faltarem nele são criados).
#
# Uso:
#   TICKETFY_EMAIL=... TICKETFY_PASSWORD=... scripts/seed-demo.sh            # cria
#   TICKETFY_EMAIL=... TICKETFY_PASSWORD=... scripts/seed-demo.sh --dry-run  # só mostra
#
# Variáveis de ambiente:
#   TICKETFY_API_URL          endereço da API (padrão http://localhost:8080)
#   TICKETFY_EMAIL            e-mail da conta organizadora
#   TICKETFY_PASSWORD         senha dessa conta (nunca grave em arquivo)
#   TICKETFY_ORIGIN           origem do front enviada no login, precisa estar no
#                             CORS da API (padrão: http://localhost:5173 para API
#                             local, https://ticketfy-6fd.pages.dev para as outras)
#   TICKETFY_IMAGE_BASE       onde estão as fotos (padrão https://ticketfy-6fd.pages.dev/demo)
#   TICKETFY_WAKE_TIMEOUT     segundos esperando a API acordar (padrão 300)
#   TICKETFY_SKIP_IMAGE_CHECK=1  cria mesmo se as fotos ainda não estiverem publicadas
#   DRY_RUN=1                 o mesmo que --dry-run
#
# No modo simulação nada é criado. Com e-mail e senha, ele faz login (o que
# abre uma sessão) para conferir o papel da conta e o que já existe; sem
# eles, só mostra o plano.
set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
for arg in "$@"; do
  case "$arg" in
    --dry-run|--simular) DRY_RUN=1 ;;
    -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Argumento desconhecido: $arg (use --dry-run ou --help)" >&2; exit 2 ;;
  esac
done
[[ "$DRY_RUN" == "1" ]] || DRY_RUN=0

API_URL="${TICKETFY_API_URL:-http://localhost:8080}"
API_URL="${API_URL%/}"
IS_LOCAL=0
[[ "$API_URL" =~ ^http://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]] && IS_LOCAL=1
if [[ "$IS_LOCAL" == "1" ]]; then
  ORIGIN="${TICKETFY_ORIGIN:-http://localhost:5173}"
else
  ORIGIN="${TICKETFY_ORIGIN:-https://ticketfy-6fd.pages.dev}"
fi
IMAGE_BASE="${TICKETFY_IMAGE_BASE:-https://ticketfy-6fd.pages.dev/demo}"
IMAGE_BASE="${IMAGE_BASE%/}"
WAKE_TIMEOUT="${TICKETFY_WAKE_TIMEOUT:-300}"
TZ_EVENTS="America/Sao_Paulo"
DEMO_NOTE="Evento fictício, criado para demonstração do Ticketfy."

die() { echo "ERRO: $*" >&2; exit 1; }
log() { echo "==> $*"; }

command -v curl >/dev/null || die "curl não encontrado. Instale com: sudo apt install curl"
command -v jq >/dev/null || die "jq não encontrado. Instale com: sudo apt install jq (macOS: brew install jq)"
date -d "2026-01-01 +1 day" >/dev/null 2>&1 || die "este script precisa do date do GNU (Linux; no macOS: brew install coreutils)"
[[ "$API_URL" == https://* || "$IS_LOCAL" == "1" ]] || die "TICKETFY_API_URL precisa ser https (ou http://localhost)."

HAVE_CREDENTIALS=0
if [[ -n "${TICKETFY_EMAIL:-}" && -n "${TICKETFY_PASSWORD:-}" ]]; then
  HAVE_CREDENTIALS=1
elif [[ "$DRY_RUN" == "0" ]]; then
  die "defina TICKETFY_EMAIL e TICKETFY_PASSWORD (conta com papel ORGANIZER)."
fi
export TICKETFY_EMAIL="${TICKETFY_EMAIL:-}" TICKETFY_PASSWORD="${TICKETFY_PASSWORD:-}"

# Token e corpos ficam em arquivos 600, nunca na linha de comando (ps mostraria).
WORK="$(mktemp -d)"
chmod 700 "$WORK"
trap 'rm -rf "$WORK"' EXIT
AUTH_CFG="$WORK/auth.cfg"
BODY_FILE="$WORK/body.json"
RESP_FILE="$WORK/resp.json"
HEAD_FILE="$WORK/headers.txt"
: >"$AUTH_CFG"
CODE=""
BODY=""

# ---------------------------------------------------------------- eventos ---
# dayOffset: dias a partir de hoje; weekday: 1=segunda ... 7=domingo (a data
# anda até cair nesse dia); time: horário de Brasília; hours: duração.
EVENTS_JSON="$(cat <<'JSON'
[
  {
    "name": "Festival Maré Alta",
    "category": "MUSIC",
    "image": "festival-praia.jpg",
    "venueName": "Arena Costa do Campeche",
    "address": "Rodovia Aparício Ramos Cordeiro, 4200 - Campeche",
    "city": "Florianópolis", "state": "SC",
    "dayOffset": 62, "weekday": 6, "time": "15:00", "hours": 9,
    "description": "Dez horas de música à beira-mar com indie, MPB e eletrônica em dois palcos. O line-up mistura bandas autorais do Sul com DJs que tocam até o fim da noite, com praça de alimentação, área de descanso e vista para o pôr do sol no Campeche.",
    "lots": [
      {"name": "Pista - 1º lote", "description": "Acesso aos dois palcos e à praça de alimentação.", "price": 180.00, "quantityTotal": 1200, "maxPerOrder": 6},
      {"name": "Pista - Meia-entrada", "description": "Estudantes, pessoas com 60 anos ou mais, PcD e jovens de baixa renda. Apresente o documento na entrada.", "price": 90.00, "quantityTotal": 400, "maxPerOrder": 2},
      {"name": "VIP Open Bar", "description": "Área elevada em frente ao palco principal, open bar de água, refrigerante e chope, e banheiros exclusivos.", "price": 420.00, "quantityTotal": 8, "maxPerOrder": 4}
    ]
  },
  {
    "name": "Noite Elétrica: Tributo ao Rock Nacional",
    "category": "MUSIC",
    "image": "show-rock.jpg",
    "venueName": "Casa Mineira de Shows",
    "address": "Rua Pernambuco, 1150 - Savassi",
    "city": "Belo Horizonte", "state": "MG",
    "dayOffset": 82, "weekday": 5, "time": "21:00", "hours": 4,
    "description": "Uma banda de oito músicos revisita os clássicos do rock brasileiro dos anos 80 e 90, com projeções, convidados especiais e aquele refrão que todo mundo sabe cantar.",
    "lots": [
      {"name": "Pista", "description": "Ingresso para a pista em pé.", "price": 95.00, "quantityTotal": 600, "maxPerOrder": 6},
      {"name": "Pista - Meia-entrada", "description": "Meia-entrada conforme a Lei 12.933/2013. Documento obrigatório na entrada.", "price": 47.50, "quantityTotal": 200, "maxPerOrder": 2},
      {"name": "Mezanino VIP", "description": "Mezanino com mesas, vista privilegiada do palco e bar exclusivo.", "price": 220.00, "quantityTotal": 80, "maxPerOrder": 4}
    ]
  },
  {
    "name": "O Último Ato de Helena",
    "category": "THEATER",
    "image": "teatro.jpg",
    "venueName": "Teatro Vila Aurora",
    "address": "Rua Augusta, 1520 - Consolação",
    "city": "São Paulo", "state": "SP",
    "dayOffset": 88, "weekday": 6, "time": "20:00", "hours": 2,
    "description": "Drama em três atos sobre uma atriz veterana que decide encerrar a carreira na mesma noite em que reencontra a filha que não vê há vinte anos. Texto inédito, elenco de quatro atores e trilha tocada ao vivo. Classificação indicativa: 14 anos.",
    "lots": [
      {"name": "Plateia - Inteira", "description": "Assentos numerados na plateia, escolhidos na bilheteria por ordem de chegada.", "price": 120.00, "quantityTotal": 250, "maxPerOrder": 6},
      {"name": "Plateia - Meia-entrada", "description": "Estudantes, professores, pessoas com 60 anos ou mais e PcD, com documento.", "price": 60.00, "quantityTotal": 100, "maxPerOrder": 2},
      {"name": "Frisa VIP", "description": "Frisa lateral para até 4 pessoas, com espumante no intervalo.", "price": 280.00, "quantityTotal": 6, "maxPerOrder": 2}
    ]
  },
  {
    "name": "Stand-up: Tô Rindo de Nervoso",
    "category": "COMEDY",
    "image": "stand-up.jpg",
    "venueName": "Batel Comedy Club",
    "address": "Avenida do Batel, 1450 - Batel",
    "city": "Curitiba", "state": "PR",
    "dayOffset": 64, "weekday": 6, "time": "21:30", "hours": 2,
    "description": "Quatro comediantes da nova geração do stand-up paranaense testam material inédito sobre boletos, aplicativos de namoro e reunião que podia ser um e-mail. Casa intimista, com mesas e cardápio de petiscos. Classificação: 16 anos.",
    "lots": [
      {"name": "Mesa - Inteira", "description": "Lugar em mesa compartilhada. Consumação não incluída.", "price": 70.00, "quantityTotal": 9, "maxPerOrder": 4},
      {"name": "Mesa - Meia-entrada", "description": "Meia-entrada com documento na entrada.", "price": 35.00, "quantityTotal": 20, "maxPerOrder": 2}
    ]
  },
  {
    "name": "Baile do Pôr do Sol",
    "category": "PARTIES",
    "image": "festa.jpg",
    "venueName": "Terraço Rio Vermelho",
    "address": "Rua Fonte do Boi, 210 - Rio Vermelho",
    "city": "Salvador", "state": "BA",
    "dayOffset": 85, "weekday": 6, "time": "16:00", "hours": 7,
    "description": "Festa ao ar livre que começa com o pôr do sol na Baía e segue noite adentro com pagodão, axé e funk. Chuva de confete à meia-noite, bar de drinks com frutas da estação e DJs residentes da cidade.",
    "lots": [
      {"name": "Pista", "description": "Acesso à pista e aos bares.", "price": 80.00, "quantityTotal": 900, "maxPerOrder": 6},
      {"name": "Pista - Meia-entrada", "description": "Meia-entrada para quem tem direito, com documento.", "price": 40.00, "quantityTotal": 300, "maxPerOrder": 2},
      {"name": "Camarote Open Bar", "description": "Camarote com vista para o mar, open bar de drinks e cerveja e acesso exclusivo.", "price": 260.00, "quantityTotal": 150, "maxPerOrder": 4}
    ]
  },
  {
    "name": "Corrida Orla Carioca 10K",
    "category": "SPORTS",
    "image": "corrida.jpg",
    "venueName": "Aterro do Flamengo - Largada",
    "address": "Avenida Infante Dom Henrique, s/n - Flamengo",
    "city": "Rio de Janeiro", "state": "RJ",
    "dayOffset": 90, "weekday": 7, "time": "06:30", "hours": 4,
    "description": "Percursos de 5 km e 10 km pela orla, do Aterro do Flamengo até o Leme e de volta, com pontos de hidratação a cada 2,5 km, cronometragem por chip e medalha para todos que completarem a prova.",
    "lots": [
      {"name": "Kit Básico", "description": "Camiseta, número de peito com chip e medalha de participação.", "price": 119.00, "quantityTotal": 1500, "maxPerOrder": 4, "maxPerBuyer": 4},
      {"name": "Kit Básico - Meia (60+)", "description": "Para corredores com 60 anos ou mais. Documento na retirada do kit.", "price": 59.50, "quantityTotal": 300, "maxPerOrder": 1, "maxPerBuyer": 1},
      {"name": "Kit Premium", "description": "Kit básico mais jaqueta corta-vento, tenda exclusiva com massagem e café da manhã após a prova.", "price": 189.00, "quantityTotal": 10, "maxPerOrder": 2}
    ]
  },
  {
    "name": "Oficina de Fotografia de Rua",
    "category": "COURSES",
    "image": "oficina-fotografia.jpg",
    "venueName": "Estúdio Casa Amarela",
    "address": "Rua da Aurora, 325 - Boa Vista",
    "city": "Recife", "state": "PE",
    "dayOffset": 103, "weekday": 6, "time": "09:00", "hours": 8,
    "description": "Um dia de prática pelo centro histórico do Recife: composição, luz natural, abordagem de pessoas na rua e edição no celular. Turma pequena, com revisão das fotos de cada participante no fim do dia. Leve sua câmera ou seu celular.",
    "lots": [
      {"name": "Vaga - Inteira", "description": "Oficina completa com almoço e certificado de participação.", "price": 350.00, "quantityTotal": 7, "maxPerOrder": 2, "maxPerBuyer": 2},
      {"name": "Vaga - Meia-entrada (estudante)", "description": "Para estudantes com carteirinha válida.", "price": 175.00, "quantityTotal": 5, "maxPerOrder": 1, "maxPerBuyer": 1}
    ]
  },
  {
    "name": "DevSul Conf: Software que Escala",
    "category": "TALKS",
    "image": "conferencia.jpg",
    "venueName": "Centro de Eventos Guaíba",
    "address": "Avenida Edvaldo Pereira Paiva, 1000 - Praia de Belas",
    "city": "Porto Alegre", "state": "RS",
    "dayOffset": 100, "weekday": 4, "time": "09:00", "hours": 9,
    "description": "Conferência de um dia sobre arquitetura, observabilidade, segurança e carreira em tecnologia, com 12 palestras em duas trilhas, painel com líderes técnicos da região e espaço de networking com as empresas apoiadoras.",
    "lots": [
      {"name": "Ingresso Geral", "description": "Acesso às duas trilhas, coffee breaks e área de networking.", "price": 290.00, "quantityTotal": 500, "maxPerOrder": 5},
      {"name": "Meia-entrada", "description": "Estudantes, professores e pessoas com 60 anos ou mais, com documento.", "price": 145.00, "quantityTotal": 150, "maxPerOrder": 1},
      {"name": "VIP + Workshop", "description": "Ingresso geral, workshop prático na véspera, almoço com palestrantes e lugar reservado nas primeiras filas.", "price": 590.00, "quantityTotal": 5, "maxPerOrder": 1}
    ]
  },
  {
    "name": "Exposição Cores do Cerrado",
    "category": "ARTS",
    "image": "exposicao.jpg",
    "venueName": "Galeria Eixo Monumental",
    "address": "Setor Cultural Sul, Lote 2 - Asa Sul",
    "city": "Brasília", "state": "DF",
    "dayOffset": 80, "weekday": 6, "time": "10:00", "hours": 8,
    "description": "Pinturas, fotografias e instalações de 18 artistas do Centro-Oeste inspiradas na paisagem, na fauna e nas cores do cerrado. O ingresso vale para o dia de abertura, com visita guiada pela curadoria às 11h e às 15h.",
    "lots": [
      {"name": "Inteira", "description": "Entrada para o dia de abertura.", "price": 30.00, "quantityTotal": 400, "maxPerOrder": 6},
      {"name": "Meia-entrada", "description": "Estudantes, professores, pessoas com 60 anos ou mais e PcD.", "price": 15.00, "quantityTotal": 200, "maxPerOrder": 2}
    ]
  },
  {
    "name": "Festival Sabores do Norte",
    "category": "FOOD",
    "image": "gastronomia.jpg",
    "venueName": "Armazém das Docas",
    "address": "Boulevard Castilhos França, 700 - Campina",
    "city": "Belém", "state": "PA",
    "dayOffset": 95, "weekday": 6, "time": "12:00", "hours": 10,
    "description": "Chefs paraenses e convidados de todo o Brasil preparam pratos com tucupi, jambu, açaí e peixes da região, com aulas-show, feira de produtores locais e música ao vivo à beira do rio.",
    "lots": [
      {"name": "Entrada", "description": "Acesso ao festival. As degustações são pagas à parte, nas barracas.", "price": 40.00, "quantityTotal": 1000, "maxPerOrder": 6},
      {"name": "Entrada - Meia", "description": "Meia-entrada com documento.", "price": 20.00, "quantityTotal": 300, "maxPerOrder": 2},
      {"name": "Degustação VIP", "description": "Entrada, menu de oito degustações assinado pelos chefs e lugar garantido nas aulas-show.", "price": 210.00, "quantityTotal": 60, "maxPerOrder": 4}
    ]
  },
  {
    "name": "Oficina Pequenos Artistas",
    "category": "KIDS",
    "image": "oficina-infantil.jpg",
    "venueName": "Espaço Cultural Beira-Mar",
    "address": "Avenida Beira-Mar, 2500 - Meireles",
    "city": "Fortaleza", "state": "CE",
    "dayOffset": 110, "weekday": 7, "time": "10:00", "hours": 2,
    "description": "Duas horas de pintura, colagem e massinha para crianças de 4 a 10 anos, com material incluso e monitores especializados. Cada criança leva a obra para casa. Um adulto acompanha de graça.",
    "lots": [
      {"name": "Criança", "description": "Vaga para uma criança de 4 a 10 anos, com material incluso. Um adulto acompanhante não paga.", "price": 45.00, "quantityTotal": 40, "maxPerOrder": 3},
      {"name": "Kit Família", "description": "Duas crianças, avental personalizado para cada uma e lanche no intervalo.", "price": 110.00, "quantityTotal": 8, "maxPerOrder": 1}
    ]
  }
]
JSON
)"

# ------------------------------------------------------------------- HTTP ---
# call METHOD PATH [arquivo-de-corpo] -> CODE e BODY
call() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -X "$method" -o "$RESP_FILE" -w '%{http_code}' --max-time 60
    -H 'Accept: application/json' -K "$AUTH_CFG")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data-binary "@$body")
  CODE="$(curl "${args[@]}" "$API_URL$path" || echo 000)"
  BODY="$(cat "$RESP_FILE" 2>/dev/null || true)"
}

LOGGED_IN=0

login() {
  local wait
  : >"$AUTH_CFG"
  while true; do
    jq -n '{email: env.TICKETFY_EMAIL, password: env.TICKETFY_PASSWORD}' >"$BODY_FILE"
    CODE="$(curl -sS -X POST -o "$RESP_FILE" -D "$HEAD_FILE" -w '%{http_code}' --max-time 60 \
      -H "Origin: $ORIGIN" -H 'X-Ticketfy-Auth: 1' -H 'Content-Type: application/json' \
      --data-binary "@$BODY_FILE" "$API_URL/auth/login" || echo 000)"
    : >"$BODY_FILE"
    if [[ "$CODE" == "429" ]]; then
      wait="$(grep -i '^retry-after:' "$HEAD_FILE" | tr -dc '0-9')"
      wait="${wait:-15}"
      echo "    limite de tentativas de login, aguardando ${wait}s..." >&2
      sleep "$((wait + 1))"
      continue
    fi
    break
  done
  case "$CODE" in
    200) ;;
    401) die "login recusado: e-mail ou senha incorretos (contas criadas só pelo Google não têm senha; defina uma em Minha conta)." ;;
    403) die "login rejeitado (HTTP 403). A origem '$ORIGIN' precisa estar em CORS_ALLOWED_ORIGINS da API; ajuste TICKETFY_ORIGIN. Resposta: $(cat "$RESP_FILE")" ;;
    *) die "login falhou (HTTP $CODE): $(cat "$RESP_FILE" 2>/dev/null)" ;;
  esac
  local token
  token="$(jq -r '.token // empty' "$RESP_FILE")"
  [[ -n "$token" ]] || die "login não devolveu token."
  printf 'header = "Authorization: Bearer %s"\n' "$token" >"$AUTH_CFG"
  : >"$RESP_FILE"
  LOGGED_IN=1
}

# api ESPERADO METHOD PATH [arquivo-de-corpo]: falha se o status for outro.
# O access token dura 10 min; num 401 faz login de novo e repete uma vez.
api() {
  local expected="$1"; shift
  call "$@"
  if [[ "$CODE" == "401" && "$LOGGED_IN" == "1" ]]; then
    log "token expirou, entrando de novo"
    login
    call "$@"
  fi
  if [[ "$CODE" != "$expected" ]]; then
    echo "Falha em $1 $2: HTTP $CODE (esperado $expected)" >&2
    echo "$BODY" >&2
    exit 1
  fi
}

wake_api() {
  local start code
  start="$(date +%s)"
  log "Acordando a API em $API_URL (até ${WAKE_TIMEOUT}s; no Render gratuito leva ~1 min)"
  while true; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$API_URL/actuator/health" || true)"
    [[ "$code" == "200" ]] && break
    (( $(date +%s) - start >= WAKE_TIMEOUT )) && die "a API não respondeu em ${WAKE_TIMEOUT}s (último status: ${code:-sem resposta})."
    printf '    ainda não (status %s), tentando de novo em 5s...\n' "${code:-000}"
    sleep 5
  done
  echo "    API no ar após $(( $(date +%s) - start ))s."
}

# ------------------------------------------------------------- utilidades ---
# Data local (Brasília) a partir de hoje + offset, avançando até o dia da semana.
event_day() {
  local offset="$1" weekday="$2" day
  day="$(TZ="$TZ_EVENTS" date -d "$(TZ="$TZ_EVENTS" date +%F) +$offset days" +%F)"
  while [[ "$(date -d "$day" +%u)" != "$weekday" ]]; do
    day="$(date -d "$day +1 day" +%F)"
  done
  echo "$day"
}

to_utc() { date -u -d "TZ=\"$TZ_EVENTS\" $1 $2" +%Y-%m-%dT%H:%M:%SZ; }
plus_hours() { date -u -d "$1 +$2 hours" +%Y-%m-%dT%H:%M:%SZ; }
local_label() { TZ="$TZ_EVENTS" date -d "$1" '+%d/%m/%Y %H:%M'; }
money() { LC_NUMERIC=C printf 'R$ %.2f' "$1" | tr '.' ','; }
# printf alinha por bytes; aqui a largura é contada em caracteres (acentos).
pad() { local text="$1" width="$2"; printf '%s%*s' "$text" "$(( width > ${#text} ? width - ${#text} : 0 ))" ''; }

check_images() {
  local missing=0 url code type
  log "Conferindo as fotos em $IMAGE_BASE"
  while read -r image; do
    url="$IMAGE_BASE/$image"
    read -r code type < <(curl -s -o /dev/null -L -w '%{http_code} %{content_type}\n' --max-time 30 "$url" || echo "000 -")
    if [[ "$code" != "200" || "$type" != image/* ]]; then
      echo "    sem foto: $url (HTTP $code, $type)"
      missing=1
    fi
  done < <(jq -r '.[].image' <<<"$EVENTS_JSON" | sort -u)
  [[ "$missing" == "0" ]] && { echo "    todas as fotos respondem."; return; }
  local msg="as fotos ainda não estão publicadas. Publique o front com public/demo/ antes, ou os eventos ficam com imagem quebrada."
  if [[ "$DRY_RUN" == "1" || "$IS_LOCAL" == "1" || "${TICKETFY_SKIP_IMAGE_CHECK:-0}" == "1" ]]; then
    echo "AVISO: $msg"
  else
    die "$msg (para criar mesmo assim: TICKETFY_SKIP_IMAGE_CHECK=1)"
  fi
}

# ------------------------------------------------------------------ início ---
if [[ "$DRY_RUN" == "1" ]]; then
  log "MODO SIMULAÇÃO: nada será criado"
fi
log "API: $API_URL | origem do login: $ORIGIN | fotos: $IMAGE_BASE"

jq -e 'length > 0' <<<"$EVENTS_JSON" >/dev/null || die "lista de eventos inválida."
wake_api

log "Conferindo as categorias da API"
api 200 GET /events/categories
mapfile -t API_CATEGORIES < <(jq -r '.[].category' <<<"$BODY")
while read -r cat; do
  printf '%s\n' "${API_CATEGORIES[@]}" | grep -qx "$cat" \
    || die "a categoria $cat não existe nesta API (existem: ${API_CATEGORIES[*]})."
done < <(jq -r '.[].category' <<<"$EVENTS_JSON" | sort -u)
echo "    ok: $(jq -r '[.[].category] | unique | join(", ")' <<<"$EVENTS_JSON")"

check_images

declare -A EXISTING=()
if [[ "$HAVE_CREDENTIALS" == "1" ]]; then
  log "Entrando como $TICKETFY_EMAIL"
  login
  api 200 GET /users/me
  ROLE="$(jq -r '.role' <<<"$BODY")"
  echo "    papel da conta: $ROLE"
  if [[ "$ROLE" != "ORGANIZER" && "$ROLE" != "ADMIN" ]]; then
    msg="a conta precisa ser ORGANIZER (hoje é $ROLE). No site: Minha conta > virar organizador, ou POST /users/me/organizer logado."
    [[ "$DRY_RUN" == "1" ]] && echo "AVISO: $msg" || die "$msg"
  fi

  page=0
  while true; do
    api 200 GET "/events/mine?size=100&page=$page"
    while IFS=$'\t' read -r id name; do
      [[ -n "$id" ]] && EXISTING["$name"]="$id"
    done < <(jq -r '.content[] | [.id, .name] | @tsv' <<<"$BODY")
    total_pages="$(jq -r '.page.totalPages // .totalPages // 1' <<<"$BODY")"
    page=$((page + 1))
    (( page < total_pages )) || break
  done
  echo "    eventos já cadastrados por esta conta: ${#EXISTING[@]}"
else
  echo "    sem TICKETFY_EMAIL/TICKETFY_PASSWORD: a simulação não confere o que já existe."
fi

SUMMARY=()
CREATED=0
SKIPPED=0
count="$(jq 'length' <<<"$EVENTS_JSON")"

for ((i = 0; i < count; i++)); do
  ev="$(jq -c ".[$i]" <<<"$EVENTS_JSON")"
  name="$(jq -r '.name' <<<"$ev")"
  day="$(event_day "$(jq -r '.dayOffset' <<<"$ev")" "$(jq -r '.weekday' <<<"$ev")")"
  starts="$(to_utc "$day" "$(jq -r '.time' <<<"$ev")")"
  ends="$(plus_hours "$starts" "$(jq -r '.hours' <<<"$ev")")"
  city="$(jq -r '"\(.city)/\(.state)"' <<<"$ev")"
  echo
  log "$name — $city — $(local_label "$starts") — $(jq -r '.category' <<<"$ev")"

  event_id="${EXISTING[$name]:-}"
  missing_lots="$(jq -c '.lots' <<<"$ev")"
  if [[ -n "$event_id" ]]; then
    api 200 GET "/events/$event_id/ticket-types/manage"
    missing_lots="$(jq -c --argjson have "$(jq '[.[].name]' <<<"$BODY")" \
      '[.lots[] | select(.name as $n | $have | index($n) | not)]' <<<"$ev")"
    echo "    já existe ($event_id): evento pulado"
    status="já existia"
    SKIPPED=$((SKIPPED + 1))
  elif [[ "$DRY_RUN" == "1" ]]; then
    echo "    criaria o evento (foto $IMAGE_BASE/$(jq -r '.image' <<<"$ev"))"
    status="seria criado"
  else
    jq --arg img "$IMAGE_BASE/" --arg st "$starts" --arg en "$ends" --arg note "$DEMO_NOTE" \
      '{name, description: (.description + "\n\n" + $note), imageUrl: ($img + .image), venueName, address,
        city, state, startsAt: $st, endsAt: $en, category}' <<<"$ev" >"$BODY_FILE"
    api 201 POST /events "$BODY_FILE"
    event_id="$(jq -r '.id' <<<"$BODY")"
    echo "    evento criado: $event_id"
    status="criado"
    CREATED=$((CREATED + 1))
  fi

  nlots="$(jq 'length' <<<"$missing_lots")"
  for ((j = 0; j < nlots; j++)); do
    lot="$(jq -c ".[$j]" <<<"$missing_lots")"
    lot_label="$(jq -r '.name' <<<"$lot") — $(money "$(jq -r '.price' <<<"$lot")") — $(jq -r '.quantityTotal' <<<"$lot") ingressos"
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "    criaria o lote: $lot_label"
    else
      jq '{name, description, price, quantityTotal, maxPerOrder, maxPerBuyer}' <<<"$lot" >"$BODY_FILE"
      api 201 POST "/events/$event_id/ticket-types" "$BODY_FILE"
      echo "    lote criado: $lot_label"
    fi
  done
  if [[ -n "${EXISTING[$name]:-}" && "$nlots" -gt 0 ]]; then
    [[ "$DRY_RUN" == "1" ]] && status="já existia, faltam $nlots lote(s)" || status="já existia, $nlots lote(s) completado(s)"
  fi

  SUMMARY+=("$(pad "$name" 42) $(pad "$city" 18) $(pad "$(local_label "$starts")" 17) $status")
done

echo
echo "================ Resumo ================"
echo "$(pad EVENTO 42) $(pad CIDADE 18) $(pad "INÍCIO (BRT)" 17) SITUAÇÃO"
printf '%s\n' "${SUMMARY[@]}"
echo
if [[ "$DRY_RUN" == "1" ]]; then
  echo "Simulação concluída: nada foi criado."
else
  echo "Concluído: $CREATED evento(s) criado(s), $SKIPPED já existiam."
fi
