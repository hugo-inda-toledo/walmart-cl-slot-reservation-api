package cl.walmart.slot.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

class DockerCompatibilitySmokeTest {

    private static final String MINIMUM_API_VERSION = "1.44";

    @Test
    void requiresDockerEngineApiAtLeast144() {
        try {
            String apiVersion = DockerClientFactory.instance().client().versionCmd().exec().getApiVersion();

            assertThat(isCompatibleApiVersion(apiVersion))
                    .withFailMessage("Docker Engine API %s incompatible: se requiere ≥ %s", apiVersion, MINIMUM_API_VERSION)
                    .isTrue();
        } catch (Exception exception) {
            fail("Docker Engine API no disponible o incompatible: se requiere ≥ " + MINIMUM_API_VERSION, exception);
        }
    }

    @Test
    void comparesDockerApiVersionsNumerically() {
        assertThat(isCompatibleApiVersion("1.9")).isFalse();
        assertThat(isCompatibleApiVersion("1.44")).isTrue();
        assertThat(isCompatibleApiVersion("1.53")).isTrue();
    }

    private static boolean isCompatibleApiVersion(String apiVersion) {
        String[] actual = apiVersion.split("\\.");
        String[] minimum = MINIMUM_API_VERSION.split("\\.");
        for (int index = 0; index < Math.max(actual.length, minimum.length); index++) {
            int actualSegment = index < actual.length ? Integer.parseInt(actual[index]) : 0;
            int minimumSegment = index < minimum.length ? Integer.parseInt(minimum[index]) : 0;
            if (actualSegment != minimumSegment) {
                return actualSegment > minimumSegment;
            }
        }
        return true;
    }
}
