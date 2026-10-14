package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba que cada regla de {@link CatalogBoundaryRulesTest} falla ante su contraejemplo, identificando la clase y la
 * dependencia que violan el seam.
 */
class CatalogBoundaryRulesFixturesTest {

    private static final String FIXTURES_PACKAGE = "architecturefixtures";

    @Test
    void identifiesAReservationConsumerUsingACatalogDomainType() {
        assertViolation(CatalogBoundaryRulesTest.otherContextsOnlyUseThePublishedCatalogPorts,
                "architecturefixtures.application.service.reservation.ReservationUsingCatalogDomain",
                "cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId");
    }

    @Test
    void identifiesAPortClassExposingACatalogDomainType() {
        assertViolation(CatalogBoundaryRulesTest.publishedCatalogPortsDoNotExposeCatalogInternals,
                "architecturefixtures.domain.port.catalog.PortDependingOnCatalogDomain",
                "cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId");
    }

    @Test
    void identifiesACatalogApplicationClassUsingTheKeyValueClient() {
        assertViolation(CatalogBoundaryRulesTest.onlyTheCatalogCachePackageUsesTheKeyValueClient,
                "architecturefixtures.application.service.catalog.CatalogApplicationUsingKeyValueClient",
                "org.springframework.data.redis.core.StringRedisTemplate");
    }

    private static void assertViolation(ArchRule rule, String className, String dependencyName) {
        assertThatThrownBy(() -> rule.check(new ClassFileImporter().importPackages(FIXTURES_PACKAGE)))
                .isInstanceOf(AssertionError.class)
                .satisfies(error -> assertThat(error).hasMessageContaining(className).hasMessageContaining(dependencyName));
    }
}
