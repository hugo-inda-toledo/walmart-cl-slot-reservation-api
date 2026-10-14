package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de {@code catalog_holiday}; solo lectura.
 */
@Entity
@Table(name = "catalog_holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayEntity {

    @Id
    private UUID id;

    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "zone_id")
    private UUID zoneId;
}
