package cl.walmart.slot.reservation.domain.repository.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de zonas de despacho y sus áreas.
 */
public interface ZoneLookupRepository {

    Optional<DeliveryZone> findById(DeliveryZoneId id);

    /** Zonas ordenadas por código, opcionalmente filtradas por estado. */
    List<DeliveryZone> findAll(Optional<ZoneStatus> status);

    /** Zona a la que está asignada la comuna completa, en cualquier estado. */
    Optional<DeliveryZone> findAssignedTo(ComunaCode comuna);

    /** Zona activa con un área del tipo indicado que cubre el punto; desempate por menor área y código. */
    Optional<DeliveryZone> findActiveAreaCovering(GeoPoint point, ZoneAreaKind kind);

    /** Geometría de la zona; vacío si no tiene polígonos. */
    Optional<ZoneGeometry> findGeometry(DeliveryZoneId id);
}
