package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackNode(String name, CallbackNode child) {
}
