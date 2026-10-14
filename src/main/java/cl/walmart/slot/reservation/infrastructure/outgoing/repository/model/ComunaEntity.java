package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code geo_comuna}; solo lectura. No mapea la geometría: las consultas espaciales son SQL nativo.
 */
@Entity
@Table(name = "geo_comuna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComunaEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 5, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "province_code", nullable = false)
    private ProvinceEntity province;

    @Formula("exists (select 1 from geo_comuna_boundary b where b.comuna_code = code)")
    private boolean hasBoundary;
}
