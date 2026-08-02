package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderRequired;

@Buildable
record RequiredFieldCollision(
        @BuilderRequired String value,
        String value$set
) {
}
