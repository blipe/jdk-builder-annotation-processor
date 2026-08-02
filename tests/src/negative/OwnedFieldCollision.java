package negative;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable
record OwnedFieldCollision(
        List<String> values,
        String values$owned
) {
}
