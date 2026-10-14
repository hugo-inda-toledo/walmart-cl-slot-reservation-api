package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DeliveryZoneEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DeliveryZoneJpaRepository extends JpaRepository<DeliveryZoneEntity, UUID> {

    List<DeliveryZoneEntity> findAllByOrderByCodeAsc();

    List<DeliveryZoneEntity> findAllByStatusOrderByCodeAsc(String status);

    @Query("select z from DeliveryZoneEntity z join z.comunas c where c.code = :comunaCode")
    Optional<DeliveryZoneEntity> findAssignedTo(@Param("comunaCode") String comunaCode);

    /** Id de la zona activa con un área del tipo indicado que cubre el punto; menor área y luego código. */
    @Query(value = """
            select z.id
            from geo_delivery_zone_area a
            join geo_delivery_zone z on z.id = a.zone_id
            where z.status = 'ACTIVE'
              and a.kind = :kind
              and ST_Covers(a.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            order by ST_Area(a.geom), z.code
            limit 1
            """, nativeQuery = true)
    Optional<UUID> findActiveAreaCovering(@Param("lat") double latitude, @Param("lng") double longitude,
                                          @Param("kind") String kind);

    /**
     * Multipolígono GeoJSON: (comunas con límite + inclusiones) - exclusiones. Nulo si no hay polígonos o el
     * resultado queda vacío.
     */
    @Query(value = """
            select ST_AsGeoJSON(g, 6)
            from (
                select ST_Multi(ST_CollectionExtract(
                           ST_Difference(s.included,
                                         coalesce(s.excluded, ST_GeomFromText('GEOMETRYCOLLECTION EMPTY', 4326))),
                           3)) as g
                from (
                    select (select ST_Union(u.geom)
                            from (
                                select b.geom
                                from geo_delivery_zone_comuna zc
                                join geo_comuna_boundary b on b.comuna_code = zc.comuna_code
                                where zc.zone_id = :zoneId
                                union all
                                select a.geom
                                from geo_delivery_zone_area a
                                where a.zone_id = :zoneId and a.kind = 'INCLUSION'
                            ) u) as included,
                           (select ST_Union(a.geom)
                            from geo_delivery_zone_area a
                            where a.zone_id = :zoneId and a.kind = 'EXCLUSION') as excluded
                ) s
                where s.included is not null
            ) r
            where r.g is not null and not ST_IsEmpty(r.g)
            """, nativeQuery = true)
    Optional<String> findGeometryGeoJson(@Param("zoneId") UUID zoneId);
}
