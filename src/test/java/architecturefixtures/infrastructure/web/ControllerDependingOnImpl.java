package architecturefixtures.infrastructure.web;

import architecturefixtures.application.ExampleUseCaseImpl;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ControllerDependingOnImpl {

    private final ExampleUseCaseImpl useCase;

    public ControllerDependingOnImpl(ExampleUseCaseImpl useCase) {
        this.useCase = useCase;
    }
}
