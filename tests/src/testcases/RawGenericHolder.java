package testcases;

import io.github.jdkbuilder.Buildable;

@SuppressWarnings("rawtypes")
@Buildable
public record RawGenericHolder(CallbackBox child) {
}
