package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.ComunaEntity;
import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.ComunaRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ComunaJpaRepository extends JpaRepository<ComunaEntity, String> {

    @Query("select c from ComunaEntity c where c.code like :prefix")
    List<ComunaEntity> findByCodePrefix(@Param("prefix") String prefix);

    /** Comuna cuyo límite cubre el punto (borde incluido); desempate por menor CUT. */
    @Query(value = """
            select c.code as code, c.name as name, c.province_code as provinceCode,
                   p.name as provinceName, true as hasBoundary
            from geo_comuna_boundary b
            join geo_comuna c on c.code = b.comuna_code
            join geo_province p on p.code = c.province_code
            where ST_Covers(b.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            order by c.code
            limit 1
            """, nativeQuery = true)
    Optional<ComunaRow> findCovering(@Param("lat") double latitude, @Param("lng") double longitude);

    /**
     * Candidatos por KNN sobre el índice GiST (distancia plana) y filtro por distancia geodésica en metros;
     * desempate por distancia y luego por CUT.
     */
    @Query(value = """
            select c.code as code, c.name as name, c.province_code as provinceCode,
                   p.name as provinceName, true as hasBoundary
            from (
                select b.comuna_code, b.geom
                from geo_comuna_boundary b
                order by b.geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                limit 5
            ) cand
            join geo_comuna c on c.code = cand.comuna_code
            join geo_province p on p.code = c.province_code
            where ST_Distance(CAST(cand.geom AS geography),
                              CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)) <= :meters
            order by ST_Distance(CAST(cand.geom AS geography),
                                 CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)),
                     c.code
            limit 1
            """, nativeQuery = true)
    Optional<ComunaRow> findNearestWithin(@Param("lat") double latitude, @Param("lng") double longitude,
                                          @Param("meters") double meters);
}
