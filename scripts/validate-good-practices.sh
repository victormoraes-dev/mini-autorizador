#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.yml"

readonly SERVER_PORT="${VALIDATION_SERVER_PORT:-8080}"
readonly BASE_URL="${VALIDATION_BASE_URL:-http://localhost:$SERVER_PORT}"
readonly READER_KEY="${VALIDATION_READER_KEY:-reader-timeline-key-with-32-characters}"
readonly WRITER_KEY="${VALIDATION_WRITER_KEY:-writer-timeline-key-with-32-characters}"
readonly CARD_PASSWORD="${VALIDATION_CARD_PASSWORD:-1234}"
readonly MYSQL_CONTAINER="${VALIDATION_MYSQL_CONTAINER:-mysql}"
readonly KEEP_ARTIFACTS="${VALIDATION_KEEP_ARTIFACTS:-0}"
readonly KEEP_MYSQL="${VALIDATION_KEEP_MYSQL:-0}"

readonly RUN_ID="$(date +%s)$(printf '%04d' "$((BASHPID % 10000))")"
readonly PRIMARY_PAN="654987${RUN_ID}"
readonly CONCURRENT_CREATE_PAN="754987${RUN_ID}"
readonly CONCURRENT_DEBIT_PAN="854987${RUN_ID}"
readonly UNKNOWN_CARD_ID="00000000-0000-0000-0000-000000000001"

ARTIFACT_DIR=""
APP_PID=""
APP_PGID=""
APP_LOG=""
MYSQL_WAS_RUNNING=0
REQUEST_SEQUENCE=0
LAST_BODY=""
LAST_HEADERS=""
LAST_STATUS=""
SENSITIVE_RESPONSE_FILES=()

info() {
    printf '\n==> %s\n' "$*"
}

pass() {
    printf '  [OK] %s\n' "$*"
}

die() {
    printf '  [ERRO] %s\n' "$*" >&2
    if [[ -n "$APP_LOG" && -f "$APP_LOG" ]]; then
        printf '\nÚltimas linhas da aplicação (%s):\n' "$APP_LOG" >&2
        tail -n 40 "$APP_LOG" >&2 || true
    fi
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Comando obrigatório não encontrado: $1"
}

stop_application() {
    if [[ -z "$APP_PGID" ]]; then
        return
    fi

    if kill -0 -- "-$APP_PGID" 2>/dev/null; then
        kill -TERM -- "-$APP_PGID" 2>/dev/null || true
        for _ in {1..20}; do
            kill -0 -- "-$APP_PGID" 2>/dev/null || break
            sleep 1
        done
        if kill -0 -- "-$APP_PGID" 2>/dev/null; then
            kill -KILL -- "-$APP_PGID" 2>/dev/null || true
        fi
    fi

    if [[ -n "$APP_PID" ]]; then
        wait "$APP_PID" 2>/dev/null || true
    fi

    APP_PID=""
    APP_PGID=""
}

cleanup() {
    local exit_code=$?
    trap - EXIT
    set +e

    stop_application

    if [[ "$MYSQL_WAS_RUNNING" == "0" && "$KEEP_MYSQL" != "1" ]]; then
        docker compose -f "$COMPOSE_FILE" stop mysql >/dev/null 2>&1
    fi

    if [[ -n "$ARTIFACT_DIR" && -d "$ARTIFACT_DIR" ]]; then
        if [[ "$exit_code" == "0" && "$KEEP_ARTIFACTS" != "1" ]]; then
            rm -rf -- "$ARTIFACT_DIR"
        else
            printf '\nEvidências preservadas em: %s\n' "$ARTIFACT_DIR" >&2
        fi
    fi

    exit "$exit_code"
}

trap cleanup EXIT

assert_status() {
    local expected=$1
    local description=$2
    [[ "$LAST_STATUS" == "$expected" ]] \
        || die "$description: esperado HTTP $expected, recebido HTTP $LAST_STATUS; corpo: $(tr '\n' ' ' <"$LAST_BODY")"
    pass "$description (HTTP $expected)"
}

