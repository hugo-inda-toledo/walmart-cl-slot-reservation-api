package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.BASE_PACKAGE;
import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.inCompositionRoot;
import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.inContext;
import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.inContextLayer;
import static cl.walmart.slot.reservation.architecture.ArchitecturePackages.isInContextLayer;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas del seam del contexto {@code geo}: el único punto de contacto con otros contextos es el puerto
 * publicado {@code domain.port.geo}, y geo no depende del cliente clave-valor (la corrección de la
 * cobertura no puede depender de la caché).
 */
@AnalyzeClasses(packages = "cl.walmart.slot.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class GeoBoundaryRulesTest {

    private static final String GEO = "geo";
    private static final String PORT_LAYER = "domain.port";

    @ArchTest
    static final ArchRule otherContextsOnlyUseThePublishedGeoPort = noClasses().that(not(inContext(GEO))).and(not(inCompositionRoot()))
            .should().dependOnClassesThat(geoInternalType())
            .because("fuera de geo solo se puede usar el paquete del puerto publicado (domain.port.geo)");

    @ArchTest
    static final ArchRule publishedGeoPortDoesNotExposeGeoInternals = noClasses()
            .that(inContextLayer(GEO, PORT_LAYER))
            .should().dependOnClassesThat(geoInternalType())
            .because("el puerto publicado de geo solo emplea tipos del JDK en sus firmas");

    @ArchTest
    static final ArchRule geoDoesNotUseTheKeyValueClient = noClasses().that(inContext(GEO))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..", "io.lettuce..",
                    "redis.clients..")
            .because("la cobertura se resuelve solo con PostgreSQL; la caché no participa en geo");

    private static DescribedPredicate<JavaClass> geoInternalType() {
        return new DescribedPredicate<>("an internal type of geo (outside the published port)") {
            @Override
            public boolean test(JavaClass dependency) {
                String packageName = dependency.getPackageName();
                return packageName.startsWith(BASE_PACKAGE + ".")
                        && inContext(GEO).test(dependency)
                        && !isInContextLayer(packageName, GEO, PORT_LAYER);
            }
        };
    }
}
