package testcases;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable(collectionRemovers = false)
public record NoRemovers(List<String> values) {
}
