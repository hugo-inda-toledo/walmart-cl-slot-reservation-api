package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.exception.catalog.CatalogException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Período consultable: hoy (fecha de Santiago) y los {@code days - 1} días siguientes.
 */
public record DisplayPeriod(LocalDate today, int days) {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 14;

    public DisplayPeriod {
        Objects.requireNonNull(today, "today must not be null");
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and 14: " + days);
        }
    }

    public static DisplayPeriod from(Clock clock, int days) {
        return new DisplayPeriod(CatalogTime.today(clock), days);
    }

    public List<LocalDate> dates() {
        return IntStream.range(0, days).mapToObj(today::plusDays).toList();
    }

    public LocalDate lastDate() {
        return today.plusDays(days - 1L);
    }

    /** @throws CatalogException {@code DELIVERY_DATE_OUT_OF_RANGE} si la fecha no pertenece al período */
    public void requireContains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (date.isBefore(today) || date.isAfter(lastDate())) {
            throw CatalogException.deliveryDateOutOfRange(date, today, lastDate());
        }
    }
}
