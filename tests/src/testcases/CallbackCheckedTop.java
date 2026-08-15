package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackCheckedTop(CallbackCheckedMiddle middle) {
}
