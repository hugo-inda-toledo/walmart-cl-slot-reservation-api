package cl.walmart.slot.reservation.integration.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GeoSeedResourcesTest {

    private static final long MAX_GEO_DATA_BYTES = 2L * 1024 * 1024;
    private static final Path GEO_MIGRATIONS = Path.of("src/main/resources/db/migration/geo");
    private static final Path GEO_SCRIPTS = Path.of("scripts/geo");

    @Test
    void keepsVersionedGeoDataBelowTwoMegabytes() {
        long totalBytes = sizeOf(GEO_MIGRATIONS) + sizeOf(GEO_SCRIPTS);

        assertThat(totalBytes).isPositive().isLessThan(MAX_GEO_DATA_BYTES);
    }

    @Test
    void providesTheGeoMigrationsInOrder() {
        assertThat(fileNames(GEO_MIGRATIONS)).startsWith(
                "V2_1__create_geo_schema.sql",
                "V2_2__seed_dpa.sql",
                "V2_3__seed_comuna_boundaries.sql",
                "V2_4__seed_delivery_zones.sql",
                "V2_5__seed_demo_addresses.sql");
    }

    private static long sizeOf(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(GeoSeedResourcesTest::size).sum();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<String> fileNames(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
