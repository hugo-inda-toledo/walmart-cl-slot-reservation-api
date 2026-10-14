package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoMultiPolygonDto;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte la geometría de una zona (texto GeoJSON producido por PostGIS) al DTO {@code GeoMultiPolygon}
 * del contrato, con el mapper JSON de la aplicación.
 */
@Component
public class GeoJsonGeometryMapper {

    private final JsonMapper jsonMapper;

    public GeoJsonGeometryMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public GeoMultiPolygonDto toDto(ZoneGeometry geometry) {
        return jsonMapper.readValue(geometry.geoJson(), GeoMultiPolygonDto.class);
    }
}
