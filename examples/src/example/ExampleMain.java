package example;

public final class ExampleMain {
    public static void main(String[] args) {
        Customer customer = CustomerBuilder.builder()
                .id("customer-1")
                .name("Ada")
                .addRole("admin")
                .build();

        Customer renamed = CustomerBuilder.from(customer)
                .name("Grace")
                .build();

        System.out.println(customer);
        System.out.println(renamed);
    }
}
