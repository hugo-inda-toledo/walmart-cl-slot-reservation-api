package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.entities.geo.DispatchNode;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;

/** Fakes y fábricas compartidas por los tests de resolvers y del servicio de cobertura. */
public final class ResolverFixtures {

    public static final GeoPoint POINT = new GeoPoint(-33.45, -70.66);

    private ResolverFixtures() {
    }

    public static Comuna comuna(String code, String name, boolean hasBoundary) {
        var cc = new ComunaCode(code);
        return new Comuna(cc, name, cc.provinceCode(), "Provincia", hasBoundary);
    }

    public static DeliveryZone zone(String code, ZoneStatus status, Comuna... comunas) {
        return new DeliveryZone(new DeliveryZoneId(UUID.nameUUIDFromBytes(code.getBytes())), code, code, status,
                new DispatchNode("CD1", "CD 1"), List.of(comunas), comunas.length == 0 ? 1 : 0, 0);
    }

    public static Address address(Comuna declared, GeoPoint point) {
        return new Address("Calle", "1", declared.code(), Optional.ofNullable(point));
    }

    /** Fake de comunas con conteo de invocaciones a {@code findCovering}. */
    public static final class FakeComunas implements ComunaLookupRepository {
        public final Map<ComunaCode, Comuna> byCode = new HashMap<>();
        public Optional<Comuna> covering = Optional.empty();
        public Optional<Comuna> nearest = Optional.empty();
        public int coveringCalls;
        public BorderTolerance lastTolerance;

        public FakeComunas with(Comuna... comunas) {
            for (var c : comunas) {
                byCode.put(c.code(), c);
            }
            return this;
        }

        @Override
        public Optional<Comuna> findByCode(ComunaCode code) {
            return Optional.ofNullable(byCode.get(code));
        }

        @Override
        public List<Comuna> findByRegion(RegionCode code) {
            return new ArrayList<>(byCode.values());
        }

        @Override
        public Optional<Comuna> findCovering(GeoPoint point) {
            coveringCalls++;
            return covering;
        }

        @Override
        public Optional<Comuna> findNearestWithin(GeoPoint point, BorderTolerance tolerance) {
            lastTolerance = tolerance;
            return nearest;
        }
    }

    /** Fake de zonas. */
    public static final class FakeZones implements ZoneLookupRepository {
        public final Map<ZoneAreaKind, DeliveryZone> areas = new HashMap<>();
        public final Map<ComunaCode, DeliveryZone> assigned = new HashMap<>();
        public final List<DeliveryZone> all = new ArrayList<>();
        public Optional<ZoneGeometry> geometry = Optional.empty();
        public Optional<ZoneStatus> lastStatusFilter;

        @Override
        public Optional<DeliveryZone> findById(DeliveryZoneId id) {
            return all.stream().filter(z -> z.id().equals(id)).findFirst();
        }

        @Override
        public List<DeliveryZone> findAll(Optional<ZoneStatus> status) {
            lastStatusFilter = status;
            return all.stream().filter(z -> status.map(s -> z.status() == s).orElse(true)).toList();
        }

        @Override
        public Optional<DeliveryZone> findAssignedTo(ComunaCode comuna) {
            return Optional.ofNullable(assigned.get(comuna));
        }

        @Override
        public Optional<DeliveryZone> findActiveAreaCovering(GeoPoint point, ZoneAreaKind kind) {
            return Optional.ofNullable(areas.get(kind));
        }

        @Override
        public Optional<ZoneGeometry> findGeometry(DeliveryZoneId id) {
            return geometry;
        }
    }
}
