package cl.walmart.slot.reservation.cmd.config.properties;

import cl.walmart.slot.reservation.cmd.config.GeoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeoPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GeoConfiguration.class);

    @Test
    void defaultsTheBorderToleranceTo300Meters() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GeoProperties.class).borderToleranceMeters()).isEqualTo(300);
            assertThat(context.getBean(BorderTolerance.class)).isEqualTo(new BorderTolerance(300));
        });
    }

    @Test
    void bindsAnOverrideIntoTheDomainBean() {
        contextRunner.withPropertyValues("app.geo.border-tolerance-meters=1200").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BorderTolerance.class).meters()).isEqualTo(1200);
        });
    }

    @Test
    void acceptsTheRangeLimits() {
        contextRunner.withPropertyValues("app.geo.border-tolerance-meters=0").run(context ->
                assertThat(context.getBean(BorderTolerance.class).meters()).isZero());
        contextRunner.withPropertyValues("app.geo.border-tolerance-meters=5000").run(context ->
                assertThat(context.getBean(BorderTolerance.class).meters()).isEqualTo(5000));
    }

    @Test
    void rejectsValuesOutOfRange() {
        contextRunner.withPropertyValues("app.geo.border-tolerance-meters=-1")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("app.geo.border-tolerance-meters=5001")
                .run(context -> assertThat(context).hasFailed());
    }
}
