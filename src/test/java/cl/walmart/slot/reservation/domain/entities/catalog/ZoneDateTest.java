package cl.walmart.slot.reservation.domain.entities.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneDateTest {

    private static final CatalogZoneId ZONE = new CatalogZoneId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    @Test
    void rejectsNullComponents() {
        assertThatNullPointerException().isThrownBy(() -> new ZoneDate(null, DATE));
        assertThatNullPointerException().isThrownBy(() -> new ZoneDate(ZONE, null));
    }

    @Test
    void hasValueEqualityUsableInSets() {
        assertThat(Set.of(new ZoneDate(ZONE, DATE))).contains(new ZoneDate(ZONE, DATE));
        assertThat(new ZoneDate(ZONE, DATE)).isNotEqualTo(new ZoneDate(ZONE, DATE.plusDays(1)));
    }
}
