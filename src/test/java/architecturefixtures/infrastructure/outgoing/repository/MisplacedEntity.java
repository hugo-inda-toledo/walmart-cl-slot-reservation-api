package architecturefixtures.infrastructure.outgoing.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class MisplacedEntity {

    @Id
    private String id;
}
