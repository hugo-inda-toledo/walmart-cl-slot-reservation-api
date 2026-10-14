package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Zona de despacho (agregado de lectura). Invariante: debe tener al menos una comuna completa
 * o al menos un área de inclusión.
 */
public final class DeliveryZone {

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    private final DeliveryZoneId id;
    private final String code;
    private final String name;
    private final ZoneStatus status;
    private final DispatchNode node;
    private final List<Comuna> comunas;
    private final int inclusionAreaCount;
    private final int exclusionAreaCount;

    public DeliveryZone(DeliveryZoneId id, String code, String name, ZoneStatus status, DispatchNode node,
                        List<Comuna> comunas, int inclusionAreaCount, int exclusionAreaCount) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.node = Objects.requireNonNull(node, "node must not be null");
        this.comunas = List.copyOf(Objects.requireNonNull(comunas, "comunas must not be null"));
        if (!CODE_FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("zone code must match [A-Z][A-Z0-9_]{1,39}: " + code);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (inclusionAreaCount < 0 || exclusionAreaCount < 0) {
            throw new IllegalArgumentException("area counts must not be negative");
        }
        if (this.comunas.isEmpty() && inclusionAreaCount == 0) {
            throw new IllegalStateException(
                    "zone " + code + " must have at least one comuna or one inclusion area");
        }
        this.inclusionAreaCount = inclusionAreaCount;
        this.exclusionAreaCount = exclusionAreaCount;
    }

    public DeliveryZoneId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ZoneStatus status() {
        return status;
    }

    public DispatchNode node() {
        return node;
    }

    /** Comunas completas asignadas; lista inmutable. */
    public List<Comuna> comunas() {
        return comunas;
    }

    public int inclusionAreaCount() {
        return inclusionAreaCount;
    }

    public int exclusionAreaCount() {
        return exclusionAreaCount;
    }

    public boolean isActive() {
        return status == ZoneStatus.ACTIVE;
    }
}
