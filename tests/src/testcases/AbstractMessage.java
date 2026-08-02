package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable
public abstract class AbstractMessage {
    @BuilderFactory
    public static AbstractMessage create(String text) {
        return new TextMessage(text);
    }

    public abstract String text();

    private static final class TextMessage extends AbstractMessage {
        private final String text;

        private TextMessage(String text) {
            this.text = text;
        }

        @Override
        public String text() {
            return text;
        }
    }
}
