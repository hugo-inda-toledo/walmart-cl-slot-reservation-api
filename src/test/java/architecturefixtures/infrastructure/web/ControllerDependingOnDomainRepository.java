package architecturefixtures.infrastructure.web;

import architecturefixtures.domain.FixtureRepository;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ControllerDependingOnDomainRepository {

    private final FixtureRepository repository;

    public ControllerDependingOnDomainRepository(FixtureRepository repository) {
        this.repository = repository;
    }
}
