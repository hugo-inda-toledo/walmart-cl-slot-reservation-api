package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoComunaDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoRegionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP del catálogo geográfico: regiones y comunas por región (requisitos 3.1 a 3.5, 10.1, 10.2).
 */
@IntegrationTest
class GeoCatalogApiIT {

    private static final String CORRELATION_ID = "geo-catalog-it";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void listsTheSixteenRegionsFromNorthToSouth() throws Exception {
        ExchangeResult result = get("/api/v1/regions");

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);

        GeoRegionDto[] regions = jsonMapper.readValue(result.getResponseBodyContent(), GeoRegionDto[].class);
        assertThat(regions).hasSize(16);
        assertThat(regions[0].getCode()).isEqualTo("15");
        assertThat(regions[0].getName()).isEqualTo("Región de Arica y Parinacota");
        assertThat(regions[15].getCode()).isEqualTo("12");
        assertThat(List.of(regions)).extracting(GeoRegionDto::getCode).containsExactly(
                "15", "01", "02", "03", "04", "05", "13", "06", "07", "16", "08", "09", "14", "10", "11", "12");
    }

    @Test
    void listsTheFiftyTwoComunasOfTheMetropolitanRegionInSpanishOrderWithBoundaryFlag() throws Exception {
        ExchangeResult result = get("/api/v1/regions/13/comunas");

        assertThat(result.getStatus().value()).isEqualTo(200);
        GeoComunaDto[] comunas = jsonMapper.readValue(result.getResponseBodyContent(), GeoComunaDto[].class);
        assertThat(comunas).hasSize(52);

        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        Comparator<GeoComunaDto> order = Comparator.comparing(GeoComunaDto::getName, collator)
                .thenComparing(GeoComunaDto::getCode);
        assertThat(List.of(comunas)).isSortedAccordingTo(order);

        assertThat(List.of(comunas)).allSatisfy(comuna -> {
            assertThat(comuna.getCode()).matches("13[0-9]{3}");
            assertThat(comuna.getProvinceCode()).matches("[0-9]{3}");
            assertThat(comuna.getProvinceName()).isNotBlank();
            assertThat(comuna.getHasBoundary()).isTrue();
        });
        List<String> names = List.of(comunas).stream().map(GeoComunaDto::getName).toList();
        assertThat(names.indexOf("Ñuñoa")).isGreaterThan(names.indexOf("Melipilla"));
        assertThat(names.indexOf("Ñuñoa")).isLessThan(names.indexOf("Padre Hurtado"));
    }

    @Test
    void flagsComunasWithoutBoundary() throws Exception {
        GeoComunaDto[] comunas = jsonMapper.readValue(
                get("/api/v1/regions/05/comunas").getResponseBodyContent(), GeoComunaDto[].class);

        assertThat(comunas).isNotEmpty();
        assertThat(List.of(comunas)).allSatisfy(comuna -> assertThat(comuna.getHasBoundary()).isFalse());
    }

    @Test
    void rejectsARegionCodeWithAnInvalidFormatAsValidationError() throws Exception {
        for (String invalid : List.of("1", "abc", "130")) {
            ExchangeResult result = get("/api/v1/regions/" + invalid + "/comunas");

            ProblemDto problem = assertProblem(result, 400);
            assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void answersRegionNotFoundForAWellFormedUnknownRegion() throws Exception {
        ExchangeResult result = get("/api/v1/regions/99/comunas");

        ProblemDto problem = assertProblem(result, 404);
        assertThat(problem.getCode()).isEqualTo("REGION_NOT_FOUND");
    }

    @Test
    void answersNotAcceptableAsProblemDetails() throws Exception {
        ExchangeResult result = client().get().uri("/api/v1/regions")
                .header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_XML)
                .exchange().returnResult();

        ProblemDto problem = assertProblem(result, 406);
        assertThat(problem.getCode()).isEqualTo("NOT_ACCEPTABLE");
    }

    private ProblemDto assertProblem(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        ProblemDto problem = jsonMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(problem.getCorrelationId()).isEqualTo(CORRELATION_ID);
        return problem;
    }

    private ExchangeResult get(String uri) {
        return client().get().uri(uri).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON).exchange().returnResult();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
