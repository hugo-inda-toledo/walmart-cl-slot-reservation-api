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
 * Reglas del seam del contexto {@code catalog}: el único punto de contacto con otros contextos (reservas) es el
 * paquete publicado {@code domain.port.catalog}; ese paquete no expone tipos internos; y solo el paquete de
 * caché de catalog conoce el cliente clave-valor (la corrección de la capacidad no depende de la caché).
 */
@AnalyzeClasses(packages = "cl.walmart.slot.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class CatalogBoundaryRulesTest {

    private static final String CATALOG = "catalog";
    private static final String PORT_LAYER = "domain.port";
    private static final String CACHE_LAYER = "infrastructure.outgoing.cache";

    @ArchTest
    static final ArchRule otherContextsOnlyUseThePublishedCatalogPorts = noClasses()
            .that(not(inContext(CATALOG))).and(not(inCompositionRoot()))
            .should().dependOnClassesThat(catalogInternalType())
            .because("fuera de catalog solo se puede usar el paquete de puertos publicado (domain.port.catalog)");

    @ArchTest
    static final ArchRule publishedCatalogPortsDoNotExposeCatalogInternals = noClasses()
            .that(inContextLayer(CATALOG, PORT_LAYER))
            .should().dependOnClassesThat(catalogInternalType())
            .because("los puertos publicados de catalog solo emplean tipos del JDK y del propio paquete en sus firmas");

    @ArchTest
    static final ArchRule onlyTheCatalogCachePackageUsesTheKeyValueClient = noClasses()
            .that(inContext(CATALOG)).and(not(inContextLayer(CATALOG, CACHE_LAYER)))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..", "io.lettuce..",
                    "redis.clients..")
            .because("la caché es solo un acelerador: únicamente el adaptador de caché de catalog habla con Valkey");

    private static DescribedPredicate<JavaClass> catalogInternalType() {
        return new DescribedPredicate<>("an internal type of catalog (outside the published port package)") {
            @Override
            public boolean test(JavaClass dependency) {
                String packageName = dependency.getPackageName();
                return packageName.startsWith(BASE_PACKAGE + ".")
                        && inContext(CATALOG).test(dependency)
                        && !isInContextLayer(packageName, CATALOG, PORT_LAYER);
            }
        };
    }
}
