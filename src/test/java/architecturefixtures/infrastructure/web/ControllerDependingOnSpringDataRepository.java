package architecturefixtures.infrastructure.web;

import architecturefixtures.infrastructure.SpringDataFixtureRepository;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ControllerDependingOnSpringDataRepository {

    private final SpringDataFixtureRepository repository;

    public ControllerDependingOnSpringDataRepository(SpringDataFixtureRepository repository) {
        this.repository = repository;
    }
}
