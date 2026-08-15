package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;

import java.io.IOException;

@Buildable
public final class CheckedAccessorCopy {
    private final String value;

    @BuilderConstructor
    public CheckedAccessorCopy(String value) {
        this.value = value;
    }

    public String value() throws IOException {
        return value;
    }
}
