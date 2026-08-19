#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly EXPECTED_BRANCH="reference-readme-literal"
readonly CARD_PASSWORD="${VALIDATION_CARD_PASSWORD:-1234}"
readonly KEEP_ARTIFACTS="${VALIDATION_KEEP_ARTIFACTS:-0}"

readonly RUN_ID="$(date +%s)$(printf '%05d' "$((BASHPID % 100000))")"
readonly PRIMARY_PAN="654987${RUN_ID}"
readonly CONCURRENT_PAN="754987${RUN_ID}"
readonly UNKNOWN_PAN="11112222${RUN_ID}"
readonly MYSQL_CONTAINER="mini-authorizer-literal-${RUN_ID}"

ARTIFACT_DIR=""
EXECUTION_ROOT=""
APP_PID=""
APP_PGID=""
APP_LOG=""
APP_PORT=""
BASE_URL=""
MYSQL_PORT=""
MYSQL_STARTED=0
REQUEST_SEQUENCE=0
LAST_BODY=""
LAST_HEADERS=""
LAST_STATUS=""

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

stop_mysql() {
    if [[ "$MYSQL_STARTED" == "1" ]]; then
        docker stop "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
        MYSQL_STARTED=0
    fi
}

cleanup() {
    local exit_code=$?
    trap - EXIT
    set +e

    stop_application
    stop_mysql

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

response_header() {
    local name=$1

    awk -v header="$name" '
        tolower($1) == tolower(header ":") {
            sub(/^[^:]+:[[:space:]]*/, ""); sub(/\r$/, ""); print; exit
        }
    ' "$LAST_HEADERS"
}

assert_content_type() {
    local expected_prefix=$1
    local description=$2
    local actual

    actual="$(response_header Content-Type)"
    [[ "$actual" == "$expected_prefix"* ]] \
        || die "$description: Content-Type esperado '$expected_prefix', recebido '$actual'"
    pass "$description usa Content-Type $expected_prefix"
}

assert_exact_body() {
    local expected=$1
    local description=$2
    local actual

    actual="$(<"$LAST_BODY")"
    [[ "$actual" == "$expected" ]] \
        || die "$description: body esperado '$expected', recebido '$actual'"
    pass "$description retorna body literal '$expected'"
}

request() {
    local label=$1
    local method=$2
    local path=$3
    local payload=${4:-}
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
    if [[ -n "$payload" ]]; then
        curl_args+=(--header 'Content-Type: application/json' --data "$payload")
    fi

    LAST_STATUS="$(curl "${curl_args[@]}" "$BASE_URL$path")"
}

concurrent_post() {
    local body_file=$1
    local headers_file=$2
    local status_file=$3
    local payload=$4

    curl --silent --show-error --connect-timeout 3 --max-time 30 \
        --request POST \
        --header 'Content-Type: application/json' \
        --data "$payload" \
        --dump-header "$headers_file" \
        --output "$body_file" \
        --write-out '%{http_code}' \
        "$BASE_URL/transacoes" >"$status_file"
}

preflight() {
    info "Validando pré-requisitos"

    require_command awk
    require_command curl
    require_command docker
    require_command git
    require_command grep
    require_command java
    require_command jq
    require_command paste
    require_command sed
    require_command setsid
    require_command sort
    require_command tar
    require_command tee

    local java_major branch
    java_major="$(java -XshowSettings:properties -version 2>&1 \
        | awk -F'= ' '/java.specification.version/ { print $2; exit }')"
    [[ "$java_major" =~ ^[0-9]+$ ]] && ((java_major >= 21)) \
        || die "Java 21 ou superior é obrigatório; versão ativa: ${java_major:-desconhecida}"

    docker version >/dev/null || die "Docker não está disponível"
    branch="$(git -C "$PROJECT_ROOT" branch --show-current)"
    [[ "$branch" == "$EXPECTED_BRANCH" ]] \
        || die "Execute este script na branch $EXPECTED_BRANCH; branch atual: $branch"

    [[ "$PRIMARY_PAN" =~ ^[0-9]{1,32}$ ]] || die "PAN principal inválido: $PRIMARY_PAN"
    [[ "$CONCURRENT_PAN" =~ ^[0-9]{1,32}$ ]] || die "PAN concorrente inválido: $CONCURRENT_PAN"
    [[ "$UNKNOWN_PAN" =~ ^[0-9]{1,32}$ ]] || die "PAN inexistente inválido: $UNKNOWN_PAN"

    pass "Java, Docker, ferramentas e branch validados"
}

create_isolated_copy() {
    info "Criando cópia isolada da branch em /tmp"

    EXECUTION_ROOT="$ARTIFACT_DIR/project"
    mkdir -p "$EXECUTION_ROOT"
    git -C "$PROJECT_ROOT" archive --format=tar HEAD | tar -xf - -C "$EXECUTION_ROOT"

    [[ -x "$EXECUTION_ROOT/mvnw" ]] || die "Maven Wrapper não foi copiado corretamente"
    grep -Eq 'image:[[:space:]]*mysql:5\.7' "$EXECUTION_ROOT/docker/docker-compose.yml" \
        || die "A branch não mantém a imagem mysql:5.7 declarada no Compose"

    pass "Fontes e target isolados em $EXECUTION_ROOT"
}

start_isolated_mysql() {
    info "Iniciando MySQL 5.7 isolado"

    docker run --detach --rm \
        --name "$MYSQL_CONTAINER" \
        --env MYSQL_DATABASE=miniautorizador \
        --env MYSQL_ALLOW_EMPTY_PASSWORD=yes \
        --publish 127.0.0.1::3306 \
        mysql:5.7 >/dev/null
    MYSQL_STARTED=1

    for _ in {1..90}; do
        if docker exec "$MYSQL_CONTAINER" mysqladmin ping -uroot --silent >/dev/null 2>&1; then
            MYSQL_PORT="$(docker port "$MYSQL_CONTAINER" 3306/tcp | awk -F: 'END { print $NF }')"
            [[ "$MYSQL_PORT" =~ ^[0-9]+$ ]] || die "Não foi possível descobrir a porta do MySQL"
            pass "MySQL 5.7 disponível na porta efêmera $MYSQL_PORT"
            return
        fi
        sleep 1
    done

    die "MySQL não ficou disponível dentro do prazo"
}

start_application() {
    info "Iniciando aplicação literal em porta efêmera"

    APP_LOG="$ARTIFACT_DIR/application.log"
    setsid env \
        SPRING_PROFILES_ACTIVE=avaliacao \
        SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$MYSQL_PORT/miniautorizador?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
        SPRING_DATASOURCE_USERNAME=root \
        SPRING_DATASOURCE_PASSWORD= \
        SERVER_PORT=0 \
        "$EXECUTION_ROOT/mvnw" -f "$EXECUTION_ROOT/pom.xml" spring-boot:run >"$APP_LOG" 2>&1 &
    APP_PID=$!
    APP_PGID=$APP_PID

    for _ in {1..90}; do
        if ! kill -0 "$APP_PID" 2>/dev/null; then
            die "A aplicação encerrou antes de ficar pronta"
        fi

        APP_PORT="$(sed -n 's/.*Tomcat started on port \([0-9][0-9]*\).*/\1/p' "$APP_LOG" | tail -n 1)"
        if [[ -n "$APP_PORT" ]]; then
            BASE_URL="http://127.0.0.1:$APP_PORT"
            if [[ "$(curl --silent --output /dev/null --write-out '%{http_code}' \
                --connect-timeout 1 --max-time 2 "$BASE_URL/actuator/health" 2>/dev/null || true)" == "200" ]]; then
                sleep 1
                kill -0 "$APP_PID" 2>/dev/null || die "A aplicação encerrou após o readiness check"
                pass "Aplicação disponível na porta efêmera $APP_PORT"
                return
            fi
        fi
        sleep 1
    done

    die "A aplicação não ficou disponível dentro do prazo"
}

scenario_literal_contract() {
    info "Validando contrato literal de cartões e transações"

    local create_payload transaction_payload expected_card
    create_payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senha: $password}')"
    expected_card="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{senha: $password, numeroCartao: $pan}')"

    request create-card POST /cartoes "$create_payload"
    assert_status 201 "Criação do cartão sem autenticação"
    assert_content_type application/json "Criação do cartão"
    jq -e --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '. == {senha: $password, numeroCartao: $pan}' "$LAST_BODY" >/dev/null \
        || die "Criação não devolveu literalmente senha e número do cartão"
    [[ "$(jq -c . "$LAST_BODY")" == "$expected_card" ]] \
        || die "Representação de criação contém campos adicionais"
    pass "Criação devolve literalmente senha e número do cartão"

    request duplicate-card POST /cartoes "$create_payload"
    assert_status 422 "Criação duplicada"
    assert_content_type application/json "Criação duplicada"
    [[ "$(jq -c . "$LAST_BODY")" == "$expected_card" ]] \
        || die "Duplicidade não repetiu o body literal do cartão"
    pass "Duplicidade repete o cartão no body"

    request initial-balance GET "/cartoes/$PRIMARY_PAN"
    assert_status 200 "Consulta do saldo inicial"
    assert_content_type text/plain "Consulta de saldo"
    assert_exact_body 500.00 "Saldo inicial"

    transaction_payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 10}')"
    request first-transaction POST /transacoes "$transaction_payload"
    assert_status 201 "Primeira transação"
    assert_content_type text/plain "Transação aprovada"
    assert_exact_body OK "Transação aprovada"

    request balance-after-first GET "/cartoes/$PRIMARY_PAN"
    assert_status 200 "Saldo após primeira transação"
    assert_exact_body 490.00 "Saldo após primeira transação"

    transaction_payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 490}')"
    request consume-balance POST /transacoes "$transaction_payload"
    assert_status 201 "Transação que consome o saldo"
    assert_exact_body OK "Transação que consome o saldo"

    request zero-balance GET "/cartoes/$PRIMARY_PAN"
    assert_status 200 "Consulta de saldo zerado"
    assert_exact_body 0.00 "Saldo zerado"

    transaction_payload="$(jq -cn --arg pan "$PRIMARY_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 0.01}')"
    request insufficient-balance POST /transacoes "$transaction_payload"
    assert_status 422 "Transação sem saldo"
    assert_content_type text/plain "Recusa por saldo"
    assert_exact_body SALDO_INSUFICIENTE "Recusa por saldo"

    transaction_payload="$(jq -cn --arg pan "$PRIMARY_PAN" \
        '{numeroCartao: $pan, senhaCartao: "9999", valor: 10}')"
    request invalid-password POST /transacoes "$transaction_payload"
    assert_status 422 "Transação com senha inválida"
    assert_exact_body SENHA_INVALIDA "Recusa por senha"

    transaction_payload="$(jq -cn --arg pan "$UNKNOWN_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 10}')"
    request unknown-card POST /transacoes "$transaction_payload"
    assert_status 422 "Transação com cartão inexistente"
    assert_exact_body CARTAO_INEXISTENTE "Recusa por cartão inexistente"

    request missing-balance GET "/cartoes/$UNKNOWN_PAN"
    assert_status 404 "Consulta de cartão inexistente"
    [[ ! -s "$LAST_BODY" ]] || die "Consulta inexistente deveria responder sem body"
    pass "Consulta inexistente responde sem body"
}

