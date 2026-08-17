package br.com.vr.miniautorizador;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@ActiveProfiles("producao")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.require-tls=false",
                "app.security.password.pepper=production-integration-test-pepper"
        })
class MiniAuthorizerProductionApiIT {

    private static final String CARD_NUMBER = "6549873025634501";
    private static final String READER_KEY = "reader-production-test-key-32-chars";
    private static final String WRITER_KEY = "writer-production-test-key-32-chars";

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

    @Test
    void usesTheSameSafeContractInProduction() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", WRITER_KEY)
                .build();

        HttpResponse created = client.post()
                .uri("/api/v1/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cardNumber", CARD_NUMBER, "password", "1234"))
                .exchange((request, response) -> new HttpResponse(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                        response.getHeaders().getLocation().toString()));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.location()).startsWith("/api/v1/cards/").doesNotContain(CARD_NUMBER);
        assertThat(created.body())
                .contains("\"cardNumber\":\"************4501\"")
                .contains("\"balance\":500.00")
                .doesNotContain(CARD_NUMBER, "1234", "password", "hash");
    }

    private record HttpResponse(int status, String body, String location) {
    }
}
