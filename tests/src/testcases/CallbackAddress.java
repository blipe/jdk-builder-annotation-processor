package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable
public record CallbackAddress(String city, String postalCode) {
}
