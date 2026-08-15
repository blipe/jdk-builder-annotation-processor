package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable(builderCallbacks = false)
public record CallbacksDisabled(CallbackAddress address) {
}
