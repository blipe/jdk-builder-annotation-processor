package integration.app;

import integration.model.Order;
import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record Purchase(
        @Valid Order order,
        @Size(min = 1) @BuilderAdder("order") List<@Valid Order> orders,
        @BuilderAdder("indexedOrder") Map<String, @Valid Order> indexedOrders
) {
}
