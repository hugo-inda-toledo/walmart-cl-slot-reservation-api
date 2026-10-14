package cl.walmart.slot.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MigrationBaselineTest {

    @Test
    void providesThePostgisFoundationMigration() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/foundation/V1_1__enable_postgis.sql")) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("CREATE EXTENSION IF NOT EXISTS postgis");
        }
    }
}
