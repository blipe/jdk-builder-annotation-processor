package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderNoAdder;

import java.util.List;

@Buildable
public record NoAdder(@BuilderNoAdder List<String> data) {
}
