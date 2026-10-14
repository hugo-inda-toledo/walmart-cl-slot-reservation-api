package cl.walmart.slot.reservation.cmd.config;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.cmd.config.properties.AppProperties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final int MAX_LENGTH = 64;

    @Test
    void reusesAValidIncomingCorrelationIdAndMakesItAvailableDuringTheRequest() throws Exception {
        var request = requestWithHeader("delivery.123_A-b");
        var response = new MockHttpServletResponse();
        var chainCorrelationId = new AtomicReference<String>();

        filter().doFilter(request, response, (servletRequest, servletResponse) ->
                chainCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(chainCorrelationId).hasValue("delivery.123_A-b");
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo("delivery.123_A-b");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("delivery.123_A-b");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidCorrelationIds")
    void generatesAUuidForAnInvalidIncomingCorrelationId(String incomingCorrelationId) throws Exception {
        var request = requestWithHeader(incomingCorrelationId);
        var response = new MockHttpServletResponse();

        filter().doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank().satisfies(id -> {
            UUID.fromString(id);
            assertThat(id).isNotEqualTo(incomingCorrelationId);
        });
    }

    @Test
    void alwaysAddsTheResponseHeaderWhenTheRequestHasNoIncomingCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter().doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    private static java.util.stream.Stream<String> invalidCorrelationIds() {
        return java.util.stream.Stream.of("", "a".repeat(MAX_LENGTH + 1), "invalid/id");
    }

    private static MockHttpServletRequest requestWithHeader(String correlationId) {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        return request;
    }

    private static CorrelationIdFilter filter() {
        return new CorrelationIdFilter(new AppProperties(
                new AppProperties.Cors(java.util.List.of(), java.util.List.of(CorrelationIdFilter.HEADER)),
                new AppProperties.Problem(java.net.URI.create("urn:test:")),
                new AppProperties.CorrelationId(MAX_LENGTH)));
    }
}
