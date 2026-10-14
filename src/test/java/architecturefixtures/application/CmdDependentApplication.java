package architecturefixtures.application;

import architecturefixtures.cmd.config.FixtureProperties;

public final class CmdDependentApplication {

    private final FixtureProperties properties;

    public CmdDependentApplication(FixtureProperties properties) {
        this.properties = properties;
    }
}
