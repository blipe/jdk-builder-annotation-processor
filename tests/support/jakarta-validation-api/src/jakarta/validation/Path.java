package jakarta.validation;

public interface Path extends Iterable<Path.Node> {
    interface Node {
        String getName();
    }
}
