package cl.walmart.slot.reservation.domain.entities.catalog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Etiqueta de una fecha: "Hoy" o la abreviatura fija del día en español (independiente del {@code Locale} de la JVM).
 */
public final class DeliveryDateLabel {

    private static final String TODAY_LABEL = "Hoy";

    private static final Map<DayOfWeek, String> ABBREVIATIONS = Map.of(
            DayOfWeek.MONDAY, "Lun",
            DayOfWeek.TUESDAY, "Mar",
            DayOfWeek.WEDNESDAY, "Mié",
            DayOfWeek.THURSDAY, "Jue",
            DayOfWeek.FRIDAY, "Vie",
            DayOfWeek.SATURDAY, "Sáb",
            DayOfWeek.SUNDAY, "Dom");

    private DeliveryDateLabel() {
    }

    public static String of(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return TODAY_LABEL;
        }
        return ABBREVIATIONS.get(date.getDayOfWeek());
    }
}
