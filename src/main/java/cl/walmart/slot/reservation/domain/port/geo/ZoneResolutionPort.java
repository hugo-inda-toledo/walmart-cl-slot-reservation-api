package cl.walmart.slot.reservation.domain.port.geo;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto publicado de geo para otros contextos (p. ej. {@code delivery-slot-catalog}). Es el único paquete de
 * geo que pueden usar; sus firmas solo emplean tipos del JDK.
 */
public interface ZoneResolutionPort {

    /**
     * Mismas reglas que {@code POST /api/v1/coverage/resolve}. "No cubierta" es un valor; la entrada inválida
     * lanza una {@code DomainException} sin marcar como rollback-only la transacción del llamador.
     */
    ZoneResolution resolveZone(ZoneResolutionQuery query);

    /** Zona por identificador, en cualquier estado; vacío si no existe. */
    Optional<ZoneReference> findZone(UUID zoneId);
}
