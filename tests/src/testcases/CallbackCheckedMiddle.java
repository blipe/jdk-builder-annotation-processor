package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackCheckedMiddle(CallbackCheckedChild child) {
}