assert_json() {
    local expression=$1
    local description=$2
    jq -e "$expression" "$LAST_BODY" >/dev/null \
        || die "$description: JSON inesperado em $LAST_BODY"
    pass "$description"
}

assert_problem() {
    local expected_status=$1
    local expected_code=$2
    local description=$3

    assert_status "$expected_status" "$description"
    jq -e --argjson status "$expected_status" --arg code "$expected_code" \
        '.status == $status and .code == $code and
         (.type | type == "string") and (.title | type == "string") and
         (.detail | type == "string") and (.instance | type == "string")' \
        "$LAST_BODY" >/dev/null \
        || die "$description: Problem Details inválido ou código diferente de $expected_code"
    pass "$description retorna Problem Details com código $expected_code"
}

assert_header() {
    local name=$1
    local expected=$2
    local description=$3
    local actual

    actual="$(awk -v header="$name" '
        tolower($1) == tolower(header ":") {
            sub(/^[^:]+:[[:space:]]*/, ""); sub(/\r$/, ""); print; exit
        }
    ' "$LAST_HEADERS")"
    [[ "$actual" == "$expected" ]] \
        || die "$description: header $name esperado '$expected', recebido '$actual'"
    pass "$description"
}

assert_response_hides() {
    local file=$1
    shift
    local secret
    for secret in "$@"; do
        [[ -z "$secret" ]] && continue
        if grep -F -- "$secret" "$file" >/dev/null; then
            die "Resposta $file expôs dado sensível: $secret"
        fi
    done
}

track_sensitive_response() {
    SENSITIVE_RESPONSE_FILES+=("$LAST_BODY")
}

request() {
    local label=$1
    local method=$2
    local path=$3
    local api_key=${4:-}
    local payload=${5:-}
    local safe_label
    local -a curl_args

    ((REQUEST_SEQUENCE += 1))
    safe_label="${label//[^[:alnum:]_-]/-}"
    LAST_BODY="$ARTIFACT_DIR/$(printf '%03d' "$REQUEST_SEQUENCE")-${safe_label}.body"
    LAST_HEADERS="$ARTIFACT_DIR/$(printf '%03d' "$REQUEST_SEQUENCE")-${safe_label}.headers"

    curl_args=(
        --silent --show-error
        --connect-timeout 3 --max-time 20
        --request "$method"
        --dump-header "$LAST_HEADERS"
        --output "$LAST_BODY"
        --write-out '%{http_code}'
    )
    if [[ -n "$api_key" ]]; then
        curl_args+=(--header "X-API-Key: $api_key")
    fi
    if [[ -n "$payload" ]]; then
        curl_args+=(--header 'Content-Type: application/json' --data "$payload")
    fi

    LAST_STATUS="$(curl "${curl_args[@]}" "$BASE_URL$path")"
}

wait_for_status() {
    local path=$1
    local expected=$2
    local api_key=${3:-}
    local status="000"
    local -a headers=()

    [[ -n "$api_key" ]] && headers=(--header "X-API-Key: $api_key")

    for _ in {1..90}; do
        if [[ -n "$APP_PID" ]] && ! kill -0 "$APP_PID" 2>/dev/null; then
            die "A aplicação encerrou antes de ficar pronta"
        fi
        status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
            --connect-timeout 1 --max-time 2 "${headers[@]}" "$BASE_URL$path" 2>/dev/null || true)"
        [[ "$status" == "$expected" ]] && return
        sleep 1
    done

    die "A aplicação não respondeu HTTP $expected em $path dentro do prazo (último status: $status)"
}

