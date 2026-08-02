package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackBox<T>(T value) {
}
