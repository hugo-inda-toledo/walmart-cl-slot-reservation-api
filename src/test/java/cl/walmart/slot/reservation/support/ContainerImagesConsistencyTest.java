package cl.walmart.slot.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ContainerImagesConsistencyTest {

    private static final Pattern IMAGE = Pattern.compile("^\\s{4}image:\\s*(\\S+)\\s*$");

    @Test
    void usesTheSamePostgisAndValkeyImagesAsLocalCompose() throws IOException {
        var compose = Files.readAllLines(Path.of("docker-compose.yml"));

        assertThat(imageForService(compose, "postgres")).isEqualTo(ContainerImages.POSTGIS);
        assertThat(imageForService(compose, "valkey")).isEqualTo(ContainerImages.VALKEY);
    }

    private String imageForService(Iterable<String> lines, String service) {
        boolean inService = false;
        for (String line : lines) {
            if (line.equals("  " + service + ":")) {
                inService = true;
                continue;
            }
            if (inService && line.startsWith("  ") && !line.startsWith("    ")) {
                break;
            }
            if (inService) {
                Matcher matcher = IMAGE.matcher(line);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
        }
        throw new IllegalArgumentException("No image configured for service " + service);
    }
}
