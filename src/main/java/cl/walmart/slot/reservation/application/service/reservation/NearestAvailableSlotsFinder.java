package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.service.reservation.AlternativeSlotsFinder;
import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dos consultas de un día al catálogo (misma fecha y siguiente), nunca un rango mayor. El estado de cierre del
 * catálogo usa el reloj de la aplicación; aquí se descarta además todo lo que la base ya considera cerrado.
 */
@Service
class NearestAvailableSlotsFinder implements AlternativeSlotsFinder {

    private final SlotQueryPort catalog;
    private final int alternativesMax;

    NearestAvailableSlotsFinder(SlotQueryPort catalog, ReservationSettings settings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.alternativesMax = Objects.requireNonNull(settings, "settings must not be null").alternativesMax();
    }

    @Override
    public List<AlternativeSlot> find(SlotView requested, Instant dbNow) {
        Objects.requireNonNull(requested, "requested must not be null");
        Objects.requireNonNull(dbNow, "dbNow must not be null");
        if (alternativesMax == 0) {
            return List.of();
        }
        var sameDay = candidates(requested, requested.deliveryDate(), dbNow).stream()
                .sorted(Comparator.comparing((SlotView s) -> distance(s, requested))
                        .thenComparing(SlotView::startAt))
                .toList();
        var nextDay = candidates(requested, requested.deliveryDate().plusDays(1), dbNow).stream()
                .sorted(Comparator.comparing(SlotView::startAt))
                .toList();
        var all = new ArrayList<SlotView>(sameDay);
        all.addAll(nextDay);
        return all.stream().limit(alternativesMax).map(NearestAvailableSlotsFinder::toAlternative).toList();
    }

    private List<SlotView> candidates(SlotView requested, java.time.LocalDate day, Instant dbNow) {
        UUID requestedId = requested.slotId();
        return catalog.findAvailableSlots(requested.zoneId(), day, day).stream()
                .filter(s -> !s.slotId().equals(requestedId))
                .filter(s -> s.status() == SlotView.Status.AVAILABLE && s.remainingCapacity() > 0)
                .filter(s -> s.cutoffAt().isAfter(dbNow))
                .toList();
    }

    private static Duration distance(SlotView candidate, SlotView requested) {
        return Duration.between(requested.startAt(), candidate.startAt()).abs();
    }

    private static AlternativeSlot toAlternative(SlotView s) {
        return new AlternativeSlot(new SlotId(s.slotId()), s.deliveryDate(), s.startAt(), s.endAt(), s.priceClp(),
                s.remainingCapacity());
    }
}
