package negative;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable(buildMethod = "clearRoles")
public record CustomizedMethodCollision(List<String> roles) {
}
