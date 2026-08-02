package negative;

import io.github.jdkbuilder.Buildable;

@Buildable
public final class MissingAccessor {
    private final String hidden;

    public MissingAccessor(String hidden) {
        this.hidden = hidden;
    }
}
