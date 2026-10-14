package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

/**
 * Fila de {@code geo_delivery_zone} con sus comunas completas; solo lectura. No mapea geometrías: las áreas se
 * cuentan con fórmulas y se consultan con SQL nativo.
 */
@Entity
@Table(name = "geo_delivery_zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryZoneEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dispatch_node_code", nullable = false)
    private DispatchNodeEntity node;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "geo_delivery_zone_comuna",
            joinColumns = @JoinColumn(name = "zone_id"),
            inverseJoinColumns = @JoinColumn(name = "comuna_code"))
    @OrderBy("code")
    private List<ComunaEntity> comunas;

    @Formula("(select count(*) from geo_delivery_zone_area a where a.zone_id = id and a.kind = 'INCLUSION')")
    private int inclusionAreaCount;

    @Formula("(select count(*) from geo_delivery_zone_area a where a.zone_id = id and a.kind = 'EXCLUSION')")
    private int exclusionAreaCount;
}
