package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code geo_province}; solo lectura.
 */
@Entity
@Table(name = "geo_province")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProvinceEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 3, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;
}
