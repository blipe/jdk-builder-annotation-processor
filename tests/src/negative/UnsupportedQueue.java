package negative;

import io.github.jdkbuilder.Buildable;

import java.util.Queue;

@Buildable
public record UnsupportedQueue(Queue<String> values) {
}
