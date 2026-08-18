package br.com.vr.miniautorizador;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class MiniAuthorizerReadmeLiteralApiIT {

    private static final String CARD_NUMBER = "6549873025634501";
    private static final String PASSWORD = "1234";

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
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient client;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cards");
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void reproducesTheReadmeContractEndToEnd() {
        HttpResponse created = createCard();
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.contentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(created.body()).isEqualTo(
                "{\"senha\":\"1234\",\"numeroCartao\":\"6549873025634501\"}");

        HttpResponse duplicate = createCard();
        assertThat(duplicate.status()).isEqualTo(422);
        assertThat(duplicate.body()).isEqualTo(created.body());

        assertBalance(CARD_NUMBER, "500.00");
        assertText(authorize(CARD_NUMBER, PASSWORD, "10.00"), 201, "OK");
        assertBalance(CARD_NUMBER, "490.00");

        assertText(authorize(CARD_NUMBER, PASSWORD, "500.00"), 422, "SALDO_INSUFICIENTE");
        assertText(authorize(CARD_NUMBER, "9999", "10.00"), 422, "SENHA_INVALIDA");
        assertText(authorize("1111222233334444", PASSWORD, "10.00"), 422, "CARTAO_INEXISTENTE");

        HttpResponse missingBalance = get("/cartoes/1111222233334444");
        assertThat(missingBalance.status()).isEqualTo(404);
        assertThat(missingBalance.body()).isEmpty();

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM cards WHERE card_number = ?",
                String.class,
                CARD_NUMBER);
        assertThat(storedHash).startsWith("pbkdf2-sha256$").isNotEqualTo(PASSWORD);
    }

    @Test
    void omitsTheVersionedContractAndOpenApi() {
        assertThat(get("/api/v1/cards/00000000-0000-0000-0000-000000000000").status()).isEqualTo(404);
        assertThat(get("/v3/api-docs").status()).isEqualTo(404);
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(404);
    }

    @Test
    void authorizesOnlyOneConcurrentTransactionForTheAvailableBalance() throws Exception {
        createCard();
        jdbcTemplate.update("UPDATE cards SET balance = 10.00 WHERE card_number = ?", CARD_NUMBER);
        CyclicBarrier start = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpResponse> request = () -> {
                start.await();
                return authorize(CARD_NUMBER, PASSWORD, "10.00");
            };
            List<Future<HttpResponse>> futures = List.of(executor.submit(request), executor.submit(request));
            List<HttpResponse> responses = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(responses).anySatisfy(response -> assertText(response, 201, "OK"));
            assertThat(responses).anySatisfy(response -> assertText(response, 422, "SALDO_INSUFICIENTE"));
        }

        assertBalance(CARD_NUMBER, "0.00");
    }

    private HttpResponse createCard() {
        return post("/cartoes", Map.of("numeroCartao", CARD_NUMBER, "senha", PASSWORD));
    }

    private HttpResponse authorize(String cardNumber, String password, String amount) {
        return post("/transacoes", Map.of(
                "numeroCartao", cardNumber,
                "senhaCartao", password,
                "valor", new BigDecimal(amount)));
    }

    private void assertBalance(String cardNumber, String expected) {
        assertText(get("/cartoes/" + cardNumber), 200, expected);
    }

    private static void assertText(HttpResponse response, int status, String body) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.contentType()).startsWith(MediaType.TEXT_PLAIN_VALUE);
        assertThat(response.body()).isEqualTo(body);
    }

    private HttpResponse post(String path, Object body) {
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
        MediaType contentType = response.getHeaders().getContentType();
        return new HttpResponse(
                response.getStatusCode().value(),
                body,
                contentType == null ? "" : contentType.toString());
    }

    private record HttpResponse(int status, String body, String contentType) {
    }
}
