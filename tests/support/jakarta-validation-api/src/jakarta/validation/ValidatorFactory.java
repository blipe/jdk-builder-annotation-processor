package jakarta.validation;

public interface ValidatorFactory extends AutoCloseable {
    Validator getValidator();

    @Override
    void close();
}
