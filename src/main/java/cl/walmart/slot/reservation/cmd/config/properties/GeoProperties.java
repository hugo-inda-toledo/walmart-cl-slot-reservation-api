package cl.walmart.slot.reservation.cmd.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de cobertura geográfica bajo {@code app.geo}.
 *
 * @param borderToleranceMeters distancia máxima, en metros, para asignar a la comuna más cercana un punto fuera
 *                              de todo límite (0 a 5000; por defecto 300).
 */
@Validated
@ConfigurationProperties("app.geo")
public record GeoProperties(@DefaultValue("300") @Min(0) @Max(5000) int borderToleranceMeters) {
}
