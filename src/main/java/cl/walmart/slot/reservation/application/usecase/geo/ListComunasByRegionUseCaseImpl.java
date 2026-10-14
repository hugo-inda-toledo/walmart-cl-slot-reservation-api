package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.usecase.geo.ListComunasByRegionUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.domain.repository.geo.RegionRepository;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ListComunasByRegionUseCaseImpl implements ListComunasByRegionUseCase {

    private final RegionRepository regionRepository;
    private final ComunaLookupRepository comunaRepository;

    ListComunasByRegionUseCaseImpl(RegionRepository regionRepository, ComunaLookupRepository comunaRepository) {
        this.regionRepository = regionRepository;
        this.comunaRepository = comunaRepository;
    }

    @Override
    public List<Comuna> list(RegionCode regionCode) {
        if (!regionRepository.exists(regionCode)) {
            throw GeoException.regionNotFound(regionCode.value());
        }
        return comunaRepository.findByRegion(regionCode).stream()
                .sorted(spanishOrder())
                .toList();
    }

    /** Colación española de fuerza primaria (tildes no alteran el orden, ñ es letra propia); desempate por CUT. */
    private static Comparator<Comuna> spanishOrder() {
        // Collator no es thread-safe: se crea por invocación.
        Collator collator = Collator.getInstance(Locale.of("es", "CL"));
        collator.setStrength(Collator.PRIMARY);
        return Comparator.comparing(Comuna::name, collator).thenComparing(c -> c.code().value());
    }
}
