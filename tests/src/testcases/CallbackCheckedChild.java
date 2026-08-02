package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;

import java.io.IOException;

@Buildable
public final class CallbackCheckedChild {
    private final String value;

    @BuilderConstructor
    public CallbackCheckedChild(String value) throws IOException {
        if (value.equals("fail")) {
            throw new IOException("callback child failure");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
