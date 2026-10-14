package cl.walmart.slot.reservation.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ubica una clase por contexto y capa usando los segmentos del paquete <em>relativos</em> a la raíz del código
 * ({@code cl.walmart.slot.reservation} o {@code architecturefixtures}). Es necesario porque el paquete base
 * contiene el segmento {@code reservation}: un patrón {@code ..reservation..} calzaría con todas las clases.
 */
final class ArchitecturePackages {

    static final String BASE_PACKAGE = "cl.walmart.slot.reservation";
    private static final List<String> ROOTS = List.of(BASE_PACKAGE, "architecturefixtures");

    private ArchitecturePackages() {
    }

    /** Raíz de composición ({@code cmd}): configuración que cablea todos los contextos. */
    static DescribedPredicate<JavaClass> inCompositionRoot() {
        return new DescribedPredicate<>("in the composition root (cmd)") {
            @Override
            public boolean test(JavaClass javaClass) {
                List<String> segments = relativeSegments(javaClass.getPackageName());
                return !segments.isEmpty() && segments.get(0).equals("cmd");
            }
        };
    }

    /** Clases del contexto {@code context}, en cualquier capa. */
    static DescribedPredicate<JavaClass> inContext(String context) {
        return new DescribedPredicate<>("in context " + context) {
            @Override
            public boolean test(JavaClass javaClass) {
                return relativeSegments(javaClass.getPackageName()).contains(context);
            }
        };
    }

    /**
     * Clases del contexto {@code context} cuya ruta de capa (el paquete relativo sin el segmento del contexto)
     * empieza por {@code layerPath}, por ejemplo {@code domain} o {@code infrastructure.outgoing.repository}.
     */
    static DescribedPredicate<JavaClass> inContextLayer(String context, String layerPath) {
        return new DescribedPredicate<>("in context " + context + " layer " + layerPath) {
            @Override
            public boolean test(JavaClass javaClass) {
                return isInContextLayer(javaClass.getPackageName(), context, layerPath);
            }
        };
    }

    static boolean isInContextLayer(String packageName, String context, String layerPath) {
        List<String> segments = new ArrayList<>(relativeSegments(packageName));
        if (!segments.remove(context)) {
            return false;
        }
        List<String> layer = Arrays.asList(layerPath.split("\\."));
        return segments.size() >= layer.size() && segments.subList(0, layer.size()).equals(layer);
    }

    private static List<String> relativeSegments(String packageName) {
        for (String root : ROOTS) {
            if (packageName.equals(root)) {
                return List.of();
            }
            if (packageName.startsWith(root + ".")) {
                return Arrays.asList(packageName.substring(root.length() + 1).split("\\."));
            }
        }
        return List.of();
    }
}