scenario_omitted_good_practices() {
    info "Validando ausências exigidas pelo contrato literal"

    request versioned-route GET /api/v1/cards/00000000-0000-0000-0000-000000000000
    assert_status 404 "Endpoint versionado ausente"

    request openapi GET /v3/api-docs
    assert_status 404 "Contrato OpenAPI ausente"

    request swagger GET /swagger-ui/index.html
    assert_status 404 "Swagger UI ausente"

    pass "API opera sem X-API-Key, rate limiting e contrato OpenAPI"
}

scenario_persistence() {
    info "Validando persistência mantida onde o README é silencioso"

    local stored
    stored="$(docker exec "$MYSQL_CONTAINER" mysql -uroot -N -B miniautorizador -e \
        "SELECT CONCAT(password_hash, '|', balance) FROM cards WHERE card_number = '$PRIMARY_PAN';")"
    [[ "$stored" == 'pbkdf2-sha256$'*'|0.00' ]] \
        || die "Senha não foi persistida como PBKDF2 ou saldo final não é 0.00: $stored"
    [[ "$stored" != "$CARD_PASSWORD|"* ]] || die "Senha foi persistida em texto puro"
    pass "Senha permanece em hash PBKDF2 e saldo autorizado foi persistido"
}

scenario_concurrency() {
    info "Validando desafio de concorrência"

    local create_payload transaction_payload prefix first_pid second_pid statuses loser_body
    create_payload="$(jq -cn --arg pan "$CONCURRENT_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senha: $password}')"
    request create-concurrent-card POST /cartoes "$create_payload"
    assert_status 201 "Criação do cartão para concorrência"

    transaction_payload="$(jq -cn --arg pan "$CONCURRENT_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 490}')"
    request prepare-concurrent-balance POST /transacoes "$transaction_payload"
    assert_status 201 "Preparação do saldo concorrente em 10.00"

    transaction_payload="$(jq -cn --arg pan "$CONCURRENT_PAN" --arg password "$CARD_PASSWORD" \
        '{numeroCartao: $pan, senhaCartao: $password, valor: 10}')"
    prefix="$ARTIFACT_DIR/concurrent-transaction"
    concurrent_post "$prefix-1.body" "$prefix-1.headers" "$prefix-1.status" "$transaction_payload" &
    first_pid=$!
    concurrent_post "$prefix-2.body" "$prefix-2.headers" "$prefix-2.status" "$transaction_payload" &
    second_pid=$!
    wait "$first_pid"
    wait "$second_pid"

    statuses="$(printf '%s\n' "$(<"$prefix-1.status")" "$(<"$prefix-2.status")" \
        | sort -n | paste -sd, -)"
    [[ "$statuses" == "201,422" ]] \
        || die "Transações concorrentes deveriam produzir 201 e 422; recebido $statuses"

    loser_body="$prefix-1.body"
    [[ "$(<"$prefix-2.status")" == "422" ]] && loser_body="$prefix-2.body"
    [[ "$(<"$loser_body")" == "SALDO_INSUFICIENTE" ]] \
        || die "Transação concorrente recusada não retornou SALDO_INSUFICIENTE"
    pass "Somente uma das transações concorrentes foi autorizada"

    request concurrent-final-balance GET "/cartoes/$CONCURRENT_PAN"
    assert_status 200 "Consulta após concorrência"
    assert_exact_body 0.00 "Saldo final após concorrência"
}

