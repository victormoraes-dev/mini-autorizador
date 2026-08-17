package br.com.vr.miniautorizador;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@ActiveProfiles("avaliacao")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MiniAuthorizerApiIT {

    private static final String CARD_NUMBER = "6549873025634501";
    private static final String READER_KEY = "reader-integration-key-32-characters";
    private static final String WRITER_KEY = "writer-integration-key-32-characters";
    private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7")
            .withDatabaseName("miniautorizador")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.security.api.reader-key", () -> READER_KEY);
        registry.add("app.security.api.writer-key", () -> WRITER_KEY);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient client;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cards");
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", WRITER_KEY)
                .build();
    }

    @Test
    void executesBusinessFlowWithSafeRestRepresentations() {
        HttpResponse created = createCard();
        String cardId = extractId(created.body());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.location()).isEqualTo("/api/v1/cards/" + cardId);
        assertSafeCard(created.body(), cardId, "500.00");

        HttpResponse duplicate = createCard();
        assertProblem(duplicate, 409, "CARD_ALREADY_EXISTS");

        assertCard(cardId, "500.00");
        assertThat(authorize(cardId, "1234", "10.00"))
                .isEqualTo(new HttpResponse(200, "{\"status\":\"AUTHORIZED\"}", null));
        assertCard(cardId, "490.00");

        assertProblem(authorize(cardId, "1234", "500.00"), 422, "INSUFFICIENT_BALANCE");
        assertProblem(authorize(cardId, "9999", "10.00"), 422, "INVALID_PASSWORD");
        assertProblem(authorize(UUID.randomUUID().toString(), "1234", "10.00"), 404, "CARD_NOT_FOUND");
        assertProblem(get("/api/v1/cards/" + UUID.randomUUID()), 404, "CARD_NOT_FOUND");
        assertCard(cardId, "490.00");

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT public_id, password_hash FROM cards WHERE card_number = ?",
                CARD_NUMBER);
        assertThat(stored.get("public_id")).isEqualTo(cardId);
        assertThat(stored.get("password_hash").toString())
                .startsWith("pbkdf2-sha256$")
                .isNotEqualTo("1234");
    }

    @Test
    void exposesCanonicalOpenApiAndStructuredValidationErrors() {
        HttpResponse invalidCard = post("/api/v1/cards", Map.of("cardNumber", "", "password", "1234"));
        HttpResponse invalidTransaction = post("/api/v1/transactions", Map.of(
                "cardId", UUID.randomUUID().toString(),
                "password", "1234",
                "amount", BigDecimal.ZERO));
        HttpResponse malformed = postJson("/api/v1/transactions", "{invalid-json");
        HttpResponse invalidId = get("/api/v1/cards/not-a-uuid");

        assertProblem(invalidCard, 400, "VALIDATION_ERROR");
        assertThat(invalidCard.body()).contains("violations", "cardNumber").doesNotContain(CARD_NUMBER);
        assertProblem(invalidTransaction, 400, "VALIDATION_ERROR");
        assertProblem(malformed, 400, "MALFORMED_REQUEST");
        assertProblem(invalidId, 400, "VALIDATION_ERROR");

        HttpResponse openApi = get("/v3/api-docs");
        assertThat(openApi.status()).isEqualTo(200);
        assertThat(openApi.body())
                .contains("\"/api/v1/cards\"")
                .contains("\"/api/v1/cards/{cardId}\"")
                .contains("\"/api/v1/transactions\"")
                .contains("cardNumber", "password", "cardId", "amount")
                .contains("securitySchemes", "X-API-Key", "500")
                .doesNotContain("\"/cartoes\"", "\"/transacoes\"");
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);
        assertThat(get("/cartoes/" + CARD_NUMBER).status()).isEqualTo(404);
    }

    @Test
    void requiresAuthenticationAndEnforcesReaderWriterPermissions() {
        RestClient anonymous = RestClient.builder().baseUrl("http://localhost:" + port).build();
        HttpResponse unauthenticated = anonymous.get()
                .uri("/api/v1/cards/" + UUID.randomUUID())
                .exchange((request, response) -> response(response));

        RestClient reader = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", READER_KEY)
                .build();
        HttpResponse forbidden = reader.post()
                .uri("/api/v1/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cardNumber", CARD_NUMBER, "password", "1234"))
                .exchange((request, response) -> response(response));

        assertProblem(unauthenticated, 401, "AUTHENTICATION_REQUIRED");
        assertProblem(forbidden, 403, "ACCESS_DENIED");
    }

    @Test
    void authorizesOnlyOneOfTwoConcurrentTransactionsForTheSameBalance() throws Exception {
        String cardId = extractId(createCard().body());
        jdbcTemplate.update("UPDATE cards SET balance = 10.00 WHERE public_id = ?", cardId);
        CyclicBarrier start = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpResponse> request = () -> {
                start.await();
                return authorize(cardId, "1234", "10.00");
            };
            List<Future<HttpResponse>> futures = List.of(executor.submit(request), executor.submit(request));
            List<HttpResponse> responses = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(responses).anySatisfy(response -> assertThat(response.status()).isEqualTo(200));
            assertThat(responses).anySatisfy(response -> assertProblem(response, 422, "INSUFFICIENT_BALANCE"));
        }

        assertCard(cardId, "0.00");
    }

    @Test
    void convertsConcurrentCardCreationIntoCreatedAndConflictResponses() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpResponse> request = () -> {
                start.await();
                return createCard();
            };
            List<Future<HttpResponse>> futures = List.of(executor.submit(request), executor.submit(request));
            List<HttpResponse> responses = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(responses).anySatisfy(response -> assertThat(response.status()).isEqualTo(201));
            assertThat(responses).anySatisfy(response -> assertProblem(response, 409, "CARD_ALREADY_EXISTS"));
        }

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cards WHERE card_number = ?",
                Integer.class,
                CARD_NUMBER);
        assertThat(rows).isEqualTo(1);
    }

    private HttpResponse createCard() {
        return post("/api/v1/cards", Map.of("cardNumber", CARD_NUMBER, "password", "1234"));
    }

    private HttpResponse authorize(String cardId, String password, String amount) {
        return post("/api/v1/transactions", Map.of(
                "cardId", cardId,
                "password", password,
                "amount", new BigDecimal(amount)));
    }

    private void assertCard(String cardId, String balance) {
        HttpResponse response = get("/api/v1/cards/" + cardId);
        assertThat(response.status()).isEqualTo(200);
        assertSafeCard(response.body(), cardId, balance);
    }

    private static void assertSafeCard(String body, String cardId, String balance) {
        assertThat(body)
                .contains("\"id\":\"" + cardId + "\"")
                .contains("\"cardNumber\":\"************4501\"")
                .contains("\"balance\":" + balance)
                .doesNotContain(CARD_NUMBER, "1234", "password", "hash");
    }

    private static void assertProblem(HttpResponse response, int status, String code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.body())
                .contains("\"status\":" + status)
                .contains("\"code\":\"" + code + "\"")
                .doesNotContain(CARD_NUMBER, "1234");
    }

    private static String extractId(String body) {
        Matcher matcher = ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as("response contains card id").isTrue();
        return matcher.group(1);
    }

    private HttpResponse post(String path, Object body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> response(response));
    }

    private HttpResponse postJson(String path, String body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> response(response));
    }

    private HttpResponse get(String path) {
        return client.get().uri(path).exchange((request, response) -> response(response));
    }

    private static HttpResponse response(org.springframework.http.client.ClientHttpResponse response)
            throws java.io.IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String location = response.getHeaders().getLocation() == null
                ? null
                : response.getHeaders().getLocation().toString();
        return new HttpResponse(response.getStatusCode().value(), body, location);
    }

    private record HttpResponse(int status, String body, String location) {
    }
}
