package cl.walmart.slot.reservation.cmd.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class TimeAndJacksonConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TimeConfig.class, JacksonConfig.class, JacksonAutoConfiguration.class);

    @Test
    void exposesTheSantiagoBusinessClock() {
        contextRunner.run(context -> assertThat(context.getBean(Clock.class).getZone())
                .isEqualTo(ZoneId.of("America/Santiago")));
    }

    @Test
    void serializesTemporalValuesAsIso8601WithoutEpochNumbers() {
        contextRunner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            assertThat(mapper.writeValueAsString(Instant.parse("2026-10-11T19:00:00Z")))
                    .isEqualTo("\"2026-10-11T19:00:00Z\"");
            assertThat(mapper.writeValueAsString(OffsetDateTime.parse("2026-10-11T16:00:00-03:00")))
                    .isEqualTo("\"2026-10-11T16:00:00-03:00\"");
            assertThat(mapper.writeValueAsString(LocalDate.parse("2026-10-11")))
                    .isEqualTo("\"2026-10-11\"");
        });
    }

    @Test
    void retainsAnOffsetDateTimesOwnOffsetDuringSerialization() {
        contextRunner.run(context -> assertThat(context.getBean(JsonMapper.class)
                .writeValueAsString(OffsetDateTime.parse("2026-10-11T16:00:00-03:00")))
                .isEqualTo("\"2026-10-11T16:00:00-03:00\""));
    }

    @Test
    void activatesTheJacksonCustomizerAndIgnoresUnknownProperties() {
        contextRunner.run(context -> {
            assertThat(context.getBean("jacksonDateTimeCustomizer", JsonMapperBuilderCustomizer.class)).isNotNull();

            assertThat(context.getBean(JsonMapper.class)
                    .readValue("{\"known\":\"value\",\"unknown\":true}", Sample.class))
                    .isEqualTo(new Sample("value"));
        });
    }

    @Test
    void derivesSantiagoOffsetsForSummerAndWinterInstants() {
        contextRunner.run(context -> {
            ZoneId santiago = context.getBean(Clock.class).getZone();

            assertThat(OffsetDateTime.ofInstant(Instant.parse("2026-01-15T12:00:00Z"), santiago).getOffset().getId())
                    .isEqualTo("-03:00");
            assertThat(OffsetDateTime.ofInstant(Instant.parse("2026-06-15T12:00:00Z"), santiago).getOffset().getId())
                    .isEqualTo("-04:00");
        });
    }

    private record Sample(String known) {
    }
}