scenario_regression() {
    info "Executando regressão automatizada"

    stop_application
    stop_mysql

    local unit_log="$ARTIFACT_DIR/maven-test.log"
    local verify_log="$ARTIFACT_DIR/maven-verify.log"

    (cd "$EXECUTION_ROOT" && ./mvnw test) | tee "$unit_log"
    grep -E 'Tests run: 32, Failures: 0, Errors: 0, Skipped: 0' "$unit_log" >/dev/null \
        || die "A suíte unitária não registrou os 32 testes esperados"
    grep -F 'BUILD SUCCESS' "$unit_log" >/dev/null || die "mvn test não concluiu com BUILD SUCCESS"
    pass "mvn test executou 32 testes com sucesso"

    (cd "$EXECUTION_ROOT" && ./mvnw clean verify) | tee "$verify_log"
    grep -E 'Tests run: 3, Failures: 0, Errors: 0, Skipped: 0' "$verify_log" >/dev/null \
        || die "A suíte de integração não registrou os 3 testes esperados"
    grep -F 'BUILD SUCCESS' "$verify_log" >/dev/null \
        || die "mvn clean verify não concluiu com BUILD SUCCESS"
    pass "mvn clean verify executou os 3 testes de integração com MySQL 5.7"
}

main() {
    ARTIFACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mini-authorizer-readme-literal-validation.XXXXXX")"

    preflight
    create_isolated_copy
    start_isolated_mysql
    start_application
    scenario_literal_contract
    scenario_omitted_good_practices
    scenario_persistence
    scenario_concurrency
    scenario_regression

    info "Todos os cenários literais do README foram aprovados"
    printf 'Execução isolada: porta HTTP %s; PANs descartáveis %s e %s\n' \
        "$APP_PORT" "$PRIMARY_PAN" "$CONCURRENT_PAN"
}

main "$@"
