package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.cmd.config.properties.AppProperties;
import cl.walmart.slot.reservation.cmd.config.CorrelationIdFilter;
import cl.walmart.slot.reservation.cmd.exception.DefaultProblemDetailFactory;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ReservationProblemAdviceTest {

    private static final String CORRELATION_ID = "advice-test-correlation";

    private final Logger adviceLogger = (Logger) LoggerFactory.getLogger(ReservationProblemAdvice.class);
    private ListAppender<ILoggingEvent> logs;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        adviceLogger.addAppender(logs);
        adviceLogger.setLevel(Level.INFO);
        var properties = new AppProperties(
                new AppProperties.Cors(List.of(), List.of(CorrelationIdFilter.HEADER)),
                new AppProperties.Problem(URI.create("urn:slot-reservation:problem:")),
                new AppProperties.CorrelationId(64));
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new ReservationProblemAdvice(new DefaultProblemDetailFactory(properties)))
                .build();
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        adviceLogger.detachAppender(logs);
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void respondeConflictoConRetryAfterProblemJsonYCorrelationId() throws Exception {
        mockMvc.perform(get("/fixture/in-progress")
                        .requestAttr(CorrelationIdFilter.REQUEST_ATTRIBUTE, CORRELATION_ID))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_IN_PROGRESS"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/fixture/in-progress"));
    }

    @Test
    void registraInfoConElFormatoDelManejadorGlobalSinClienteNiOrden() throws Exception {
        mockMvc.perform(get("/fixture/in-progress")).andExpect(status().isConflict());

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Domain error: code=IDEMPOTENCY_REQUEST_IN_PROGRESS, category=CONFLICT");
            assertThat(event.getMDCPropertyMap()).containsEntry(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);
            assertThat(event.getMDCPropertyMap()).doesNotContainKeys("customerId", "orderId");
        });
    }

    @Test
    void noManejaOtrasExcepcionesDeDominio() throws Exception {
        // Sin GlobalExceptionHandler registrado, el advice no debe capturar ReservationException.
        try {
            mockMvc.perform(get("/fixture/other-domain"));
        } catch (Exception expected) {
            assertThat(expected.getCause()).isInstanceOf(ReservationException.class);
        }
        assertThat(logs.list).isEmpty();
    }

    @RestController
    static class FixtureController {

        @GetMapping("/fixture/in-progress")
        String inProgress() {
            throw new IdempotencyInProgressException(3);
        }

        @GetMapping("/fixture/other-domain")
        String otherDomain() {
            throw ReservationException.idempotencyKeyReused();
        }
    }
}
