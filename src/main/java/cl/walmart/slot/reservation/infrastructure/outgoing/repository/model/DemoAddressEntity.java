package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code geo_demo_address}; solo lectura (la carga la migración V2_5).
 */
@Entity
@Table(name = "geo_demo_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoAddressEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "number", nullable = false)
    private String number;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "comuna_code", length = 5, nullable = false)
    private String comunaCode;

    @Formula("(select c.name from geo_comuna c where c.code = comuna_code)")
    private String comunaName;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "expected_covered", nullable = false)
    private boolean expectedCovered;

    @Column(name = "expected_zone_code")
    private String expectedZoneCode;

    @Column(name = "expected_method")
    private String expectedMethod;

    @Column(name = "expected_reason")
    private String expectedReason;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
