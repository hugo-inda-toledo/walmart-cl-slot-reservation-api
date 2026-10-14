package cl.walmart.slot.reservation.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DeliverySlotEntity;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

class CatalogCounterOwnershipTest {
    @Test
    void deliverySlotEntityIsImmutableAndProtectsReservationCountersFromJpaWrites() throws Exception {
        assertThat(DeliverySlotEntity.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertProtected("reserved");
        assertProtected("held");
    }

    @Test
    void catalogSqlNeverAssignsCountersOrIncludesThemInSlotInserts() throws Exception {
        String sources = Files.walk(Path.of("src/main"))
                .filter(path -> path.toString().contains("catalog"))
                .filter(Files::isRegularFile)
                .map(path -> { try { return Files.readString(path).toLowerCase(); } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); } })
                .reduce("", String::concat).replaceAll("--[^\\n]*", "");
        assertThat(sources).doesNotContainPattern("(?s)update\\s+catalog_delivery_slot\\s+set[^;]*(?:\\bset|,)\\s*reserved\\s*=");
        assertThat(sources).doesNotContainPattern("(?s)update\\s+catalog_delivery_slot\\s+set[^;]*(?:\\bset|,)\\s*held\\s*=");
        assertThat("update catalog_delivery_slot set capacity = 1, reserved = 1")
                .containsPattern("(?:\\bset|,)\\s*reserved\\s*=");
        assertThat(sources).doesNotContainPattern("insert\\s+into\\s+catalog_delivery_slot\\s*\\([^)]*(reserved|held)");
    }
    private static void assertProtected(String name) throws Exception {
        Field field = DeliverySlotEntity.class.getDeclaredField(name);
        var column = field.getAnnotation(jakarta.persistence.Column.class);
        assertThat(column.insertable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }
}
