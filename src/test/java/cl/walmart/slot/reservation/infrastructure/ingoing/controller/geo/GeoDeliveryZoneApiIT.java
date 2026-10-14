package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDetailDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP de las zonas de despacho y su geometría (requisitos 1.6, 4.7, 7.1 a 7.7, 10.1, 10.2),
 * contra PostGIS real con los datos semilla.
 *
 * <p>El servidor corre en otro hilo, así que la zona sin límites se inserta con commit y se elimina al
 * terminar cada test, usando valores fuera del seed (región 99).
 */
@IntegrationTest
class GeoDeliveryZoneApiIT {

    private static final String CORRELATION_ID = "geo-zones-it";

    private static final String LA_SERENA_ID = "5f1c0e2a-0000-4000-8000-000000000401";
    private static final String CHICUREO_ID = "5f1c0e2a-0000-4000-8000-000000001303";
    private static final String MAIPU_ID = "5f1c0e2a-0000-4000-8000-000000001304";
    private static final String NO_BOUNDARY_ID = "00000000-0000-4000-8000-0000000000e1";
    private static final String UNKNOWN_ID = "00000000-0000-4000-8000-0000000000ff";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertZoneWithoutBoundaries() {
        cleanUp();
        jdbc.update("insert into geo_region (code, name, north_to_south_order) values ('99', 'Región de prueba', 101)");
        jdbc.update("insert into geo_province (code, region_code, name) values ('991', '99', 'Provincia de prueba')");
        jdbc.update("insert into geo_comuna (code, province_code, name) values ('99101', '991', 'Comuna sin límite')");
        jdbc.update("insert into geo_dispatch_node (code, name) values ('CD-ZTEST', 'CD de prueba')");
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, 'ZTEST_NO_BOUNDARY', 'Zona sin límites', 'ACTIVE', 'CD-ZTEST')", NO_BOUNDARY_ID);
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99101', ?::uuid)",
                NO_BOUNDARY_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from geo_delivery_zone_comuna where comuna_code = '99101'");
        jdbc.update("delete from geo_delivery_zone where code = 'ZTEST_NO_BOUNDARY'");
        jdbc.update("delete from geo_dispatch_node where code = 'CD-ZTEST'");
        jdbc.update("delete from geo_comuna where code = '99101'");
        jdbc.update("delete from geo_province where code = '991'");
        jdbc.update("delete from geo_region where code = '99'");
    }

    @Test
    void listsAllZonesOrderedByCodeWithNodeAndComunas() throws Exception {
        ExchangeResult result = get("/api/v1/zones");

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        List<GeoDeliveryZoneDto> zones = List.of(
                jsonMapper.readValue(result.getResponseBodyContent(), GeoDeliveryZoneDto[].class));

        assertThat(zones).extracting(GeoDeliveryZoneDto::getCode).isSorted().contains(
                "CHICUREO", "LA_SERENA_COQUIMBO", "MAIPU", "SANTIAGO_CENTRO", "SANTIAGO_ORIENTE",
                "ZTEST_NO_BOUNDARY");

        GeoDeliveryZoneDto laSerena = find(zones, "LA_SERENA_COQUIMBO");
        assertThat(laSerena.getId().toString()).isEqualTo(LA_SERENA_ID);
        assertThat(laSerena.getName()).isEqualTo("La Serena-Coquimbo");
        assertThat(laSerena.getStatus().getValue()).isEqualTo("ACTIVE");
        assertThat(laSerena.getDispatchNode().getCode()).isEqualTo("CD-LSR");
        assertThat(laSerena.getDispatchNode().getName()).isEqualTo("CD La Serena");
        assertThat(laSerena.getComunas()).extracting(c -> c.getCode()).containsExactly("04101", "04102");
        assertThat(laSerena.getComunas()).extracting(c -> c.getName()).containsExactly("La Serena", "Coquimbo");

        assertThat(find(zones, "CHICUREO").getComunas()).isEmpty();
        assertThat(find(zones, "SANTIAGO_CENTRO").getComunas()).hasSize(4);
        assertThat(find(zones, "MAIPU").getStatus().getValue()).isEqualTo("INACTIVE");
    }

    @Test
    void filtersTheListingByStatus() throws Exception {
        List<GeoDeliveryZoneDto> active = list("/api/v1/zones?status=ACTIVE");
        List<GeoDeliveryZoneDto> inactive = list("/api/v1/zones?status=INACTIVE");

        assertThat(active).isNotEmpty().allSatisfy(z -> assertThat(z.getStatus().getValue()).isEqualTo("ACTIVE"));
        assertThat(active).extracting(GeoDeliveryZoneDto::getCode)
                .isSorted().contains("CHICUREO", "LA_SERENA_COQUIMBO").doesNotContain("MAIPU");
        assertThat(inactive).isNotEmpty().allSatisfy(z -> assertThat(z.getStatus().getValue()).isEqualTo("INACTIVE"));
        assertThat(inactive).extracting(GeoDeliveryZoneDto::getCode).contains("MAIPU").doesNotContain("CHICUREO");
    }

    @Test
    void rejectsAnInvalidStatusAsAValidationError() throws Exception {
        ProblemDto problem = assertProblem(get("/api/v1/zones?status=PAUSED"), 400);

        assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void returnsTheDetailWithAreaCountsAndNoGeometryByDefault() throws Exception {
        ExchangeResult result = get("/api/v1/zones/" + LA_SERENA_ID);

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        GeoDeliveryZoneDetailDto detail = jsonMapper.readValue(
                result.getResponseBodyContent(), GeoDeliveryZoneDetailDto.class);
        assertThat(detail.getCode()).isEqualTo("LA_SERENA_COQUIMBO");
        assertThat(detail.getComunas()).hasSize(2);
        assertThat(detail.getInclusionAreaCount()).isZero();
        assertThat(detail.getExclusionAreaCount()).isEqualTo(1);
        assertThat(detail.getGeometry()).isNull();

        GeoDeliveryZoneDetailDto chicureo = jsonMapper.readValue(
                get("/api/v1/zones/" + CHICUREO_ID + "?includeGeometry=false").getResponseBodyContent(),
                GeoDeliveryZoneDetailDto.class);
        assertThat(chicureo.getInclusionAreaCount()).isEqualTo(1);
        assertThat(chicureo.getExclusionAreaCount()).isZero();
        assertThat(chicureo.getGeometry()).isNull();
    }

    @Test
    void includesAValidMultiPolygonWhenRequested() throws Exception {
        ExchangeResult result = get("/api/v1/zones/" + LA_SERENA_ID + "?includeGeometry=true");

        assertThat(result.getStatus().value()).isEqualTo(200);
        JsonNode geometry = jsonMapper.readTree(result.getResponseBodyContent()).get("geometry");
        assertThat(geometry).isNotNull();
        assertThat(geometry.get("type").asString()).isEqualTo("MultiPolygon");
        assertThat(geometry.get("coordinates").isArray()).isTrue();
        assertThat(geometry.get("coordinates").size()).isPositive();

        GeoDeliveryZoneDetailDto detail = jsonMapper.readValue(
                result.getResponseBodyContent(), GeoDeliveryZoneDetailDto.class);
        assertThat(detail.getGeometry().getType().getValue()).isEqualTo("MultiPolygon");
        assertThat(detail.getGeometry().getCoordinates()).isNotEmpty();

        // El GeoJSON publicado es una geometría válida de PostGIS y cubre el centro de La Serena.
        String geoJson = jsonMapper.writeValueAsString(geometry);
        assertThat(jdbc.queryForObject(
                "select ST_IsValid(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))", Boolean.class, geoJson)).isTrue();
        assertThat(jdbc.queryForObject(
                "select ST_Covers(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ST_SetSRID(ST_MakePoint(-71.25, -29.90), 4326))",
                Boolean.class, geoJson)).isTrue();
    }

    @Test
    void includesTheGeometryOfAZoneDefinedOnlyByAnInclusionArea() throws Exception {
        ExchangeResult result = get("/api/v1/zones/" + CHICUREO_ID + "?includeGeometry=true");

        JsonNode geometry = jsonMapper.readTree(result.getResponseBodyContent()).get("geometry");
        assertThat(geometry.get("type").asString()).isEqualTo("MultiPolygon");
        assertThat(jdbc.queryForObject(
                "select ST_Covers(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ST_SetSRID(ST_MakePoint(-70.65, -33.285), 4326))",
                Boolean.class, jsonMapper.writeValueAsString(geometry))).isTrue();
    }

    @Test
    void omitsTheGeometryOfAZoneWithoutBoundariesWithoutFailing() throws Exception {
        ExchangeResult result = get("/api/v1/zones/" + NO_BOUNDARY_ID + "?includeGeometry=true");

        assertThat(result.getStatus().value()).isEqualTo(200);
        JsonNode body = jsonMapper.readTree(result.getResponseBodyContent());
        assertThat(body.get("code").asString()).isEqualTo("ZTEST_NO_BOUNDARY");
        assertThat(body.get("comunas")).hasSize(1);
        assertThat(body.get("inclusionAreaCount").asInt()).isZero();
        assertThat(body.get("exclusionAreaCount").asInt()).isZero();
        assertThat(body.get("geometry") == null || body.get("geometry").isNull()).isTrue();
    }

    @Test
    void returnsTheInactiveZoneDetail() throws Exception {
        GeoDeliveryZoneDetailDto detail = jsonMapper.readValue(
                get("/api/v1/zones/" + MAIPU_ID).getResponseBodyContent(), GeoDeliveryZoneDetailDto.class);

        assertThat(detail.getStatus().getValue()).isEqualTo("INACTIVE");
        assertThat(detail.getDispatchNode().getCode()).isEqualTo("CD-STGO");
    }

    @Test
    void rejectsAnInvalidUuidAsAValidationError() throws Exception {
        ProblemDto problem = assertProblem(get("/api/v1/zones/not-a-uuid"), 400);

        assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsAnInvalidIncludeGeometryAsAValidationError() throws Exception {
        ProblemDto problem = assertProblem(get("/api/v1/zones/" + LA_SERENA_ID + "?includeGeometry=maybe"), 400);

        assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void answersZoneNotFoundAsProblemDetails() throws Exception {
        ProblemDto problem = assertProblem(get("/api/v1/zones/" + UNKNOWN_ID), 404);

        assertThat(problem.getCode()).isEqualTo("ZONE_NOT_FOUND");
    }

    @Test
    void answersNotAcceptableAsProblemDetails() throws Exception {
        for (String uri : List.of("/api/v1/zones", "/api/v1/zones/" + LA_SERENA_ID)) {
            ExchangeResult result = client().get().uri(uri)
                    .header("X-Correlation-Id", CORRELATION_ID)
                    .accept(MediaType.APPLICATION_XML).exchange().returnResult();

            assertThat(assertProblem(result, 406).getCode()).isEqualTo("NOT_ACCEPTABLE");
        }
    }

    @Test
    void exposesNoWriteOperationsOnZones() {
        for (String uri : List.of("/api/v1/zones", "/api/v1/zones/" + LA_SERENA_ID)) {
            for (var method : List.of(
                    org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                    org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE)) {
                ExchangeResult result = client().method(method).uri(uri)
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}")
                        .exchange().returnResult();

                assertThat(result.getStatus().value()).as("%s %s", method, uri).isEqualTo(405);
            }
        }
    }

    private GeoDeliveryZoneDto find(List<GeoDeliveryZoneDto> zones, String code) {
        return zones.stream().filter(z -> z.getCode().equals(code)).findFirst().orElseThrow();
    }

    private List<GeoDeliveryZoneDto> list(String uri) throws Exception {
        ExchangeResult result = get(uri);
        assertThat(result.getStatus().value()).isEqualTo(200);
        return List.of(jsonMapper.readValue(result.getResponseBodyContent(), GeoDeliveryZoneDto[].class));
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
        return client().get().uri(uri)
                .header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .exchange().returnResult();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
