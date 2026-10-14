package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba que cada regla de {@link GeoBoundaryRulesTest} falla ante su contraejemplo, identificando la clase y la
 * dependencia que violan el seam.
 */
class GeoBoundaryRulesFixturesTest {

    private static final String FIXTURES_PACKAGE = "architecturefixtures";

    @Test
    void identifiesAnotherContextDependingOnAGeoInternalType() {
        assertViolation(GeoBoundaryRulesTest.otherContextsOnlyUseThePublishedGeoPort,
                "architecturefixtures.application.service.catalog.CatalogDependingOnGeoInternals",
                "cl.walmart.slot.reservation.domain.entities.geo.ComunaCode");
    }

    @Test
    void identifiesAPortClassExposingAGeoDomainType() {
        assertViolation(GeoBoundaryRulesTest.publishedGeoPortDoesNotExposeGeoInternals,
                "architecturefixtures.domain.port.geo.PortDependingOnGeoDomain",
                "cl.walmart.slot.reservation.domain.entities.geo.ComunaCode");
    }

    @Test
    void identifiesGeoDependingOnTheKeyValueClient() {
        assertViolation(GeoBoundaryRulesTest.geoDoesNotUseTheKeyValueClient,
                "architecturefixtures.infrastructure.outgoing.repository.geo.RedisDependentGeoAdapter",
                "org.springframework.data.redis.core.StringRedisTemplate");
    }

    private static void assertViolation(ArchRule rule, String className, String dependencyName) {
        assertThatThrownBy(() -> rule.check(new ClassFileImporter().importPackages(FIXTURES_PACKAGE)))
                .isInstanceOf(AssertionError.class)
                .satisfies(error -> assertThat(error).hasMessageContaining(className).hasMessageContaining(dependencyName));
    }
}
