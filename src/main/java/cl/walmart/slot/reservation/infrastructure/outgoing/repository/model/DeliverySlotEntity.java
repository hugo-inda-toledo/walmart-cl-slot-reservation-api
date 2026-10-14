package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Fila de {@code catalog_delivery_slot}; solo lectura. Los contadores {@code reserved}/{@code held} los modifica
 * únicamente {@code slot-reservation} con SQL condicional, nunca esta entidad.
 */
@Entity
@Immutable
@Table(name = "catalog_delivery_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverySlotEntity {

    @Id
    private UUID id;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "cutoff_at")
    private Instant cutoffAt;

    @Column(name = "price_clp")
    private long priceClp;

    private int capacity;

    @Column(insertable = false, updatable = false)
    private int reserved;

    @Column(insertable = false, updatable = false)
    private int held;

    @Column(insertable = false, updatable = false)
    private long version;
}
