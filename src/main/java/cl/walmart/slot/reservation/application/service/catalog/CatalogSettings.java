package cl.walmart.slot.reservation.application.service.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Ajustes de consulta del catálogo ya validados por la infraestructura. */
public record CatalogSettings(
        @Min(1) @Max(14) int displayDays,
        @Min(1) @Max(60) int horizonDays) {

    public CatalogSettings {
        if (displayDays > horizonDays) {
            throw new IllegalArgumentException("displayDays must not exceed horizonDays");
        }
    }
}