start_application() {
    local profile=$1
    local rate_limit=$2
    local ready_path=$3
    local ready_status=$4
    local ready_key=${5:-}
    local rate_label=${rate_limit:-default}
    local -a application_env=(
        "SPRING_PROFILES_ACTIVE=$profile"
        "APP_SECURITY_READER_API_KEY=$READER_KEY"
        "APP_SECURITY_WRITER_API_KEY=$WRITER_KEY"
        "SERVER_PORT=$SERVER_PORT"
    )

    if [[ -n "$rate_limit" ]]; then
        application_env+=(
            "APP_SECURITY_RATE_LIMIT_REQUESTS=$rate_limit"
            "APP_SECURITY_RATE_LIMIT_WINDOW_SECONDS=60"
        )
    fi
    if [[ "$profile" == "producao" ]]; then
        application_env+=("APP_SECURITY_PASSWORD_PEPPER=timeline-production-pepper")
    fi

    APP_LOG="$ARTIFACT_DIR/application-${profile}-${rate_label}.log"
    info "Iniciando aplicação com perfil $profile e rate limit $rate_label"

    setsid env "${application_env[@]}" \
        "$PROJECT_ROOT/mvnw" spring-boot:run >"$APP_LOG" 2>&1 &
    APP_PID=$!
    APP_PGID=$APP_PID

    wait_for_status "$ready_path" "$ready_status" "$ready_key"
    sleep 1
    kill -0 "$APP_PID" 2>/dev/null || die "A aplicação encerrou logo após responder ao readiness check"
    pass "Aplicação pronta no perfil $profile"
}

concurrent_post() {
    local body_file=$1
    local headers_file=$2
    local status_file=$3
    local path=$4
    local payload=$5

    curl --silent --show-error --connect-timeout 3 --max-time 30 \
        --request POST \
        --header "X-API-Key: $WRITER_KEY" \
        --header 'Content-Type: application/json' \
        --data "$payload" \
        --dump-header "$headers_file" \
        --output "$body_file" \
        --write-out '%{http_code}' \
        "$BASE_URL$path" >"$status_file"
}

assert_concurrent_statuses() {
    local first_status_file=$1
    local second_status_file=$2
    local expected=$3
    local description=$4
    local actual

    actual="$(printf '%s\n' "$(<"$first_status_file")" "$(<"$second_status_file")" | sort -n | paste -sd, -)"
    [[ "$actual" == "$expected" ]] \
        || die "$description: status esperados $expected, recebidos $actual"
    pass "$description ($actual)"
}

