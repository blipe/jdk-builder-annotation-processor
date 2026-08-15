package testcases;

import io.github.jdkbuilder.Buildable;

import java.util.List;
import java.util.Queue;

@Buildable(defensiveCopyCollections = false)
public record MutableCollectionOptOut(List<String> values, Queue<String> queue) {
}
