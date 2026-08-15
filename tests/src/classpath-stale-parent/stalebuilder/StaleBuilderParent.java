package stalebuilder;

import io.github.jdkbuilder.Buildable;

@Buildable
public record StaleBuilderParent(StaleChild child) {
}
