package testcases;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable
public record Wildcards(List<? extends Number> values) {
}
