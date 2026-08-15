package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable(callbackMethod = "create", customizeMethod = "apply")
public record CustomCallbackModel(String value) {
}
