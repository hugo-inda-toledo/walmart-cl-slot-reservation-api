package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado de una resolución: dirección, comuna declarada y la comuna que contiene el punto, consultada una
 * sola vez por resolución (memoizada). No es thread-safe: vive durante una única resolución.
 */
public final class ResolutionContext {

    private final Address address;
    private final Comuna declaredComuna;
    private final ComunaLookupRepository comunaRepository;
    private Optional<Comuna> comunaAtPoint;

    public ResolutionContext(Address address, Comuna declaredComuna, ComunaLookupRepository comunaRepository) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.declaredComuna = Objects.requireNonNull(declaredComuna, "declaredComuna must not be null");
        this.comunaRepository = Objects.requireNonNull(comunaRepository, "comunaRepository must not be null");
    }

    public Address address() {
        return address;
    }

    public Comuna declaredComuna() {
        return declaredComuna;
    }

    public Optional<GeoPoint> point() {
        return address.location();
    }

    /** Comuna cuyo límite cubre el punto; vacío si no hay punto o ningún límite lo cubre. */
    public Optional<Comuna> comunaAtPoint() {
        if (comunaAtPoint == null) {
            comunaAtPoint = point().flatMap(comunaRepository::findCovering);
        }
        return comunaAtPoint;
    }
}
