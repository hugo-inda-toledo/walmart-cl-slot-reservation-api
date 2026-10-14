package cl.walmart.slot.reservation.application.helper.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.util.Objects;

/** Mapeo único de agregados del dominio a las vistas de salida de la capa de aplicación. */
public final class ReservationViews {

    private ReservationViews() {
    }

    public static ReservationView of(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        return new ReservationView(reservation.id().value(), reservation.holdId().value(),
                reservation.orderId().value(), reservation.status(), reservation.confirmedAt(),
                reservation.cancelledAt(), of(reservation.slot()));
    }

    /** {@code status} es el estado informado (puede diferir del persistido) y {@code reservationId} puede ser nulo. */
    public static HoldView of(SlotHold hold, HoldStatus status, ReservationId reservationId) {
        Objects.requireNonNull(hold, "hold must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return new HoldView(hold.id().value(), hold.orderId().value(), status, hold.createdAt(), hold.expiresAt(),
                of(hold.slot()), reservationId == null ? null : reservationId.value());
    }

    private static SlotSnapshotView of(SlotSnapshot s) {
        return new SlotSnapshotView(s.slotId().value(), s.zoneId(), s.deliveryDate(), s.startAt(), s.endAt(),
                s.priceClp());
    }
}
