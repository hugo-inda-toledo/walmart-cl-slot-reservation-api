package cl.walmart.slot.reservation.domain.repository.catalog;

import cl.walmart.slot.reservation.domain.service.catalog.HolidayCalendar;
import java.time.LocalDate;

/**
 * Lectura del calendario de feriados nacionales y bloqueos por zona.
 */
public interface HolidayRepository {

    /** Calendario con los feriados y bloqueos del rango {@code [from, to]} (ambos inclusive). */
    HolidayCalendar calendarBetween(LocalDate from, LocalDate to);
}
