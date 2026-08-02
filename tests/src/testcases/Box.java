package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderRequired;

@Buildable
public record Box<T extends Comparable<T>>(@BuilderRequired T value) {
}
