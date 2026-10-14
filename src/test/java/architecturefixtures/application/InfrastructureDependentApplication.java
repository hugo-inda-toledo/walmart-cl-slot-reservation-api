package architecturefixtures.application;

import architecturefixtures.infrastructure.InfrastructureAdapter;

public final class InfrastructureDependentApplication {

    private final InfrastructureAdapter adapter;

    public InfrastructureDependentApplication(InfrastructureAdapter adapter) {
        this.adapter = adapter;
    }
}
