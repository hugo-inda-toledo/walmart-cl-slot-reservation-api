package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.domain.repository.geo.RegionRepository;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Contrato del adaptador de regiones contra PostGIS real (3.1, 3.2).
 */
@IntegrationTest
class RegionRepositoryImplIT {

    @Autowired
    private RegionRepository regions;

    @Test
    void listsAllRegionsOrderedFromNorthToSouth() {
        List<Region> all = regions.findAllOrderedNorthToSouth();

        assertThat(all).extracting(r -> r.code().value()).containsExactly(
                "15", "01", "02", "03", "04", "05", "13", "06", "07", "16", "08", "09", "14", "10", "11", "12");
        assertThat(all).extracting(Region::northToSouthOrder).isSorted();
        assertThat(all.getFirst().name()).isEqualTo("Región de Arica y Parinacota");
    }

    @Test
    void tellsWhetherARegionExists() {
        assertThat(regions.exists(new RegionCode("13"))).isTrue();
        assertThat(regions.exists(new RegionCode("99"))).isFalse();
    }
}
