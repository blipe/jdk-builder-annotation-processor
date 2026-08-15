package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record NoCallbackParent(NoCallbackChild child) {
}
