package cl.walmart.slot.reservation.application.service.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NearestAvailableSlotsFinderTest {

    private static final UUID ZONE = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static final Instant NINE = Instant.parse("2026-10-20T12:00:00Z");
    private static final Instant DB_NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final FakeSlotQueryPort catalog = new FakeSlotQueryPort();

    private NearestAvailableSlotsFinder finder(int max) {
        return new NearestAvailableSlotsFinder(catalog, settings(max));
    }

    @Test
    @DisplayName("mismo día por cercanía de inicio, luego por inicio, y después el día siguiente por inicio")
    void ordersSameDayByProximityThenNextDayByStart() {
        var requested = slot(DAY, 0, 4);
        var far = slot(DAY, 6, 9);
        var nearAfter = slot(DAY, 1, 3);
        var nearBefore = slot(DAY, -1, 3);
        var nextLate = slot(DAY.plusDays(1), 5, 3);
        var nextEarly = slot(DAY.plusDays(1), 1, 3);
        catalog.add(requested, far, nearAfter, nearBefore, nextLate, nextEarly);

        var result = finder(10).find(requested, DB_NOW);

        // empate de distancia (1h antes y 1h después): gana el de inicio más temprano
        assertThat(result).extracting(a -> a.slotId().value()).containsExactly(
                nearBefore.slotId(), nearAfter.slotId(), far.slotId(), nextEarly.slotId(), nextLate.slotId());
    }

    @Test
    @DisplayName("excluye la ventana solicitada")
    void excludesRequestedSlot() {
        var requested = slot(DAY, 0, 4);
        var other = slot(DAY, 1, 2);
        catalog.add(requested, other);

        assertThat(finder(3).find(requested, DB_NOW)).extracting(a -> a.slotId().value())
                .containsExactly(other.slotId());
    }

    @Test
    @DisplayName("corta en el tope configurado")
    void capsAtConfiguredMax() {
        var requested = slot(DAY, 0, 4);
        catalog.add(requested, slot(DAY, 1, 1), slot(DAY, 2, 1), slot(DAY.plusDays(1), 1, 1),
                slot(DAY.plusDays(1), 2, 1));

        assertThat(finder(2).find(requested, DB_NOW)).hasSize(2);
    }

    @Test
    @DisplayName("tope cero desactiva la búsqueda y no consulta el catálogo")
    void zeroMaxDisablesSearch() {
        var requested = slot(DAY, 0, 4);
        catalog.add(slot(DAY, 1, 1));

        assertThat(finder(0).find(requested, DB_NOW)).isEmpty();
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    @DisplayName("sin candidatas devuelve lista vacía")
    void emptyWhenNoCandidates() {
        var requested = slot(DAY, 0, 4);
        catalog.add(requested);

        assertThat(finder(3).find(requested, DB_NOW)).isEmpty();
    }

    @Test
    @DisplayName("descarta candidatas con cierre <= dbNow aunque el catálogo las considere disponibles")
    void discardsCandidatesClosedAccordingToDbTime() {
        var requested = slot(DAY, 0, 4);
        var closedAtDbNow = withCutoff(slot(DAY, 1, 2), DB_NOW);
        var closedBefore = withCutoff(slot(DAY, 2, 2), DB_NOW.minusSeconds(1));
        var open = withCutoff(slot(DAY, 3, 2), DB_NOW.plusSeconds(1));
        catalog.add(requested, closedAtDbNow, closedBefore, open);

        assertThat(finder(3).find(requested, DB_NOW)).extracting(a -> a.slotId().value())
                .containsExactly(open.slotId());
    }

    @Test
    @DisplayName("solo pide rangos de un día: la misma fecha y la siguiente")
    void neverRequestsMoreThanOneDay() {
        var requested = slot(DAY, 0, 4);
        catalog.add(requested);

        finder(3).find(requested, DB_NOW);

        assertThat(catalog.calls).containsExactly(
                new Call(ZONE, DAY, DAY), new Call(ZONE, DAY.plusDays(1), DAY.plusDays(1)));
    }

    @Test
    @DisplayName("mapea la candidata a AlternativeSlot con capacidad restante y precio")
    void mapsFields() {
        var requested = slot(DAY, 0, 4);
        var other = slot(DAY, 1, 7);
        catalog.add(other);

        var result = finder(3).find(requested, DB_NOW);

        assertThat(result).containsExactly(new AlternativeSlot(
                new cl.walmart.slot.reservation.domain.entities.reservation.SlotId(other.slotId()), DAY, other.startAt(),
                other.endAt(), other.priceClp(), 7));
    }

    private static ReservationSettings settings(int alternativesMax) {
        return new ReservationSettings(Duration.ofMinutes(10), alternativesMax, Duration.ofHours(24),
                Duration.ofSeconds(5), 1, 100, 10, 100);
    }

    private static SlotView slot(LocalDate day, int hourOffset, int remaining) {
        Instant start = NINE.plus(Duration.ofDays(day.toEpochDay() - DAY.toEpochDay()))
                .plus(Duration.ofHours(hourOffset));
        return new SlotView(UUID.randomUUID(), ZONE, day, start, start.plus(Duration.ofHours(1)),
                start.minus(Duration.ofHours(2)), 2990, 10, 10 - remaining, 0, remaining,
                SlotView.Status.AVAILABLE);
    }

    private static SlotView withCutoff(SlotView v, Instant cutoff) {
        return new SlotView(v.slotId(), v.zoneId(), v.deliveryDate(), v.startAt(), v.endAt(), cutoff, v.priceClp(),
                v.capacity(), v.reserved(), v.held(), v.remainingCapacity(), v.status());
    }

    private record Call(UUID zone, LocalDate from, LocalDate to) { }

    private static final class FakeSlotQueryPort implements SlotQueryPort {
        final List<SlotView> slots = new ArrayList<>();
        final List<Call> calls = new ArrayList<>();

        void add(SlotView... views) {
            slots.addAll(List.of(views));
        }

        @Override
        public Optional<SlotView> findSlot(UUID slotId) {
            return slots.stream().filter(s -> s.slotId().equals(slotId)).findFirst();
        }

        @Override
        public List<SlotView> findAvailableSlots(UUID zoneId, LocalDate from, LocalDate to) {
            calls.add(new Call(zoneId, from, to));
            return slots.stream().filter(s -> s.zoneId().equals(zoneId) && !s.deliveryDate().isBefore(from)
                    && !s.deliveryDate().isAfter(to)).toList();
        }
    }
}
