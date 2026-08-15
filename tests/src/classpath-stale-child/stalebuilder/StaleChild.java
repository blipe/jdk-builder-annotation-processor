package stalebuilder;

import io.github.jdkbuilder.Buildable;

@Buildable(builderClassName = "StaleChildDraft", builderMethod = "start", buildMethod = "finish")
public record StaleChild(String value) {
}
