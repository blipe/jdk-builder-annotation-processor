package integration.app;

import integration.model.AddressBuilder;
import integration.model.FactoryTokenBuilder;
import integration.model.FullChecks;
import integration.model.LineBuilder;
import integration.model.OptionalNote;
import integration.model.OptionalNoteBuilder;
import integration.model.OrderBuilder;
import integration.model.Range;
import integration.model.RangeBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;

import java.util.Set;

public final class RealValidationMain {
    private RealValidationMain() {
    }

    public static void main(String[] args) throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            testValidCrossModuleGraph(validator);
            testNestedCollectionValidation(validator);
            testContainerConstraints(validator);
            testGroupConversionAndSequence(validator);
            testClassLevelConstraint(validator);
            testExecutableValidation(validator);
            testOptionalValidation(validator);
            testFactoryValidation(validator);
        }
        System.out.println("REAL HIBERNATE VALIDATOR TESTS PASSED");
    }

    private static void testValidCrossModuleGraph(Validator validator) throws Exception {
        Purchase purchase = PurchaseBuilder.of(
                validator,
                builder -> builder
                        .orderUsing(order -> validOrder(order, "root"))
                        .addOrderUsing(order -> validOrder(order, "list"))
                        .putIndexedOrderUsing("primary", order -> validOrder(order, "map")),
                Default.class
        );
        check("root".equals(purchase.order().id()), "singular cross-module callback failed");
        check(purchase.orders().size() == 1, "deferred collection callback failed");
        check(purchase.indexedOrders().size() == 1, "deferred map callback failed");
    }

    private static OrderBuilder validOrder(OrderBuilder builder, String id) {
        return builder
                .id(id)
                .addressUsing(address -> validAddress(address))
                .addLineUsing(line -> validLine(line, id + "-line"))
                .putIndexedLineUsing("primary", line -> validLine(line, id + "-indexed"));
    }

    private static AddressBuilder validAddress(AddressBuilder builder) {
        return builder.city("Chicago").postalCode("60601");
    }

    private static LineBuilder validLine(LineBuilder builder, String sku) {
        return builder.sku(sku).quantity(1);
    }

    private static void testNestedCollectionValidation(Validator validator) {
        expectViolation(
                "nested collection element",
                () -> PurchaseBuilder.of(
                        validator,
                        builder -> builder
                                .orderUsing(order -> validOrder(order, "root"))
                                .addOrderUsing(order -> order
                                        .id("bad")
                                        .addressUsing(RealValidationMain::validAddress)
                                        .addLineUsing(line -> line.sku("broken").quantity(0))),
                        Default.class
                ),
                "quantity",
                "must be greater than 0"
        );
    }

    private static void testContainerConstraints(Validator validator) {
        expectViolation(
                "collection size",
                () -> OrderBuilder.builder()
                        .id("empty-lines")
                        .addressUsing(RealValidationMain::validAddress)
                        .lines(java.util.List.of())
                        .build(validator, Default.class),
                "lines",
                "size must be between"
        );

        expectViolation(
                "map key container element",
                () -> OrderBuilder.builder()
                        .id("bad-map-key")
                        .addressUsing(RealValidationMain::validAddress)
                        .addLineUsing(line -> validLine(line, "valid"))
                        .putIndexedLineUsing("", line -> validLine(line, "indexed"))
                        .build(validator, Default.class),
                "indexedLines",
                "must not be blank"
        );

        expectViolation(
                "map value cascade",
                () -> OrderBuilder.builder()
                        .id("bad-map-value")
                        .addressUsing(RealValidationMain::validAddress)
                        .addLineUsing(line -> validLine(line, "valid"))
                        .putIndexedLineUsing("primary", line -> line.sku("broken").quantity(0))
                        .build(validator, Default.class),
                "quantity",
                "must be greater than 0"
        );
    }

    private static void testGroupConversionAndSequence(Validator validator) {
        expectViolation(
                "converted Basic group",
                () -> OrderBuilder.builder()
                        .id("bad-city")
                        .addressUsing(address -> address.city("").postalCode("bad"))
                        .addLineUsing(line -> validLine(line, "sku"))
                        .build(validator, Default.class),
                "city",
                "must not be blank"
        );

        expectViolation(
                "converted Strict group",
                () -> OrderBuilder.builder()
                        .id("bad-postal")
                        .addressUsing(address -> address.city("Evanston").postalCode("bad"))
                        .addLineUsing(line -> validLine(line, "sku"))
                        .build(validator, FullChecks.class),
                "postalCode",
                "must match"
        );
    }

    private static void testClassLevelConstraint(Validator validator) {
        expectViolation(
                "class-level custom constraint",
                () -> OrderBuilder.builder()
                        .id("bad-zip")
                        .addressUsing(address -> address.city("Chicago").postalCode("99999"))
                        .addLineUsing(line -> validLine(line, "sku"))
                        .build(validator, Default.class),
                "",
                "Chicago postal code"
        );
    }

    private static void testExecutableValidation(Validator validator) {
        Range.resetConstructionCount();
        expectViolation(
                "constructor parameter",
                () -> RangeBuilder.builder().start(-1).end(2).build(validator),
                "start",
                "must be greater than or equal to 0"
        );
        check(Range.constructionCount() == 0, "parameter violation constructed the object");

        Range.resetConstructionCount();
        expectViolation(
                "cross-parameter",
                () -> RangeBuilder.builder().start(8).end(2).build(validator),
                "",
                "start must be less than or equal to end"
        );
        check(Range.constructionCount() == 0, "cross-parameter violation constructed the object");

        Range.resetConstructionCount();
        expectViolation(
                "constructor return value",
                () -> RangeBuilder.builder().start(2).end(5).build(validator),
                "",
                "range width must be even"
        );
        check(Range.constructionCount() == 1, "return-value validation did not construct exactly once");

        Range.resetConstructionCount();
        Range valid = RangeBuilder.builder().start(2).end(6).build(validator);
        check(valid.width() == 4, "valid executable path failed");
        check(Range.constructionCount() == 1, "valid executable path constructed more than once");
    }

    private static void testOptionalValidation(Validator validator) {
        OptionalNote unvalidated = OptionalNoteBuilder.builder().text("").build();
        check(unvalidated.text().isEmpty(), "ordinary OPTIONAL build unexpectedly validated");
        expectViolation(
                "optional validated overload",
                () -> OptionalNoteBuilder.builder().text("").build(validator),
                "text",
                "must not be blank"
        );
    }

    private static void testFactoryValidation(Validator validator) {
        expectViolation(
                "factory completed-bean validation",
                () -> FactoryTokenBuilder.builder().value("").build(validator),
                "value",
                "must not be blank"
        );
        check("ok".equals(FactoryTokenBuilder.builder().value("ok").build(validator).value()),
                "factory-backed validated build failed");
    }

    private static void expectViolation(
            String label,
            ThrowingRunnable action,
            String pathFragment,
            String messageFragment
    ) {
        try {
            action.run();
        } catch (ConstraintViolationException expected) {
            Set<ConstraintViolation<?>> violations = expected.getConstraintViolations();
            boolean matched = violations.stream().anyMatch(violation ->
                    violation.getPropertyPath().toString().contains(pathFragment)
                            && violation.getMessage().contains(messageFragment));
            if (!matched) {
                throw new AssertionError(label + " produced unexpected violations: " + violations, expected);
            }
            return;
        } catch (Exception unexpected) {
            throw new AssertionError(label + " threw the wrong exception", unexpected);
        }
        throw new AssertionError(label + " did not produce a ConstraintViolationException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
