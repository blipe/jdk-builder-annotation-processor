package testcases;

import io.github.jdkbuilder.Buildable;

public final class Envelope {
    private Envelope() {
    }

    @Buildable
    public static record Item<T>(T payload, int sequence) {
    }
}
