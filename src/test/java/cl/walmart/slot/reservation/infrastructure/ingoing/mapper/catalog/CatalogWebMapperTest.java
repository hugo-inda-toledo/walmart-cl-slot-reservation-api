package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotsView;
import cl.walmart.slot.reservation.domain.entities.catalog.PricedSlotView;
import cl.walmart.slot.reservation.domain.entities.catalog.ResolvedZone;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.enums.catalog.DeliveryDateStatus;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.catalog.CatalogWebMapper;
import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.catalog.CatalogWebMapperImpl;
import org.junit.jupiter.api.Test;

class CatalogWebMapperTest {
    private final CatalogWebMapper mapper = new CatalogWebMapperImpl();

    @Test
    void mapsAViewToContractDtosWithSantiagoOffsetAndIntegralPrice() {
        var zone = new ResolvedZone(new CatalogZoneId(UUID.randomUUID()), "LA_SERENA", "La Serena", "04101", "La Serena");
        var date = new DeliveryDateView(LocalDate.of(2030, 1, 2), "Mié", DeliveryDateStatus.AVAILABLE);
        var slot = new PricedSlotView(new DeliverySlotId(UUID.randomUUID()), Instant.parse("2030-01-02T19:00:00Z"),
                Instant.parse("2030-01-02T21:00:00Z"), new Money(2990), 4, SlotStatus.AVAILABLE);

        var dto = mapper.toDto(new DeliverySlotsView(zone, date, List.of(slot)));

        assertThat(dto.getZone().getId()).isEqualTo(zone.zoneId().value());
        assertThat(dto.getComuna().getCode()).isEqualTo("04101");
        assertThat(dto.getLabel()).isEqualTo("Mié");
        assertThat(dto.getSlots()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getStart().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
            assertThat(mapped.getPrice()).isEqualTo(2990L);
            assertThat(mapped.getRemainingCapacity()).isEqualTo(4);
        });
    }
}
