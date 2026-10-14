package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.inContext;
import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.inContextLayer;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura del contexto {@code reservation}: ninguna clase habla con el cliente clave-valor (la
 * capacidad vive solo en PostgreSQL), el modelo de dominio no conoce a catalog y el cliente JDBC queda confinado a
 * la persistencia de reservas. Los contratos de {@code domain.{service,usecase,port}} sí pueden usar el puerto
 * publicado de catalog (lo vigila {@link CatalogBoundaryRulesTest}).
 */
@AnalyzeClasses(packages = "cl.walmart.slot.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class ReservationBoundaryRulesTest {

    private static final String RESERVATION = "reservation";
    private static final String[] DOMAIN_MODEL_LAYERS = {
            "domain.entities", "domain.enums", "domain.exception", "domain.repository"};
    private static final String PERSISTENCE_LAYER = "infrastructure.outgoing.repository";

    @ArchTest
    static final ArchRule reservationDoesNotUseTheKeyValueClient = noClasses()
            .that(inContext(RESERVATION))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..", "io.lettuce..",
                    "redis.clients..")
            .because("la capacidad se decide solo en PostgreSQL: ninguna clase de reservation depende de Valkey");

    @ArchTest
    static final ArchRule reservationDomainDoesNotDependOnCatalog = noClasses()
            .that(inReservationDomainModel())
            .should().dependOnClassesThat(inContext("catalog"))
            .because("el modelo de dominio de reservation es autónomo: consume catalog solo mediante puertos publicados");

    @ArchTest
    static final ArchRule onlyReservationPersistenceUsesTheJdbcClient = noClasses()
            .that(inContext(RESERVATION)).and(not(inContextLayer(RESERVATION, PERSISTENCE_LAYER)))
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
            .because("el SQL de reservas vive solo en la persistencia de reservation (infrastructure.outgoing.repository.reservation)");

    private static DescribedPredicate<JavaClass> inReservationDomainModel() {
        DescribedPredicate<JavaClass> predicate = inContextLayer(RESERVATION, DOMAIN_MODEL_LAYERS[0]);
        for (int i = 1; i < DOMAIN_MODEL_LAYERS.length; i++) {
            predicate = predicate.or(inContextLayer(RESERVATION, DOMAIN_MODEL_LAYERS[i]));
        }
        return predicate;
    }
}
