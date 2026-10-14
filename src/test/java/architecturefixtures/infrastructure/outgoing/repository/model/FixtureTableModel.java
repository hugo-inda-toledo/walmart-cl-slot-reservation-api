package architecturefixtures.infrastructure.outgoing.repository.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FixtureTableModel {

    @Id
    private String id;
}
