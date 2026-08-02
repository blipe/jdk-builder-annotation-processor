package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackCheckedParent(CallbackCheckedChild child) {
}
