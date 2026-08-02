package testcases;

public class GenericBase<T> {
    private final T value;

    protected GenericBase(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }
}
