package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public final class GenericChild extends GenericBase<String> {
    public GenericChild(String value) {
        super(value);
    }
}