preflight() {
    info "Validando pré-requisitos"
    require_command curl
    require_command docker
    require_command jq
    require_command java
    require_command awk
    require_command grep
    require_command sort
    require_command paste
    require_command setsid
    require_command tee

    local java_major
    java_major="$(java -XshowSettings:properties -version 2>&1 \
        | awk -F'= ' '/java.specification.version/ { print $2; exit }')"
    [[ "$java_major" =~ ^[0-9]+$ ]] && ((java_major >= 21)) \
        || die "Java 21 ou superior é obrigatório; versão ativa: ${java_major:-desconhecida}"

    [[ -x "$PROJECT_ROOT/mvnw" ]] || die "Maven Wrapper não é executável: $PROJECT_ROOT/mvnw"
    [[ -f "$COMPOSE_FILE" ]] || die "Compose do MySQL não encontrado: $COMPOSE_FILE"
    docker compose version >/dev/null || die "Docker Compose v2 não está disponível"

    local branch
    branch="$(git -C "$PROJECT_ROOT" branch --show-current)"
    case "$branch" in
        main|feature/api-following-good-practices|reference-good-practices) ;;
        *) printf '  [AVISO] branch atual não é a referência de boas práticas: %s\n' "$branch" ;;
    esac

    [[ "$READER_KEY" != "$WRITER_KEY" ]] || die "As chaves reader e writer devem ser diferentes"
    ((${#READER_KEY} >= 32)) || die "A chave reader deve ter ao menos 32 caracteres"
    ((${#WRITER_KEY} >= 32)) || die "A chave writer deve ter ao menos 32 caracteres"
    [[ "$PRIMARY_PAN" =~ ^[0-9]{1,32}$ ]] || die "PAN de teste inválido: $PRIMARY_PAN"
    [[ "$SERVER_PORT" =~ ^[0-9]+$ ]] && ((SERVER_PORT >= 1 && SERVER_PORT <= 65535)) \
        || die "Porta de teste inválida: $SERVER_PORT"

    if curl --silent --output /dev/null --connect-timeout 1 --max-time 2 "$BASE_URL/actuator/health"; then
        die "Já existe um serviço respondendo em $BASE_URL; interrompa-o antes do teste"
    fi

    pass "Dependências, branch, credenciais e porta validadas"
}

start_mysql() {
    info "T+00 — Preparando MySQL 5.7"

    local container_id
    container_id="$(docker compose -f "$COMPOSE_FILE" ps -q mysql 2>/dev/null || true)"
    if [[ -n "$container_id" ]] && [[ "$(docker inspect --format '{{.State.Running}}' "$container_id")" == "true" ]]; then
        MYSQL_WAS_RUNNING=1
    fi

    docker compose -f "$COMPOSE_FILE" up -d mysql

    for _ in {1..90}; do
        if docker exec "$MYSQL_CONTAINER" mysqladmin ping -uroot --silent >/dev/null 2>&1; then
            pass "MySQL 5.7 disponível"
            return
        fi
        sleep 1
    done

    die "MySQL não ficou disponível dentro do prazo"
}

scenario_contract_and_docs() {
    info "T+02 — Saúde, OpenAPI e Swagger UI"

    request health GET /actuator/health
    assert_status 200 "Health check"
    assert_json '.status == "UP"' "Health indica UP"

    request openapi GET /v3/api-docs
    assert_status 200 "Documento OpenAPI"
    assert_json '
        (.paths | keys | sort) == [
            "/api/v1/cards",
            "/api/v1/cards/{cardId}",
            "/api/v1/transactions"
        ] and
        .components.securitySchemes.apiKey.type == "apiKey" and
        .components.securitySchemes.apiKey.in == "header" and
        .components.securitySchemes.apiKey.name == "X-API-Key"
    ' "OpenAPI expõe somente as rotas canônicas e o security scheme apiKey"

    request swagger GET /swagger-ui/index.html
    assert_status 200 "Swagger UI"
}

scenario_authentication_and_validation() {
    info "T+04/T+06 — Autenticação, autorização e validação"

    request missing-key GET "/api/v1/cards/$UNKNOWN_CARD_ID"
    assert_problem 401 AUTHENTICATION_REQUIRED "Consulta sem API key"

    local create_payload
    create_payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{cardNumber: $pan, password: $password}')"
    request reader-create POST /api/v1/cards "$READER_KEY" "$create_payload"
    assert_problem 403 ACCESS_DENIED "Criação com chave reader"
    track_sensitive_response

    local invalid_payload
    invalid_payload="$(jq -cn --arg password "$CARD_PASSWORD" '{cardNumber: "", password: $password}')"
    request invalid-card POST /api/v1/cards "$WRITER_KEY" "$invalid_payload"
    assert_problem 400 VALIDATION_ERROR "Cartão com número vazio"
    assert_json 'any(.violations[]; .field == "cardNumber")' "Erro identifica o campo cardNumber"
    assert_response_hides "$LAST_BODY" "$CARD_PASSWORD" "$PRIMARY_PAN" "stackTrace"
    track_sensitive_response

    request malformed-transaction POST /api/v1/transactions "$WRITER_KEY" '{invalid-json'
    assert_problem 400 MALFORMED_REQUEST "JSON malformado"
    track_sensitive_response
}

scenario_business_flow() {
    info "T+08/T+11/T+14 — Criação, consulta, autorização e recusas"

    local payload expected_mask location
    payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{cardNumber: $pan, password: $password}')"

    request create-card POST /api/v1/cards "$WRITER_KEY" "$payload"
    assert_status 201 "Criação do cartão"
    PRIMARY_CARD_ID="$(jq -er '.id | select(test("^[0-9a-fA-F-]{36}$"))' "$LAST_BODY")" \
        || die "Criação não retornou UUID público"
    expected_mask="$(printf '%*s%s' "$((${#PRIMARY_PAN} - 4))" '' "${PRIMARY_PAN: -4}" | tr ' ' '*')"
    jq -e --arg id "$PRIMARY_CARD_ID" --arg masked "$expected_mask" \
        '.id == $id and .cardNumber == $masked and .balance == 500' "$LAST_BODY" >/dev/null \
        || die "Representação criada não contém id, PAN mascarado e saldo 500.00"
    location="/api/v1/cards/$PRIMARY_CARD_ID"
    assert_header Location "$location" "Location aponta para o novo recurso"
    assert_response_hides "$LAST_BODY" "$PRIMARY_PAN" "$CARD_PASSWORD" "password" "hash"
    track_sensitive_response

    request duplicate-card POST /api/v1/cards "$WRITER_KEY" "$payload"
    assert_problem 409 CARD_ALREADY_EXISTS "Criação duplicada"
    assert_response_hides "$LAST_BODY" "$PRIMARY_PAN" "$CARD_PASSWORD"
    track_sensitive_response

    request get-created GET "$location" "$READER_KEY"
    assert_status 200 "Consulta do cartão"
    jq -e --arg id "$PRIMARY_CARD_ID" --arg masked "$expected_mask" \
        '.id == $id and .cardNumber == $masked and .balance == 500' "$LAST_BODY" >/dev/null \
        || die "Consulta não retornou representação segura com saldo 500.00"
    pass "Consulta retorna representação segura com saldo 500.00"
    assert_response_hides "$LAST_BODY" "$PRIMARY_PAN" "$CARD_PASSWORD" "password" "hash"
    track_sensitive_response

    local transaction
    transaction="$(jq -cn --arg id "$PRIMARY_CARD_ID" --arg password "$CARD_PASSWORD" \
        '{cardId: $id, password: $password, amount: 10}')"
    request approved-transaction POST /api/v1/transactions "$WRITER_KEY" "$transaction"
    assert_status 200 "Transação de 10.00"
    assert_json '. == {"status": "AUTHORIZED"}' "Transação autorizada"
    track_sensitive_response

    request get-after-debit GET "$location" "$READER_KEY"
    assert_status 200 "Consulta após débito"
    assert_json '.balance == 490' "Saldo atualizado para 490.00"
    track_sensitive_response

    transaction="$(jq -cn --arg id "$PRIMARY_CARD_ID" --arg password "$CARD_PASSWORD" \
        '{cardId: $id, password: $password, amount: 500}')"
    request insufficient-balance POST /api/v1/transactions "$WRITER_KEY" "$transaction"
    assert_problem 422 INSUFFICIENT_BALANCE "Débito acima do saldo"
    track_sensitive_response

    transaction="$(jq -cn --arg id "$PRIMARY_CARD_ID" \
        '{cardId: $id, password: "9999", amount: 500}')"
    request invalid-password POST /api/v1/transactions "$WRITER_KEY" "$transaction"
    assert_problem 422 INVALID_PASSWORD "Senha inválida tem precedência"
    track_sensitive_response

    transaction="$(jq -cn --arg id "$UNKNOWN_CARD_ID" --arg password "$CARD_PASSWORD" \
        '{cardId: $id, password: $password, amount: 10}')"
    request unknown-card POST /api/v1/transactions "$WRITER_KEY" "$transaction"
    assert_problem 404 CARD_NOT_FOUND "Transação para cartão inexistente"
    track_sensitive_response

    request get-unchanged GET "$location" "$READER_KEY"
    assert_status 200 "Consulta após recusas"
    assert_json '.balance == 490' "Recusas não alteram o saldo"
    track_sensitive_response
}

scenario_storage_security() {
    info "T+16 — Persistência e ausência de dados sensíveis"

    local stored
    stored="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -N -B miniautorizador -e \
        "SELECT CONCAT(public_id, '|', password_hash, '|', balance) FROM cards WHERE card_number = '$PRIMARY_PAN';")"
    [[ "$stored" == "$PRIMARY_CARD_ID|pbkdf2-sha256$"*"|490.00" ]] \
        || die "Registro persistido não contém UUID, hash PBKDF2 e saldo 490.00: $stored"
    [[ "$stored" != *"|$CARD_PASSWORD|"* ]] || die "Senha foi persistida em texto puro"
    pass "Banco contém UUID público, hash PBKDF2 e saldo 490.00"

    local response_file
    for response_file in "${SENSITIVE_RESPONSE_FILES[@]}"; do
        assert_response_hides "$response_file" "$PRIMARY_PAN" "$CONCURRENT_CREATE_PAN" \
            "$CONCURRENT_DEBIT_PAN" "$CARD_PASSWORD" "password_hash" "stackTrace"
    done
    pass "Respostas de negócio capturadas não expõem PAN, senha, hash ou stack trace"
}

scenario_concurrency() {
    info "T+19 — Concorrência na criação e no débito"

    local create_payload prefix first_pid second_pid loser_body rows
    create_payload="$(jq -cn --arg pan "$CONCURRENT_CREATE_PAN" --arg password "$CARD_PASSWORD" \
        '{cardNumber: $pan, password: $password}')"
    prefix="$ARTIFACT_DIR/concurrent-create"

    concurrent_post "$prefix-1.body" "$prefix-1.headers" "$prefix-1.status" /api/v1/cards "$create_payload" &
    first_pid=$!
    concurrent_post "$prefix-2.body" "$prefix-2.headers" "$prefix-2.status" /api/v1/cards "$create_payload" &
    second_pid=$!
    wait "$first_pid"
    wait "$second_pid"

    assert_concurrent_statuses "$prefix-1.status" "$prefix-2.status" "201,409" \
        "Criações concorrentes produzem um sucesso e um conflito"
    loser_body="$prefix-1.body"
    [[ "$(<"$prefix-2.status")" == "409" ]] && loser_body="$prefix-2.body"
    jq -e '.code == "CARD_ALREADY_EXISTS" and .status == 409' "$loser_body" >/dev/null \
        || die "Perdedor da criação concorrente não retornou CARD_ALREADY_EXISTS"
    assert_response_hides "$prefix-1.body" "$CONCURRENT_CREATE_PAN" "$CARD_PASSWORD"
    assert_response_hides "$prefix-2.body" "$CONCURRENT_CREATE_PAN" "$CARD_PASSWORD"

    rows="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -N -B miniautorizador -e \
        "SELECT COUNT(*) FROM cards WHERE card_number = '$CONCURRENT_CREATE_PAN';")"
    [[ "$rows" == "1" ]] || die "Criação concorrente persistiu $rows registros; esperado 1"
    pass "Criação concorrente persiste uma única linha"

    local debit_payload debit_card_id transaction
    debit_payload="$(jq -cn --arg pan "$CONCURRENT_DEBIT_PAN" --arg password "$CARD_PASSWORD" \
        '{cardNumber: $pan, password: $password}')"
    request create-debit-card POST /api/v1/cards "$WRITER_KEY" "$debit_payload"
    assert_status 201 "Criação do cartão para concorrência de débito"
    debit_card_id="$(jq -er '.id' "$LAST_BODY")"

    transaction="$(jq -cn --arg id "$debit_card_id" --arg password "$CARD_PASSWORD" \
        '{cardId: $id, password: $password, amount: 490}')"
    request prepare-balance POST /api/v1/transactions "$WRITER_KEY" "$transaction"
    assert_status 200 "Preparação do saldo em 10.00"

    transaction="$(jq -cn --arg id "$debit_card_id" --arg password "$CARD_PASSWORD" \
        '{cardId: $id, password: $password, amount: 10}')"
    prefix="$ARTIFACT_DIR/concurrent-debit"
    concurrent_post "$prefix-1.body" "$prefix-1.headers" "$prefix-1.status" /api/v1/transactions "$transaction" &
    first_pid=$!
    concurrent_post "$prefix-2.body" "$prefix-2.headers" "$prefix-2.status" /api/v1/transactions "$transaction" &
    second_pid=$!
    wait "$first_pid"
    wait "$second_pid"

    assert_concurrent_statuses "$prefix-1.status" "$prefix-2.status" "200,422" \
        "Débitos concorrentes autorizam somente uma transação"
    loser_body="$prefix-1.body"
    [[ "$(<"$prefix-2.status")" == "422" ]] && loser_body="$prefix-2.body"
    jq -e '.code == "INSUFFICIENT_BALANCE" and .status == 422' "$loser_body" >/dev/null \
        || die "Débito concorrente recusado não retornou INSUFFICIENT_BALANCE"
    assert_response_hides "$prefix-1.body" "$CONCURRENT_DEBIT_PAN" "$CARD_PASSWORD"
    assert_response_hides "$prefix-2.body" "$CONCURRENT_DEBIT_PAN" "$CARD_PASSWORD"

    request final-concurrent-balance GET "/api/v1/cards/$debit_card_id" "$READER_KEY"
    assert_status 200 "Consulta após débitos concorrentes"
    assert_json '.balance == 0' "Saldo final é 0.00 sem débito duplicado"
}

scenario_rate_limit() {
    info "T+22 — Rate limit"
    stop_application
    start_application avaliacao 3 /actuator/health 200

    local index
    for index in 1 2 3; do
        request "rate-$index" GET "/api/v1/cards/$PRIMARY_CARD_ID" "$READER_KEY"
        assert_status 200 "Requisição $index dentro do limite"
    done

    request rate-4 GET "/api/v1/cards/$PRIMARY_CARD_ID" "$READER_KEY"
    assert_problem 429 RATE_LIMIT_EXCEEDED "Quarta requisição na janela"
    assert_header Retry-After 60 "Rate limit informa espera de 60 segundos"
}

scenario_production_tls() {
    info "T+25 — TLS obrigatório em produção"
    stop_application
    start_application producao "" "/api/v1/cards/$PRIMARY_CARD_ID" 426 "$READER_KEY"

    request production-http GET "/api/v1/cards/$PRIMARY_CARD_ID" "$READER_KEY"
    assert_problem 426 TLS_REQUIRED "Acesso HTTP no perfil de produção"
}

scenario_automated_regression() {
    info "T+28 — Regressão automatizada"
    stop_application

    local unit_log="$ARTIFACT_DIR/maven-test.log"
    local verify_log="$ARTIFACT_DIR/maven-verify.log"

    (cd "$PROJECT_ROOT" && ./mvnw test) | tee "$unit_log"
    grep -E 'Tests run: 51, Failures: 0, Errors: 0, Skipped: 0' "$unit_log" >/dev/null \
        || die "A suíte unitária não registrou os 51 testes esperados"
    grep -F 'BUILD SUCCESS' "$unit_log" >/dev/null || die "mvn test não concluiu com BUILD SUCCESS"
    pass "mvn test executou 51 testes com sucesso"

    (cd "$PROJECT_ROOT" && ./mvnw verify) | tee "$verify_log"
    grep -E 'Tests run: 6, Failures: 0, Errors: 0, Skipped: 0' "$verify_log" >/dev/null \
        || die "A suíte de integração não registrou os 6 testes esperados"
    grep -F 'BUILD SUCCESS' "$verify_log" >/dev/null || die "mvn verify não concluiu com BUILD SUCCESS"
    pass "mvn verify executou os 6 testes de integração com MySQL 5.7"
}

main() {
    cd "$PROJECT_ROOT"
    ARTIFACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mini-authorizer-timeline.XXXXXX")"

    preflight
    start_mysql
    start_application avaliacao "" /actuator/health 200
    scenario_contract_and_docs
    scenario_authentication_and_validation
    scenario_business_flow
    scenario_storage_security
    scenario_concurrency
    scenario_rate_limit
    scenario_production_tls
    scenario_automated_regression

    info "Todos os cenários da linha do tempo foram aprovados"
    printf 'PANs descartáveis usados: %s, %s, %s\n' \
        "$PRIMARY_PAN" "$CONCURRENT_CREATE_PAN" "$CONCURRENT_DEBIT_PAN"
}

main "$@"
