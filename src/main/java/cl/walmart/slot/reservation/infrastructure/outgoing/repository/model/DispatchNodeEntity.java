package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de {@code geo_dispatch_node}; solo lectura.
 */
@Entity
@Table(name = "geo_dispatch_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispatchNodeEntity {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;
}
