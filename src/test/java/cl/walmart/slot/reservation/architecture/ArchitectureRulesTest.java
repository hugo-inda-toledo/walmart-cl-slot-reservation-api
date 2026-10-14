package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cl.walmart.slot.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule layersAreRespected = classes().should()
            .resideInAnyPackage("..cmd..", "..domain..", "..application..", "..infrastructure..")
            .orShould().beAnnotatedWith(SpringBootApplication.class)
            .because("solo la clase de arranque puede vivir fuera de cmd y de las capas hexagonales");

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "tools.jackson..", "com.fasterxml..", "org.hibernate..", "io.lettuce..",
                    "..application..", "..infrastructure..", "..cmd..")
            .because("el dominio debe permanecer independiente de frameworks y capas superiores");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..cmd..")
            .allowEmptyShould(true)
            .because("la aplicación no puede depender de adaptadores de infraestructura ni de la configuración (cmd)");

    @ArchTest
    static final ArchRule persistenceModelStaysInInfrastructure = noClasses().that()
            .resideInAnyPackage("..domain..", "..application..", "..infrastructure.ingoing..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.outgoing.repository.model..")
            .allowEmptyShould(true)
            .because("las clases de tabla son detalle de persistencia y no pueden filtrarse al dominio, a los casos de uso ni a la API");

    @ArchTest
    static final ArchRule entitiesLiveInPersistenceModelPackage = classes().that()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("..infrastructure.outgoing.repository.model..")
            .allowEmptyShould(true)
            .because("toda clase de tabla debe vivir junta en repository.model");

    @ArchTest
    static final ArchRule controllersDependOnUseCaseInterfacesOnly = noClasses().that().areAnnotatedWith(Controller.class)
            .or().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(prohibitedControllerDependency())
            .allowEmptyShould(true)
            .because("los controllers dependen solo de interfaces de casos de uso");

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule generatedApisAreImplemented = classes().that().areInterfaces()
            .and().resideInAPackage("..openapi.api..").and().haveSimpleNameEndingWith("Api")
            .should(haveRestControllerImplementation()).allowEmptyShould(true)
            .because("cada interfaz OpenAPI generada debe tener un controller que la implemente");

    @ArchTest
    static final ArchRule noDirectSystemClockAccess = noClasses().should().callMethod(Instant.class, "now", new Class<?>[0])
            .orShould().callMethod(LocalDate.class, "now", new Class<?>[0])
            .orShould().callMethod(LocalDateTime.class, "now", new Class<?>[0])
            .orShould().callMethod(OffsetDateTime.class, "now", new Class<?>[0])
            .orShould().callMethod(ZonedDateTime.class, "now", new Class<?>[0])
            .because("la hora actual debe provenir de un Clock inyectable");

    private static DescribedPredicate<JavaClass> prohibitedControllerDependency() {
        return new DescribedPredicate<>("a concrete implementation or repository") {
            @Override
            public boolean test(JavaClass dependency) {
                return dependency.getSimpleName().endsWith("Impl")
                        || dependency.isAssignableTo(org.springframework.data.repository.Repository.class)
                        || (dependency.isInterface()
                        && dependency.getPackageName().contains(".domain")
                        && dependency.getSimpleName().endsWith("Repository"));
            }
        };
    }

    private static ArchCondition<JavaClass> haveRestControllerImplementation() {
        return new ArchCondition<>("have an implementation annotated with @RestController") {
            @Override
            public void check(JavaClass api, ConditionEvents events) {
                boolean implementedByController = api.getAllSubclasses().stream()
                        .anyMatch(implementation -> implementation.isAnnotatedWith(RestController.class));
                String message = "%s does not have an implementation annotated with @RestController".formatted(api.getName());
                events.add(new SimpleConditionEvent(api, implementedByController, message));
            }
        };
    }

}
