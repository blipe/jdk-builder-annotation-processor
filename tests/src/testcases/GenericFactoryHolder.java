package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record GenericFactoryHolder<T>(GenericFactoryBox<T> box) {
}
