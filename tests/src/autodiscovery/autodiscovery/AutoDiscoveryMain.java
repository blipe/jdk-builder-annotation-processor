package autodiscovery;

public final class AutoDiscoveryMain {
    private AutoDiscoveryMain() {
    }

    public static void main(String[] args) {
        AutoDiscovered value = AutoDiscoveredBuilder.builder().value("detected").build();
        if (!"detected".equals(value.value())) {
            throw new AssertionError("processor service auto-discovery failed");
        }
    }
}
