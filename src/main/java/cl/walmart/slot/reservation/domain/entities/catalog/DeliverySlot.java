package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Ventana de despacho materializada, con sus contadores. El estado se calcula siempre contra un instante
 * explícito (nunca se almacena), de modo que el cierre no queda desfasado.
 */
public final class DeliverySlot {

    private final DeliverySlotId id;
    private final CatalogZoneId zoneId;
    private final LocalDate deliveryDate;
    private final Instant startAt;
    private final Instant endAt;
    private final Instant cutoffAt;
    private final Money basePrice;
    private final int capacity;
    private final int reserved;
    private final int held;

    /**
     * @throws IllegalStateException si no se cumple {@code 0 <= reserved}, {@code 0 <= held} y
     *                               {@code reserved + held <= capacity}; la base de datos lo impide, por lo que
     *                               una violación es un defecto
     */
    public DeliverySlot(DeliverySlotId id, CatalogZoneId zoneId, LocalDate deliveryDate, Instant startAt,
                        Instant endAt, Instant cutoffAt, Money basePrice, int capacity, int reserved, int held) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.deliveryDate = Objects.requireNonNull(deliveryDate, "deliveryDate must not be null");
        this.startAt = Objects.requireNonNull(startAt, "startAt must not be null");
        this.endAt = Objects.requireNonNull(endAt, "endAt must not be null");
        this.cutoffAt = Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice must not be null");
        if (capacity < 0 || reserved < 0 || held < 0 || (long) reserved + held > capacity) {
            throw new IllegalStateException("Invariante de capacidad violada: capacity=" + capacity
                    + ", reserved=" + reserved + ", held=" + held);
        }
        this.capacity = capacity;
        this.reserved = reserved;
        this.held = held;
    }

    public DeliverySlotId id() {
        return id;
    }

    public CatalogZoneId zoneId() {
        return zoneId;
    }

    public LocalDate deliveryDate() {
        return deliveryDate;
    }

    public Instant startAt() {
        return startAt;
    }

    public Instant endAt() {
        return endAt;
    }

    public Instant cutoffAt() {
        return cutoffAt;
    }

    public Money basePrice() {
        return basePrice;
    }

    public int capacity() {
        return capacity;
    }

    public int reserved() {
        return reserved;
    }

    public int held() {
        return held;
    }

    /** Cupos restantes; nunca negativo. */
    public int remaining() {
        return Math.max(0, capacity - reserved - held);
    }

    /** La ventana ya inició si {@code now >= startAt}. */
    public boolean hasStartedAt(Instant now) {
        return !now.isBefore(startAt);
    }

    /** El cierre se alcanzó si {@code now >= cutoffAt}. */
    public boolean isCutoffPassedAt(Instant now) {
        return !now.isBefore(cutoffAt);
    }

    /** Precedencia: cierre alcanzado, luego agotado, luego disponible. */
    public SlotStatus statusAt(Instant now) {
        if (isCutoffPassedAt(now)) {
            return SlotStatus.CUTOFF_PASSED;
        }
        return remaining() == 0 ? SlotStatus.SOLD_OUT : SlotStatus.AVAILABLE;
    }
}
