package cl.walmart.slot.reservation.application.usecase.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.domain.repository.geo.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListRegionsAndComunasUseCaseTest {

    private static final RegionCode RM = new RegionCode("13");

    private static Comuna comuna(String code, String name) {
        var cc = new ComunaCode(code);
        return new Comuna(cc, name, cc.provinceCode(), "Provincia", true);
    }

    private static RegionRepository regions(List<Region> all) {
        return new RegionRepository() {
            @Override
            public List<Region> findAllOrderedNorthToSouth() {
                return all;
            }

            @Override
            public boolean exists(RegionCode code) {
                return all.stream().anyMatch(r -> r.code().equals(code));
            }
        };
    }

    private static ComunaLookupRepository comunas(List<Comuna> all) {
        return new ComunaLookupRepository() {
            @Override
            public java.util.Optional<Comuna> findByCode(ComunaCode code) {
                return all.stream().filter(c -> c.code().equals(code)).findFirst();
            }

            @Override
            public List<Comuna> findByRegion(RegionCode code) {
                return all;
            }

            @Override
            public java.util.Optional<Comuna> findCovering(cl.walmart.slot.reservation.domain.entities.geo.GeoPoint point) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Comuna> findNearestWithin(cl.walmart.slot.reservation.domain.entities.geo.GeoPoint point,
                    cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance tolerance) {
                return java.util.Optional.empty();
            }
        };
    }

    @Test
    void listRegionsReturnsRepositoryOrderNorthToSouth() {
        var arica = new Region(new RegionCode("15"), "Arica y Parinacota", 1);
        var tarapaca = new Region(new RegionCode("01"), "Tarapacá", 2);
        var useCase = new ListRegionsUseCaseImpl(regions(List.of(arica, tarapaca)));

        assertThat(useCase.list()).containsExactly(arica, tarapaca);
    }

    @Test
    void comunasAreSortedWithEnyeAsOwnLetter() {
        var all = List.of(comuna("13401", "Padre Hurtado"), comuna("13115", "Ñuñoa"), comuna("13501", "Melipilla"),
                comuna("13119", "Maipú"), comuna("13126", "Peñaflor"), comuna("13122", "Peñalolén"));
        var useCase = new ListComunasByRegionUseCaseImpl(
                regions(List.of(new Region(RM, "Metropolitana", 10))), comunas(all));

        var names = useCase.list(RM).stream().map(Comuna::name).toList();

        assertThat(names).containsExactly("Maipú", "Melipilla", "Ñuñoa", "Padre Hurtado", "Peñaflor", "Peñalolén");
        assertThat(names.indexOf("Ñuñoa")).isGreaterThan(names.indexOf("Melipilla"))
                .isLessThan(names.indexOf("Padre Hurtado"));
    }

    @Test
    void accentsDoNotAlterOrderAndTiesBreakByCode() {
        var all = List.of(comuna("13200", "Maipu"), comuna("13100", "Maipú"), comuna("13300", "Lampa"));
        var useCase = new ListComunasByRegionUseCaseImpl(
                regions(List.of(new Region(RM, "Metropolitana", 10))), comunas(all));

        var codes = useCase.list(RM).stream().map(c -> c.code().value()).toList();

        assertThat(codes).containsExactly("13300", "13100", "13200");
    }

    @Test
    void unknownRegionRaisesRegionNotFound() {
        var useCase = new ListComunasByRegionUseCaseImpl(regions(List.of()), comunas(List.of()));

        assertThatThrownBy(() -> useCase.list(new RegionCode("99")))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.REGION_NOT_FOUND));
    }
}
