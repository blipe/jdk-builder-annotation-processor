package crossconstruction;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

import java.io.IOException;

@Buildable
public final class FactoryChild {
    private final String value;

    private FactoryChild(String value) {
        this.value = value;
    }

    @BuilderFactory
    public static FactoryChild create(String value) throws IOException {
        if ("fail".equals(value)) {
            throw new IOException("factory failure");
        }
        return new FactoryChild("factory:" + value);
    }

    public String value() {
        return value;
    }
}
