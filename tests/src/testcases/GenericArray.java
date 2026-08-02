package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record GenericArray<T>(T[] values) {
}
