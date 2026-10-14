package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryZoneTest {

    private static final DeliveryZoneId ID = new DeliveryZoneId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final DispatchNode NODE = new DispatchNode("CD_QUILICURA", "CD Quilicura");

    private static Comuna comuna(String code) {
        ComunaCode comunaCode = new ComunaCode(code);
        return new Comuna(comunaCode, "Comuna " + code, comunaCode.provinceCode(), "Provincia", true);
    }

    @Test
    void buildsWithOnlyComunas() {
        var zone = new DeliveryZone(ID, "RM_NORTE", "Santiago Norte", ZoneStatus.ACTIVE, NODE,
                List.of(comuna("13101")), 0, 0);

        assertThat(zone.code()).isEqualTo("RM_NORTE");
        assertThat(zone.comunas()).hasSize(1);
        assertThat(zone.isActive()).isTrue();
    }

    @Test
    void buildsWithOnlyInclusionArea() {
        var zone = new DeliveryZone(ID, "RM_SUR", "Santiago Sur", ZoneStatus.INACTIVE, NODE, List.of(), 1, 2);

        assertThat(zone.isActive()).isFalse();
        assertThat(zone.inclusionAreaCount()).isEqualTo(1);
        assertThat(zone.exclusionAreaCount()).isEqualTo(2);
    }

    @Test
    void rejectsZoneWithoutComunasNorInclusionAreas() {
        assertThatThrownBy(() -> new DeliveryZone(ID, "RM_VACIA", "Vacía", ZoneStatus.ACTIVE, NODE, List.of(), 0, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidCode() {
        for (String code : List.of("a", "rm_norte", "1ABC", "X", "A".repeat(41), "RM-NORTE")) {
            assertThatThrownBy(() -> new DeliveryZone(ID, code, "n", ZoneStatus.ACTIVE, NODE, List.of(), 1, 0))
                    .as(code)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsBoundaryCodeLengths() {
        new DeliveryZone(ID, "AB", "n", ZoneStatus.ACTIVE, NODE, List.of(), 1, 0);
        new DeliveryZone(ID, "A" + "1".repeat(39), "n", ZoneStatus.ACTIVE, NODE, List.of(), 1, 0);
    }

    @Test
    void rejectsNegativeAreaCounts() {
        assertThatThrownBy(() -> new DeliveryZone(ID, "RM_NORTE", "n", ZoneStatus.ACTIVE, NODE, List.of(), -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryZone(ID, "RM_NORTE", "n", ZoneStatus.ACTIVE, NODE,
                List.of(comuna("13101")), 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comunasListIsImmutableAndDefensivelyCopied() {
        var source = new ArrayList<>(List.of(comuna("13101")));
        var zone = new DeliveryZone(ID, "RM_NORTE", "n", ZoneStatus.ACTIVE, NODE, source, 0, 0);

        source.add(comuna("13102"));

        assertThat(zone.comunas()).hasSize(1);
        assertThatThrownBy(() -> zone.comunas().add(comuna("13103")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
