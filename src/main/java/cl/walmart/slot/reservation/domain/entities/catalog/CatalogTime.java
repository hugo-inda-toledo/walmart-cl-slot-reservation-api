package cl.walmart.slot.reservation.domain.entities.catalog;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Hora de negocio del catálogo: la fecha de "hoy" siempre es la de Santiago, sin importar la zona del reloj.
 */
public final class CatalogTime {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    private CatalogTime() {
    }

    public static LocalDate today(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
    }
}
