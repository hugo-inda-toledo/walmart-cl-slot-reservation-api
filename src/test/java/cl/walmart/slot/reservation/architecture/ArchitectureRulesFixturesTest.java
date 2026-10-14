package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureRulesFixturesTest {

    private static final String FIXTURES_PACKAGE = "architecturefixtures";

    @Test
    void layersRuleIdentifiesClassOutsideHexagonalLayers() {
        assertViolation(ArchitectureRulesTest.layersAreRespected, "architecturefixtures.OutsideLayerFixture");
    }

    @Test
    void domainRuleIdentifiesFrameworkDependency() {
        assertViolation(ArchitectureRulesTest.domainIsFrameworkFree,
                "architecturefixtures.domain.FrameworkDependentDomain", "org.springframework.stereotype.Component");
    }

    @Test
    void applicationRuleIdentifiesInfrastructureDependency() {
        assertViolation(ArchitectureRulesTest.applicationDoesNotDependOnInfrastructure,
                "architecturefixtures.application.InfrastructureDependentApplication", "architecturefixtures.infrastructure.InfrastructureAdapter");
    }

    @Test
    void applicationRuleIdentifiesCompositionRootDependency() {
        assertViolation(ArchitectureRulesTest.applicationDoesNotDependOnInfrastructure,
                "architecturefixtures.application.CmdDependentApplication", "architecturefixtures.cmd.config.FixtureProperties");
    }

    @Test
    void controllerRuleIdentifiesConcreteImplementationDependency() {
        assertViolation(ArchitectureRulesTest.controllersDependOnUseCaseInterfacesOnly,
                "architecturefixtures.infrastructure.web.ControllerDependingOnImpl", "architecturefixtures.application.ExampleUseCaseImpl");
    }

    @Test
    void controllerRuleIdentifiesSpringDataRepositoryDependency() {
        assertViolation(ArchitectureRulesTest.controllersDependOnUseCaseInterfacesOnly,
                "architecturefixtures.infrastructure.web.ControllerDependingOnSpringDataRepository",
                "architecturefixtures.infrastructure.SpringDataFixtureRepository");
    }

    @Test
    void controllerRuleIdentifiesDomainRepositoryDependency() {
        assertViolation(ArchitectureRulesTest.controllersDependOnUseCaseInterfacesOnly,
                "architecturefixtures.infrastructure.web.ControllerDependingOnDomainRepository",
                "architecturefixtures.domain.FixtureRepository");
    }

    @Test
    void fieldInjectionRuleIdentifiesInjectedField() {
        assertViolation(ArchitectureRulesTest.noFieldInjection, "architecturefixtures.infrastructure.FieldInjectedComponent");
    }

    @Test
    void persistenceModelRuleIdentifiesApplicationDependingOnTableClass() {
        assertViolation(ArchitectureRulesTest.persistenceModelStaysInInfrastructure,
                "architecturefixtures.application.PersistenceModelDependentApplication",
                "architecturefixtures.infrastructure.outgoing.repository.model.FixtureTableModel");
    }

    @Test
    void entityLocationRuleIdentifiesEntityOutsideModelPackage() {
        assertViolation(ArchitectureRulesTest.entitiesLiveInPersistenceModelPackage,
                "architecturefixtures.infrastructure.outgoing.repository.MisplacedEntity");
    }

    @Test
    void generatedApiRuleIdentifiesApiWithoutControllerImplementation() {
        assertViolation(ArchitectureRulesTest.generatedApisAreImplemented,
                "architecturefixtures.infrastructure.openapi.api.UnimplementedApi");
    }

    @Test
    void clockRuleIdentifiesDirectSystemClockAccess() {
        assertViolation(ArchitectureRulesTest.noDirectSystemClockAccess,
                "architecturefixtures.infrastructure.DirectSystemClockAccess");
    }

    @Test
    void clockRuleAllowsClockInjectedIntoNow() {
        assertNoViolation(ArchitectureRulesTest.noDirectSystemClockAccess,
                architecturefixtures.infrastructure.ClockInjectedTimeAccess.class);
    }

    private static void assertViolation(com.tngtech.archunit.lang.ArchRule rule, String className) {
        assertViolation(rule, className, null);
    }

    private static void assertViolation(com.tngtech.archunit.lang.ArchRule rule, String className, String dependencyName) {
        assertThatThrownBy(() -> rule.check(new ClassFileImporter().importPackages(FIXTURES_PACKAGE)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(className)
                .satisfies(error -> {
                    if (dependencyName != null) {
                        org.assertj.core.api.Assertions.assertThat(error).hasMessageContaining(dependencyName);
                    }
                });
    }

    private static void assertNoViolation(com.tngtech.archunit.lang.ArchRule rule, Class<?> fixtureClass) {
        rule.check(new ClassFileImporter().importClasses(fixtureClass));
    }
}
