package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba que cada regla de {@link ReservationBoundaryRulesTest} falla ante su contraejemplo, y que el contraejemplo
 * de catalog sigue fallando con la regla de catalog.
 */
class ReservationBoundaryRulesFixturesTest {

    private static final String FIXTURES_PACKAGE = "architecturefixtures";

    @Test
    void identifiesAReservationClassUsingTheKeyValueClient() {
        assertViolation(ReservationBoundaryRulesTest.reservationDoesNotUseTheKeyValueClient,
                "architecturefixtures.application.service.reservation.ReservationUsingKeyValueClient",
                "org.springframework.data.redis.core.StringRedisTemplate");
    }

    @Test
    void identifiesAReservationDomainClassDependingOnCatalog() {
        assertViolation(ReservationBoundaryRulesTest.reservationDomainDoesNotDependOnCatalog,
                "architecturefixtures.domain.entities.reservation.ReservationDomainUsingCatalogDomain",
                "cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId");
    }

    @Test
    void identifiesAReservationClassUsingTheJdbcClientOutsidePersistence() {
        assertViolation(ReservationBoundaryRulesTest.onlyReservationPersistenceUsesTheJdbcClient,
                "architecturefixtures.application.service.reservation.ReservationApplicationUsingJdbcClient",
                "org.springframework.jdbc.core.simple.JdbcClient");
    }

    @Test
    void catalogDomainCounterexampleStillFailsTheCatalogRule() {
        assertViolation(CatalogBoundaryRulesTest.otherContextsOnlyUseThePublishedCatalogPorts,
                "architecturefixtures.application.service.reservation.ReservationUsingCatalogDomain",
                "cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId");
    }

    private static void assertViolation(ArchRule rule, String className, String dependencyName) {
        assertThatThrownBy(() -> rule.check(new ClassFileImporter().importPackages(FIXTURES_PACKAGE)))
                .isInstanceOf(AssertionError.class)
                .satisfies(error -> assertThat(error).hasMessageContaining(className).hasMessageContaining(dependencyName));
    }
}
