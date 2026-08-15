package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record NullAndSamCallbacks(CallbackAddress address, CallbackHandler handler) {
}
