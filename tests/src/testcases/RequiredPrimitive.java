package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderRequired;

@Buildable
public record RequiredPrimitive(@BuilderRequired int count) {
}
