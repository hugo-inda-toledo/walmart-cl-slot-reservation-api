package cl.walmart.slot.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class OpenApiContractIT {

    @LocalServerPort
    private int port;

    @Test
    void servesTheVersionedContractWithoutChangingItsBytes() throws IOException {
        ExchangeResult result = client().get().uri("/openapi/openapi.yml").exchange().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/yaml"));
        assertThat(result.getResponseBodyContent())
                .isEqualTo(new ClassPathResource("openapi/openapi.yml").getContentAsByteArray());
    }

    @Test
    void loadsSwaggerUiConfiguredOnlyWithTheVersionedContract() {
        ExchangeResult redirect = client().get().uri("/swagger-ui.html").exchange().returnResult();

        assertThat(redirect.getStatus().is3xxRedirection()).isTrue();
        assertThat(redirect.getResponseHeaders().getLocation()).isNotNull();

        ExchangeResult page = client().get().uri("/swagger-ui/index.html").exchange().returnResult();
        assertThat(page.getStatus().value()).isEqualTo(200);

        ExchangeResult initializer = client().get().uri("/swagger-ui/swagger-initializer.js").exchange().returnResult();
        assertThat(initializer.getStatus().value()).isEqualTo(200);
        assertThat(new String(initializer.getResponseBodyContent(), StandardCharsets.UTF_8))
                .contains("/openapi/openapi.yml");
    }

    @Test
    void doesNotExposeAnInferredOpenApiContract() {
        ExchangeResult result = client().get().uri("/v3/api-docs").exchange().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(404);
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
