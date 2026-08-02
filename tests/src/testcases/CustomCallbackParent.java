package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CustomCallbackParent(CustomCallbackChild child) {
}
