package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record FactoryCallbackParent(FactoryProduct product) {
}
