package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable(defensiveCopyArrays = false)
public record MutableArrayOptOut(int[] values) {
}
