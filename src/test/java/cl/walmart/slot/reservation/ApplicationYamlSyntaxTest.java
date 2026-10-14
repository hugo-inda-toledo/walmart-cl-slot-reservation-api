package cl.walmart.slot.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ApplicationYamlSyntaxTest {

    @Test
    void parsesTheProblemTypeBaseUriAsAScalar() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/application.yml")) {
            Map<?, ?> root = new Yaml().load(stream);
            Map<?, ?> app = (Map<?, ?>) root.get("app");
            Map<?, ?> problem = (Map<?, ?>) app.get("problem");

            assertThat(problem.get("type-base-uri")).isEqualTo("urn:slot-reservation:problem:");
        }
    }
}
