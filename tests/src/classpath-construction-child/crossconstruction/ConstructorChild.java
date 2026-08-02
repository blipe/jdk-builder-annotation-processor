package crossconstruction;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;

import java.io.IOException;

@Buildable
public final class ConstructorChild {
    private final String value;

    public ConstructorChild() {
        this.value = "wrong";
    }

    @BuilderConstructor
    public ConstructorChild(String value) throws IOException {
        if ("fail".equals(value)) {
            throw new IOException("constructor failure");
        }
        this.value = "constructor:" + value;
    }

    public String value() {
        return value;
    }
}
