package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.port.geo.ZoneResolution;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionPort;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionQuery;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoCoverageResolutionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDemoAddressDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verificación extremo a extremo de las nueve direcciones demo (requisitos 8.1 a 8.4 y 9.1): cada una, resuelta
 * por HTTP y por el puerto publicado, produce exactamente su resultado esperado (zona, método o motivo).
 */
@IntegrationTest
class GeoDemoAddressesEndToEndIT {

    private static final List<String> DEMO_ADDRESS_IDS = List.of(
            "la-serena-monjitas", "santiago-monjitas", "providencia-av", "chicureo", "la-serena-borde",
            "coquimbo-sin-coordenadas", "vina-sin-cobertura", "maipu-inactiva", "tongoy-excluida");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ZoneResolutionPort zoneResolutionPort;

    static Stream<String> demoAddressIds() {
        return DEMO_ADDRESS_IDS.stream();
    }

    @Test
    void theEndpointReturnsExactlyTheNineDemoAddresses() throws Exception {
        assertThat(demoAddresses()).extracting(GeoDemoAddressDto::getId).containsExactlyElementsOf(DEMO_ADDRESS_IDS);
    }

    @ParameterizedTest(name = "{0} por HTTP")
    @MethodSource("demoAddressIds")
    void resolvesEachDemoAddressOverHttpToItsExpectedResult(String id) throws Exception {
        GeoDemoAddressDto address = demoAddress(id);

        ExchangeResult result = client().post().uri("/api/v1/coverage/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(requestFor(address))
                .exchange().returnResult();

        if (address.getExpected().getCovered()) {
            assertThat(result.getStatus().value()).isEqualTo(200);
            GeoCoverageResolutionDto resolution =
                    jsonMapper.readValue(result.getResponseBodyContent(), GeoCoverageResolutionDto.class);
            assertThat(resolution.getZone().getCode()).isEqualTo(address.getExpected().getZoneCode());
            assertThat(resolution.getMethod().getValue()).isEqualTo(address.getExpected().getMethod().getValue());
        } else {
            assertThat(result.getStatus().value()).isEqualTo(422);
            JsonNode problem = jsonMapper.readTree(result.getResponseBodyContent());
            assertThat(problem.get("code").asString()).isEqualTo("ADDRESS_NOT_COVERED");
            assertThat(problem.get("reason").asString()).isEqualTo(address.getExpected().getReason().getValue());
        }
    }

    @ParameterizedTest(name = "{0} por el puerto")
    @MethodSource("demoAddressIds")
    void resolvesEachDemoAddressThroughThePublishedPortToItsExpectedResult(String id) throws Exception {
        GeoDemoAddressDto address = demoAddress(id);

        ZoneResolution resolution = zoneResolutionPort.resolveZone(new ZoneResolutionQuery(
                address.getComuna().getCode(), address.getLatitude(), address.getLongitude()));

        if (address.getExpected().getCovered()) {
            ZoneResolution.Covered covered = assertThat(resolution).isInstanceOf(ZoneResolution.Covered.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ZoneResolution.Covered.class))
                    .actual();
            assertThat(covered.zoneCode()).isEqualTo(address.getExpected().getZoneCode());
            assertThat(covered.method().name()).isEqualTo(address.getExpected().getMethod().getValue());
        } else {
            ZoneResolution.NotCovered notCovered = assertThat(resolution).isInstanceOf(ZoneResolution.NotCovered.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ZoneResolution.NotCovered.class))
                    .actual();
            assertThat(notCovered.reason().name()).isEqualTo(address.getExpected().getReason().getValue());
        }
    }

    private GeoDemoAddressDto demoAddress(String id) throws Exception {
        return demoAddresses().stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow();
    }

    private List<GeoDemoAddressDto> demoAddresses() throws Exception {
        ExchangeResult result = client().get().uri("/api/v1/demo-addresses")
                .accept(MediaType.APPLICATION_JSON).exchange().returnResult();
        assertThat(result.getStatus().value()).isEqualTo(200);
        return List.of(jsonMapper.readValue(result.getResponseBodyContent(), GeoDemoAddressDto[].class));
    }

    private static Map<String, Object> requestFor(GeoDemoAddressDto address) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("street", address.getStreet());
        body.put("number", address.getNumber());
        body.put("comunaCode", address.getComuna().getCode());
        if (address.getLatitude() != null) {
            body.put("latitude", address.getLatitude());
            body.put("longitude", address.getLongitude());
        }
        return body;
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
