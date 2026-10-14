package architecturefixtures.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FieldInjectedComponent {

    @Autowired
    private InfrastructureAdapter adapter;
}
