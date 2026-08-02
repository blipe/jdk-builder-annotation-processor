package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record GenericCallbackHolder<T>(CallbackBox<T> box) {
}
