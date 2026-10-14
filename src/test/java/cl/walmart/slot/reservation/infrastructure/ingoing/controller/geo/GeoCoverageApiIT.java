package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoCoverageResolutionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDemoAddressDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP de la resolución de cobertura y de las direcciones demo (requisitos 1.6, 5.1 a 5.11, 5.13,
 * 6.1 a 6.5, 8.3, 10.1 a 10.3), contra PostGIS real con los datos semilla.
 */
@IntegrationTest
class GeoCoverageApiIT {

    private static final String CORRELATION_ID = "geo-coverage-it";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void resolvesByComunaBoundary() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Monjitas", "879", "13101", -33.4372, -70.645));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_BOUNDARY");
        assertThat(resolution.getComuna().getCode()).isEqualTo("13101");
        assertThat(resolution.getComuna().getName()).isEqualTo("Santiago");
        assertThat(resolution.getZone().getCode()).isEqualTo("SANTIAGO_CENTRO");
        assertThat(resolution.getZone().getId().toString()).isEqualTo("5f1c0e2a-0000-4000-8000-000000001301");
        assertThat(resolution.getComunaDiffersFromDeclared()).isFalse();
    }

    @Test
    void resolvesByInclusionAreaOverTheCommuneZone() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Camino Chicureo", "1500", "13301", -33.285, -70.65));

        assertThat(resolution.getMethod().getValue()).isEqualTo("ZONE_AREA");
        assertThat(resolution.getZone().getCode()).isEqualTo("CHICUREO");
    }

    @Test
    void resolvesByNearestComunaWithinTheBorderTolerance() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Av. del Mar", "2100", "04101", -29.91, -71.2764));

        assertThat(resolution.getMethod().getValue()).isEqualTo("NEAREST_COMUNA");
        assertThat(resolution.getZone().getCode()).isEqualTo("LA_SERENA_COQUIMBO");
    }

    @Test
    void resolvesByDeclaredComunaWithoutCoordinates() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Aldunate", "350", "04102", null, null));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_CODE");
        assertThat(resolution.getZone().getCode()).isEqualTo("LA_SERENA_COQUIMBO");
    }

    @Test
    void resolvesByDeclaredComunaWhenItHasNoBoundary() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Av. Libertad", "100", "05109", -33.0, -71.55));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_CODE");
        assertThat(resolution.getComuna().getCode()).isEqualTo("05109");
        assertThat(resolution.getZone().getCode()).isEqualTo("LIDER_REGION_05");
    }

    @Test
    void resolvesProductConfirmedMaculByCutToAnActiveZone() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Av. Macul", "3000", "13118", null, null));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_CODE");
        assertThat(resolution.getComuna().getCode()).isEqualTo("13118");
        assertThat(resolution.getZone().getCode()).isEqualTo("LIDER_REGION_13");
    }

    @Test
    void resolvesProductConfirmedMaculThroughItsComunaBoundary() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Av. Macul", "3000", "13118", -33.49, -70.60));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_BOUNDARY");
        assertThat(resolution.getComuna().getCode()).isEqualTo("13118");
        assertThat(resolution.getZone().getCode()).isEqualTo("LIDER_REGION_13");
        assertThat(resolution.getComunaDiffersFromDeclared()).isFalse();
    }

    @Test
    void resolvesOfficialVinaDelMarByCutToAnActiveZoneWithoutCoordinates() throws Exception {
        GeoCoverageResolutionDto resolution = covered(request("Av. Libertad", "100", "05109", null, null));

        assertThat(resolution.getMethod().getValue()).isEqualTo("COMUNA_CODE");
        assertThat(resolution.getComuna().getCode()).isEqualTo("05109");
        assertThat(resolution.getZone().getCode()).isEqualTo("LIDER_REGION_05");
    }

    @Test
    void flagsWhenTheResolvedComunaDiffersFromTheDeclaredOne() throws Exception {
        // Punto en Providencia (13123) declarado como Santiago (13101).
        GeoCoverageResolutionDto resolution = covered(request("Av. Providencia", "1234", "13101", -33.429, -70.612));

        assertThat(resolution.getComuna().getCode()).isEqualTo("13123");
        assertThat(resolution.getZone().getCode()).isEqualTo("SANTIAGO_ORIENTE");
        assertThat(resolution.getComunaDiffersFromDeclared()).isTrue();
    }

    @Test
    void ignoresStreetAndNumberForTheCoverageDecision() throws Exception {
        GeoCoverageResolutionDto first = covered(request("Calle A", "1", "13101", -33.4372, -70.645));
        GeoCoverageResolutionDto second = covered(request("Otra calle", "99999", "13101", -33.4372, -70.645));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejectsAnExcludedAreaAsNotCovered() throws Exception {
        assertNotCovered(request("Av. Costanera", "100", "04102", -30.26, -71.485), "EXCLUDED_AREA", "04102");
    }

    @Test
    void rejectsAnInactiveZoneAsNotCovered() throws Exception {
        assertNotCovered(request("Av. Pajaritos", "2000", "13119", -33.51, -70.757), "ZONE_INACTIVE", "13119");
    }

    @Test
    void rejectsADeclaredComunaWithoutZoneAsNotCovered() throws Exception {
        assertNotCovered(request("Calle de prueba", "100", "11101", null, null), "COMUNA_NOT_SERVED", "11101");
    }

    @Test
    void rejectsAPointOutsideEveryBoundaryAsOutsideServiceArea() throws Exception {
        ExchangeResult result = post(request("Monjitas", "879", "13101", -10.0, -100.0));

        ProblemDto problem = assertProblem(result, 422);
        assertThat(problem.getCode()).isEqualTo("ADDRESS_NOT_COVERED");
        JsonNode body = jsonMapper.readTree(result.getResponseBodyContent());
        assertThat(body.get("reason").asString()).isEqualTo("OUTSIDE_SERVICE_AREA");
        assertThat(body.has("comunaCode")).isFalse();
    }

    @Test
    void rejectsMissingBlankAndTooLongFieldsAsValidationErrors() throws Exception {
        Map<String, Object> missingStreet = request("x", "1", "13101", null, null);
        missingStreet.remove("street");
        List<Map<String, Object>> invalid = List.of(
                missingStreet,
                request("   ", "1", "13101", null, null),
                request("x", "", "13101", null, null),
                request("x".repeat(121), "1", "13101", null, null),
                request("x", "1".repeat(21), "13101", null, null),
                request("x", "1", "1310", null, null),
                request("x", "1", "abcde", null, null),
                request("x", "1", "13101", 90.5, -70.0),
                request("x", "1", "13101", -33.0, 180.5));
        for (Map<String, Object> body : invalid) {
            ProblemDto problem = assertProblem(post(body), 400);
            assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void rejectsAnUnknownComunaAsInvalidInput() throws Exception {
        ProblemDto problem = assertProblem(post(request("x", "1", "99999", null, null)), 400);

        assertThat(problem.getCode()).isEqualTo("UNKNOWN_COMUNA");
    }

    @Test
    void rejectsASingleCoordinateAsIncompleteCoordinates() throws Exception {
        Map<String, Object> onlyLatitude = request("x", "1", "13101", -33.4, null);
        Map<String, Object> onlyLongitude = request("x", "1", "13101", null, -70.6);

        assertThat(assertProblem(post(onlyLatitude), 400).getCode()).isEqualTo("INCOMPLETE_COORDINATES");
        assertThat(assertProblem(post(onlyLongitude), 400).getCode()).isEqualTo("INCOMPLETE_COORDINATES");
    }

    @Test
    void answersNotAcceptableAsProblemDetails() throws Exception {
        ExchangeResult result = client().post().uri("/api/v1/coverage/resolve")
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_XML)
                .body(request("x", "1", "13101", null, null))
                .exchange().returnResult();

        assertThat(assertProblem(result, 406).getCode()).isEqualTo("NOT_ACCEPTABLE");
    }

    @Test
    void listsTheNineDemoAddressesWithTheirExpectedCoverage() throws Exception {
        ExchangeResult result = client().get().uri("/api/v1/demo-addresses")
                .header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON).exchange().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        GeoDemoAddressDto[] addresses = jsonMapper.readValue(result.getResponseBodyContent(), GeoDemoAddressDto[].class);

        assertThat(addresses).hasSize(9);
        assertThat(addresses).extracting(GeoDemoAddressDto::getId).first().isEqualTo("la-serena-monjitas");
        GeoDemoAddressDto serena = addresses[0];
        assertThat(serena.getLabel()).isEqualTo("Monjitas 2334, La Serena");
        assertThat(serena.getComuna().getCode()).isEqualTo("04101");
        assertThat(serena.getLatitude()).isEqualTo(-29.9045);
        assertThat(serena.getExpected().getCovered()).isTrue();
        assertThat(serena.getExpected().getZoneCode()).isEqualTo("LA_SERENA_COQUIMBO");
        assertThat(serena.getExpected().getMethod().getValue()).isEqualTo("COMUNA_BOUNDARY");
        assertThat(serena.getExpected().getReason()).isNull();

        GeoDemoAddressDto noCoordinates = find(addresses, "vina-sin-cobertura");
        assertThat(noCoordinates.getLatitude()).isNull();
        assertThat(noCoordinates.getLongitude()).isNull();
        assertThat(noCoordinates.getLabel()).isEqualTo("Av. Libertad 100, Viña del Mar (cobertura por comuna)");
        assertThat(noCoordinates.getExpected().getCovered()).isTrue();
        assertThat(noCoordinates.getExpected().getReason()).isNull();
        assertThat(noCoordinates.getExpected().getZoneCode()).isEqualTo("LIDER_REGION_05");
        assertThat(noCoordinates.getExpected().getMethod().getValue()).isEqualTo("COMUNA_CODE");
    }

    @Test
    void demoAddressesAnswerNotAcceptableAsProblemDetails() throws Exception {
        ExchangeResult result = client().get().uri("/api/v1/demo-addresses")
                .header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_XML).exchange().returnResult();

        assertThat(assertProblem(result, 406).getCode()).isEqualTo("NOT_ACCEPTABLE");
    }

    private GeoDemoAddressDto find(GeoDemoAddressDto[] addresses, String id) {
        return List.of(addresses).stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow();
    }

    private GeoCoverageResolutionDto covered(Map<String, Object> body) throws Exception {
        ExchangeResult result = post(body);
        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        return jsonMapper.readValue(result.getResponseBodyContent(), GeoCoverageResolutionDto.class);
    }

    private void assertNotCovered(Map<String, Object> body, String reason, String comunaCode) throws Exception {
        ExchangeResult result = post(body);

        ProblemDto problem = assertProblem(result, 422);
        assertThat(problem.getCode()).isEqualTo("ADDRESS_NOT_COVERED");
        JsonNode json = jsonMapper.readTree(result.getResponseBodyContent());
        assertThat(json.get("reason").asString()).isEqualTo(reason);
        assertThat(json.get("comunaCode").asString()).isEqualTo(comunaCode);
    }

    private ProblemDto assertProblem(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        ProblemDto problem = jsonMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(problem.getCorrelationId()).isEqualTo(CORRELATION_ID);
        return problem;
    }

    private ExchangeResult post(Map<String, Object> body) {
        return client().post().uri("/api/v1/coverage/resolve")
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(body)
                .exchange().returnResult();
    }

    private static Map<String, Object> request(String street, String number, String comunaCode, Double latitude,
                                               Double longitude) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("street", street);
        body.put("number", number);
        body.put("comunaCode", comunaCode);
        if (latitude != null) {
            body.put("latitude", latitude);
        }
        if (longitude != null) {
            body.put("longitude", longitude);
        }
        return body;
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
