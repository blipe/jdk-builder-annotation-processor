package testcases;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class TestMain {
    public static void main(String[] args) throws Exception {
        testRecordBuilder();
        testCopyBuilder();
        testRequiredValidation();
        testClassBuilder();
        testGenericRecord();
        testNestedRecord();
        testCollisionSafeGeneratedNames();
        testAnnotationStrippingDoesNotChangeRuntimeConstruction();
        testCheckedException();
        testWildcardCollection();
        testInheritedGenericAccessor();
        testRequiredPrimitive();
        testDefaultsAndArrays();
        testBuilderCallbacks();
        testBuilderCallbackGenericsAndCheckedExceptions();
        testCallbackCorrectionHardening();
        testDeferredCollectionAndMapCallbacks();
        testValidatedBuilderCallbackPropagation();
        testCollectionRemovalHelpers();
        testGeneratedApiCustomization();
        testStaticFactoryConstruction();
        testFactoryJakartaBeanValidation();
        testAllCollectionPolicies();
        testCollectionCopyFromSnapshot();
        testCollectionOptOut();
        testJakartaValidationOptional();
        testJakartaValidationRequired();
        testJakartaValidationGeneric();
        testJakartaConstructorValidationPhases();
        testJakartaValidatedOverloadedConstructor();
        testJakartaValidationCheckedException();
        System.out.println("ALL TESTS PASSED");
    }

    private static void testRecordBuilder() {
        ArrayList<String> mutableRoles = new ArrayList<>();
        mutableRoles.add("reader");

        PersonBuilder builder = PersonBuilder.builder()
                .name("Ada")
                .age(37)
                .roles(mutableRoles)
                .addRole("admin")
                .addPermission("deploy")
                .putAttribute("region", "us-central");

        check(mutableRoles.equals(List.of("reader")), "adder does not mutate setter input");
        Person person = builder.build();
        mutableRoles.add("late-change");
        check(person.name().equals("Ada"), "record name");
        check(person.age() == 37, "record age");
        check(person.roles().equals(List.of("reader", "admin")), "list adders/copy");
        check(person.permissions().equals(Set.of("deploy")), "set adder");
        check(person.attributes().equals(Map.of("region", "us-central")), "map adder");

        expectThrows(UnsupportedOperationException.class, () -> person.roles().add("x"));
    }

    private static void testCopyBuilder() {
        Person original = PersonBuilder.builder()
                .name("Grace")
                .age(50)
                .addRole("engineer")
                .build();

        Person changed = PersonBuilder.from(original)
                .age(51)
                .addRole("reviewer")
                .build();

        check(original.age() == 50, "copy leaves original unchanged");
        check(original.roles().equals(List.of("engineer")), "copy adder does not mutate source");
        check(changed.age() == 51, "copy changes selected field");
        check(changed.roles().equals(List.of("engineer", "reviewer")), "copy-on-write adder");
    }

    private static void testRequiredValidation() {
        IllegalStateException missing = expectThrows(
                IllegalStateException.class,
                () -> PersonBuilder.builder().age(1).build()
        );
        check(missing.getMessage().contains("name"), "missing required name message");

        IllegalStateException nullValue = expectThrows(
                IllegalStateException.class,
                () -> PersonBuilder.builder().name(null).build()
        );
        check(nullValue.getMessage().contains("was null"), "required null message");
    }

    private static void testClassBuilder() {
        Account account = AccountBuilder.builder()
                .accountId("A-1")
                .active(true)
                .balance(900L)
                .build();

        check(account.id().equals("A-1"), "explicit accessor");
        check(account.isActive(), "boolean accessor");
        check(account.getBalance() == 900L, "bean accessor");

        Account copy = AccountBuilder.from(account).balance(901L).build();
        check(copy.id().equals("A-1"), "class copy");
        check(copy.getBalance() == 901L, "class copy mutation");
    }

    private static void testGenericRecord() {
        Box<String> box = BoxBuilder.<String>builder().value("value").build();
        Box<String> copy = BoxBuilder.from(box).value("copy").build();
        check(copy.value().equals("copy"), "generic builder and copy");
    }

    private static void testNestedRecord() {
        Envelope.Item<String> item = Envelope$ItemBuilder.<String>builder()
                .payload("payload")
                .sequence(7)
                .build();
        check(item.sequence() == 7, "nested record builder");
    }

    private static void testCollisionSafeGeneratedNames() {
        CollisionSafeNames.Nested nested = CollisionSafeNames$NestedBuilder.builder()
                .value("nested")
                .build();
        CollisionSafeNames_Nested flat = CollisionSafeNames_NestedBuilder.builder()
                .value("flat")
                .build();

        check(nested.value().equals("nested"), "binary-name nested builder");
        check(flat.value().equals("flat"), "underscore top-level builder");
    }

    private static void testAnnotationStrippingDoesNotChangeRuntimeConstruction() {
        AnnotatedTypes<String> value = AnnotatedTypesBuilder.<String>builder()
                .name("name")
                .tags(List.of("tag"))
                .value("value")
                .aliases(new String[]{"alias"})
                .numbers(List.of(1, 2L))
                .build();

        check(value.tags().equals(List.of("tag")), "annotated generic collection construction");
        check(value.aliases().length == 1, "annotated array construction");
    }

    private static void testCheckedException() throws IOException {
        CheckedConstruction value = CheckedConstructionBuilder.builder().value("ok").build();
        check(value.value().equals("ok"), "checked constructor throws declaration");
    }

    private static void testWildcardCollection() {
        Wildcards wildcard = WildcardsBuilder.builder().values(List.of(1, 2L)).build();
        check(wildcard.values().size() == 2, "wildcard setter compiles");
    }

    private static void testInheritedGenericAccessor() {
        GenericChild original = GenericChildBuilder.builder().value("original").build();
        GenericChild copy = GenericChildBuilder.toBuilder(original).value("copy").build();
        check(copy.value().equals("copy"), "inherited generic accessor is resolved as member");
    }

    private static void testRequiredPrimitive() {
        expectThrows(IllegalStateException.class, () -> RequiredPrimitiveBuilder.builder().build());
        RequiredPrimitive value = RequiredPrimitiveBuilder.builder().count(0).build();
        check(value.count() == 0, "required primitive tracks explicit zero");
    }


    private static void testDefaultsAndArrays() {
        DefaultsAndArrays.resetDefaultCalls();
        int[] numbers = {1, 2};
        String[][] matrix = {{"a"}, {"b"}};

        DefaultsAndArraysBuilder builder = DefaultsAndArraysBuilder.builder()
                .numbers(numbers)
                .matrix(matrix);
        numbers[0] = 99;
        matrix[0] = new String[]{"changed-reference"};

        DefaultsAndArrays first = builder.build();
        check(first.name().equals("default-name-1"), "lazy scalar default");
        check(first.roles().equals(List.of("reader")), "lazy collection default");
        check(DefaultsAndArrays.nameDefaultCalls() == 1, "scalar default invoked once per build");
        check(DefaultsAndArrays.rolesDefaultCalls() == 1, "collection default invoked once per build");
        check(first.numbers()[0] == 1, "array setter snapshots input");
        check(first.matrix()[0][0].equals("a"), "multidimensional array top-level snapshot");
        expectThrows(UnsupportedOperationException.class, () -> first.roles().add("x"));

        first.numbers()[0] = 77;
        DefaultsAndArrays second = builder.build();
        check(second.numbers()[0] == 1, "every build receives a fresh array clone");
        check(second.name().equals("default-name-2"), "default is reevaluated for builder reuse");

        int rolesBeforeHelper = DefaultsAndArrays.rolesDefaultCalls();
        DefaultsAndArrays extendedDefault = DefaultsAndArraysBuilder.builder()
                .addRole("admin")
                .build();
        check(extendedDefault.roles().equals(List.of("reader", "admin")),
                "collection helper starts from default value");
        check(DefaultsAndArrays.rolesDefaultCalls() == rolesBeforeHelper + 1,
                "collection helper evaluates default provider exactly once");

        int beforeExplicitNull = DefaultsAndArrays.nameDefaultCalls();
        DefaultsAndArrays explicitNull = DefaultsAndArraysBuilder.builder().name(null).build();
        check(explicitNull.name() == null, "explicit null suppresses default");
        check(DefaultsAndArrays.nameDefaultCalls() == beforeExplicitNull,
                "explicit assignment does not invoke default provider");

        int[] sourceNumbers = {4, 5};
        DefaultsAndArrays source = new DefaultsAndArrays("source", List.of(), sourceNumbers, null);
        DefaultsAndArraysBuilder copy = DefaultsAndArraysBuilder.from(source);
        sourceNumbers[0] = 100;
        check(copy.build().numbers()[0] == 4, "copyFrom snapshots arrays");

        int[] shared = {8};
        MutableArrayOptOut optOut = MutableArrayOptOutBuilder.builder().values(shared).build();
        check(optOut.values() == shared, "explicit array-copy opt-out preserves reference");
    }

    private static void testBuilderCallbacks() {
        CallbackOrder value = CallbackOrderBuilder.of(order -> order
                .id("order-1")
                .addressUsing(address -> address
                        .city("Chicago")
                        .postalCode("60601"))
                .addLineUsing(line -> line.sku("A").quantity(2))
                .addLineUsing(line -> line.sku("B").quantity(1))
                .putIndexedLineUsing("primary", line -> line.sku("A").quantity(2))
                .customize(builder -> builder.putIndexedLineUsing(
                        "secondary",
                        line -> line.sku("B").quantity(1)
                )));

        check(value.id().equals("order-1"), "root builder callback");
        check(value.address().city().equals("Chicago"), "nested value callback");
        check(value.lines().size() == 2, "collection element callbacks");
        check(value.indexedLines().get("secondary").sku().equals("B"),
                "map value callback and customize consumer");
        expectThrows(UnsupportedOperationException.class,
                () -> value.lines().add(new CallbackLine("C", 1)));

        CallbackAddress directWins = CallbackOrderBuilder.builder()
                .addressUsing(address -> address.city("first").postalCode("1"))
                .address(new CallbackAddress("direct", "2"))
                .build()
                .address();
        check(directWins.city().equals("direct"), "direct setter clears deferred callback");

        CallbackAddress callbackWins = CallbackOrderBuilder.builder()
                .address(new CallbackAddress("direct", "2"))
                .addressUsing(address -> address.city("callback").postalCode("3"))
                .build()
                .address();
        check(callbackWins.city().equals("callback"), "callback setter clears direct value");

        CustomCallbackModel custom = CustomCallbackModelBuilder.create(builder -> builder
                .apply(current -> current.value("custom")));
        check(custom.value().equals("custom"), "custom callback method names");

        CustomCallbackParent customChild = CustomCallbackParentBuilder.of(parent -> parent
                .childUsing(child -> child.value("custom-child")));
        check(customChild.child().value().equals("custom-child"),
                "nested callback honors child builder package and method customization");

        FactoryCallbackParent factoryChild = FactoryCallbackParentBuilder.of(parent -> parent
                .productUsing(product -> product.id("factory-callback").addTag("nested")));
        check(factoryChild.product().id().equals("factory-callback")
                        && factoryChild.product().tags().equals(List.of("nested")),
                "factory-backed nested callback");

        InterfaceCallbackParent interfaceChild = InterfaceCallbackParentBuilder.of(parent -> parent
                .contractUsing(contract -> contract.value("interface-callback")));
        check(interfaceChild.contract().value().equals("interface-callback"),
                "factory-backed interface callback");

        CallbackNode recursive = CallbackNodeBuilder.of(node -> node
                .name("root")
                .childUsing(child -> child.name("leaf")));
        check(recursive.child().name().equals("leaf"), "recursive callback model");

        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.of(
                        (java.util.function.Function<CallbackOrderBuilder, CallbackOrderBuilder>) null
                ));
        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.of(ignored -> null));
        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.builder().addressUsing(
                        (java.util.function.Function<CallbackAddressBuilder, CallbackAddressBuilder>) null
                ));
        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.builder().addressUsing(ignored -> null));
        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.builder().addLineUsing(ignored -> null));
        expectThrows(NullPointerException.class,
                () -> CallbackOrderBuilder.builder().customize(null));

        NoCallbackParent noChildCallback = NoCallbackParentBuilder.of(builder -> builder
                .child(new NoCallbackChild("direct")));
        check(noChildCallback.child().value().equals("direct"),
                "child callback opt-out keeps direct setter");
    }

    private static void testBuilderCallbackGenericsAndCheckedExceptions() throws IOException {
        GenericCallbackHolder<String> generic = GenericCallbackHolderBuilder.<String>of(holder -> holder
                .boxUsing(box -> box.value("generic")));
        check(generic.box().value().equals("generic"), "generic nested builder callback");

        CallbackCheckedParent checked = CallbackCheckedParentBuilder.of(parent -> parent
                .childUsing(child -> child.value("ok")));
        check(checked.child().value().equals("ok"),
                "checked child construction is deferred to parent build");
        expectThrows(IOException.class,
                () -> CallbackCheckedParentBuilder.of(parent -> parent
                        .childUsing(child -> child.value("fail"))));

        CallbackCheckedTop transitive = CallbackCheckedTopBuilder.of(top -> top
                .middleUsing(middle -> middle
                        .childUsing(child -> child.value("transitive"))));
        check(transitive.middle().child().value().equals("transitive"),
                "transitive checked callback construction");
        expectThrows(IOException.class,
                () -> CallbackCheckedTopBuilder.of(top -> top
                        .middleUsing(middle -> middle
                                .childUsing(child -> child.value("fail")))));

        CallbackCheckedCollection collection = CallbackCheckedCollectionBuilder.builder()
                .addChildUsing(child -> child.value("list"))
                .putChildByKeyUsing("map", child -> child.value("map"))
                .build();
        check(collection.children().getFirst().value().equals("list"),
                "checked collection callback");
        check(collection.childrenByKey().get("map").value().equals("map"),
                "checked map callback");
        expectThrows(IOException.class,
                () -> CallbackCheckedCollectionBuilder.builder()
                        .addChildUsing(child -> child.value("fail"))
                        .build());
    }

    private static void testCallbackCorrectionHardening() throws IOException {
        NullAndSamCallbacks direct = NullAndSamCallbacksBuilder.builder()
                .address(null)
                .handler(value -> value + "!")
                .build();
        check(direct.address() == null, "explicit null direct setter remains unambiguous");
        check(direct.handler().apply("ok").equals("ok!"),
                "functional-interface direct setter remains unambiguous");

        NullAndSamCallbacks nested = NullAndSamCallbacksBuilder.builder()
                .addressUsing(address -> address.city("Chicago").postalCode("60601"))
                .handlerUsing(handler -> handler.prefix("pre-"))
                .build();
        check(nested.address().city().equals("Chicago"), "distinct nested callback name");
        check(nested.handler().apply("value").equals("pre-value"),
                "functional-interface builder callback uses distinct method");

        LocalVariableCollision collision = LocalVariableCollisionBuilder.builder()
                .x("x")
                .raw$x("raw")
                .value0("value")
                .build();
        check(collision.raw$x().equals("raw"), "generated locals use numeric names");

        CheckedAccessorCopy copied = CheckedAccessorCopyBuilder
                .from(new CheckedAccessorCopy("copy"))
                .build();
        check(copied.value().equals("copy"), "checked accessor exception propagates from copy API");

        GenericFactoryBox<String> genericFactory = GenericFactoryBoxBuilder.<String>builder()
                .value("generic-factory")
                .build();
        check(genericFactory.value().equals("generic-factory"), "generic static factory method");

        GenericFactoryHolder<String> holder = GenericFactoryHolderBuilder.<String>of(parent -> parent
                .boxUsing(box -> box.value("nested-generic-factory")));
        check(holder.box().value().equals("nested-generic-factory"),
                "generic factory-backed nested callback");
    }

    private static void testDeferredCollectionAndMapCallbacks() throws IOException {
        CallbackLine base = new CallbackLine("base", 1);
        CallbackLine direct = new CallbackLine("direct", 2);
        CallbackLine bulk = new CallbackLine("bulk", 3);

        CallbackOrderBuilder builder = CallbackOrderBuilder.builder()
                .lines(List.of(base))
                .addLineUsing(line -> line.sku("callback").quantity(4))
                .addLine(direct)
                .removeLine(base)
                .addAllLines(List.of(bulk))
                .putIndexedLine("direct", direct)
                .putIndexedLineUsing("callback", line -> line.sku("mapped").quantity(5))
                .removeIndexedLine("direct");

        CallbackOrder first = builder.build();
        check(first.lines().stream().map(CallbackLine::sku).toList()
                        .equals(List.of("callback", "direct", "bulk")),
                "deferred collection callbacks preserve operation order");
        check(first.indexedLines().keySet().equals(Set.of("callback")),
                "deferred map callbacks preserve removals");

        builder.clearLines().addLineUsing(line -> line.sku("second").quantity(6));
        CallbackOrder second = builder.build();
        check(first.lines().size() == 3, "builder reuse does not mutate prior immutable result");
        check(second.lines().stream().map(CallbackLine::sku).toList().equals(List.of("second")),
                "deferred clear and subsequent callback order");

        CallbackCheckedCollection root = CallbackCheckedCollectionBuilder.of(parent -> parent
                .addChildUsing(child -> child.value("root-list"))
                .putChildByKeyUsing("root-map", child -> child.value("root-map")));
        check(root.children().getFirst().value().equals("root-list"),
                "root callback composes with checked collection child");

        expectThrows(IOException.class,
                () -> CallbackCheckedCollectionBuilder.of(parent -> parent
                        .addChildUsing(child -> child.value("fail"))));

        ValidationRecorder recorder = new ValidationRecorder(FailurePhase.NONE);
        RequiredCollectionParent validated = RequiredCollectionParentBuilder.of(
                recorder.validator(),
                parent -> parent
                        .addChildUsing(child -> child.value("list"))
                        .putChildByKeyUsing("map", child -> child.value("map")),
                StrictValidation.class
        );
        check(validated.children().getFirst().value().equals("list"),
                "required validated collection child callback");
        check(validated.childrenByKey().get("map").value().equals("map"),
                "required validated map child callback");
        check(recorder.phases.equals(List.of(
                        "constructorParameters", "constructorReturnValue", "bean",
                        "constructorParameters", "constructorReturnValue", "bean",
                        "constructorParameters", "constructorReturnValue", "bean")),
                "validator and groups propagate through deferred collection graph");
    }

    private static void testValidatedBuilderCallbackPropagation() {
        ValidationRecorder recorder = new ValidationRecorder(FailurePhase.NONE);
        RequiredCallbackParent parent = RequiredCallbackParentBuilder.of(
                recorder.validator(),
                builder -> builder.childUsing(child -> child.value("validated")),
                StrictValidation.class
        );
        check(parent.child().value().equals("validated"),
                "required nested callback builds with outer validator");
        check(recorder.phases.equals(List.of(
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean",
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean"
                )),
                "outer validator and groups propagate through nested required builder");
        check(recorder.lastGroups.length == 1
                        && recorder.lastGroups[0] == StrictValidation.class,
                "nested callback validation groups propagate");

        ValidationRecorder optionalRecorder = new ValidationRecorder(FailurePhase.NONE);
        OptionalCallbackParent optional = OptionalCallbackParentBuilder.of(
                optionalRecorder.validator(),
                builder -> builder.childUsing(child -> child.value("optional")),
                StrictValidation.class
        );
        check(optional.child().value().equals("optional"),
                "optional nested builder callback constructs value");
        check(optionalRecorder.phases.equals(List.of(
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean",
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean"
                )),
                "validated optional parent propagates validator to optional child");

        OptionalCallbackParent unvalidated = OptionalCallbackParentBuilder.of(builder -> builder
                .childUsing(child -> child.value("unvalidated")));
        check(unvalidated.child().value().equals("unvalidated"),
                "optional callback retains ordinary root build path");
    }

    private static void testCollectionRemovalHelpers() {
        Person value = PersonBuilder.builder()
                .name("helpers")
                .roles(List.of("reader", "writer", "admin"))
                .removeRole("reader")
                .removeAllRoles(List.of("writer"))
                .addRole("reviewer")
                .permissions(Set.of("deploy", "read"))
                .removePermission("read")
                .attributes(Map.of("region", "us", "tier", "gold"))
                .removeAttribute("tier")
                .putAttribute("owner", "platform")
                .build();
        check(value.roles().equals(List.of("admin", "reviewer")), "list remove helpers");
        check(value.permissions().equals(Set.of("deploy")), "set remove helper");
        check(value.attributes().equals(Map.of("region", "us", "owner", "platform")),
                "map remove helper");

        Person cleared = PersonBuilder.from(value)
                .clearRoles()
                .clearPermissions()
                .clearAttributes()
                .build();
        check(cleared.roles().isEmpty(), "clear list helper");
        check(cleared.permissions().isEmpty(), "clear set helper");
        check(cleared.attributes().isEmpty(), "clear map helper");

        NoRemovers noRemovers = NoRemoversBuilder.builder()
                .addValue("kept")
                .build();
        check(noRemovers.values().equals(List.of("kept")),
                "collectionRemovers=false retains adders");
    }

    private static void testGeneratedApiCustomization() {
        testcases.generated.OrderDraft draft = testcases.generated.OrderDraft.newDraft()
                .withName("custom")
                .withItems(List.of("one"))
                .addItem("two");
        CustomModel value = draft.create();
        check(value.items().equals(List.of("one", "two")), "custom package and setter prefix");

        CustomModel copy = testcases.generated.OrderDraft.copyOf(value)
                .withName("copy")
                .create();
        check(copy.name().equals("copy"), "custom from method");

        CustomModel edited = testcases.generated.OrderDraft.edit(copy)
                .load(value)
                .withName("edited")
                .create();
        check(edited.name().equals("edited"), "custom toBuilder/copyFrom methods");

        LocalModel local = LocalDraft.start().setValue("local").finish();
        check(local.value().equals("local"), "package-private customized builder");
    }

    private static void testStaticFactoryConstruction() throws IOException {
        FactoryProduct product = FactoryProductBuilder.builder()
                .addTag("new")
                .build();
        check(product.id().equals("factory-default"), "factory parameter default");
        check(product.tags().equals(List.of("new")), "private constructor via static factory");
        expectThrows(UnsupportedOperationException.class, () -> product.tags().add("mutable"));

        FactoryRecord factoryRecord = FactoryRecordBuilder.builder()
                .value("  normalized  ")
                .build();
        check(factoryRecord.value().equals("normalized"), "record construction through static factory");

        FactoryProduct copy = FactoryProductBuilder.from(product)
                .id("explicit")
                .build();
        check(copy.id().equals("explicit"), "factory-backed copy builder");

        AbstractMessage message = AbstractMessageBuilder.builder()
                .text("polymorphic")
                .build();
        check(message.text().equals("polymorphic"),
                "abstract target constructed through concrete factory result");

        FactoryContract contract = FactoryContractBuilder.builder()
                .value("interface")
                .build();
        check(contract.value().equals("interface"), "interface static factory target");

        NullPointerException nullFactory = expectThrows(
                NullPointerException.class,
                () -> NullFactoryProductBuilder.builder().ignored("x").build()
        );
        check(nullFactory.getMessage().contains("@BuilderFactory returned null"),
                "null factory result is rejected directly");

        String[] genericInput = {"a", "b"};
        GenericArray<String> genericArray = GenericArrayBuilder.<String>builder()
                .values(genericInput)
                .build();
        genericInput[0] = "changed";
        check(genericArray.values()[0].equals("a"), "generic arrays are defensively copied");

        FactoryChecked checked = FactoryCheckedBuilder.builder().value("ok").build();
        check(checked.value().equals("ok"), "checked static factory build");
        expectThrows(IOException.class,
                () -> FactoryCheckedBuilder.builder().value("fail").build());
    }

    private static void testFactoryJakartaBeanValidation() {
        ValidatedFactoryProduct.resetConstructionCount();
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ValidatedFactoryProduct value = ValidatedFactoryProductBuilder.builder()
                .value("valid")
                .build(success.validator());
        check(value.value().equals("valid"), "factory-backed Jakarta build");
        check(success.phases.equals(List.of("bean")),
                "static factory mode performs portable completed-bean validation only");
        check(ValidatedFactoryProduct.constructionCount() == 1,
                "factory-backed validated build constructs exactly once");

        ValidatedFactoryProduct.resetConstructionCount();
        ValidationRecorder failure = new ValidationRecorder(FailurePhase.BEAN);
        expectThrows(ConstraintViolationException.class,
                () -> ValidatedFactoryProductBuilder.builder()
                        .value("invalid")
                        .build(failure.validator()));
        check(ValidatedFactoryProduct.constructionCount() == 1,
                "bean validation follows static factory construction");
    }

    private static void testAllCollectionPolicies() {
        ArrayList<String> collectionInput = new ArrayList<>(List.of("collection"));
        ArrayList<String> listInput = new ArrayList<>(List.of("list"));
        ArrayList<String> sequencedCollectionInput = new ArrayList<>(List.of("sequence"));
        LinkedHashSet<String> setInput = new LinkedHashSet<>(Set.of("set"));
        LinkedHashSet<String> sequencedSetInput = new LinkedHashSet<>(List.of("sequence-set"));
        TreeSet<String> sortedSetInput = new TreeSet<>(Set.of("sorted-set"));
        TreeSet<String> navigableSetInput = new TreeSet<>(Set.of("navigable-set"));
        LinkedHashMap<String, String> mapInput = new LinkedHashMap<>(Map.of("map", "1"));
        LinkedHashMap<String, String> sequencedMapInput =
                new LinkedHashMap<>(Map.of("sequence-map", "1"));
        TreeMap<String, String> sortedMapInput = new TreeMap<>(Map.of("sorted-map", "1"));
        TreeMap<String, String> navigableMapInput =
                new TreeMap<>(Map.of("navigable-map", "1"));

        CollectionPoliciesBuilder builder = CollectionPoliciesBuilder.builder()
                .collection(collectionInput)
                .list(listInput)
                .sequencedCollection(sequencedCollectionInput)
                .set(setInput)
                .sequencedSet(sequencedSetInput)
                .sortedSet(sortedSetInput)
                .navigableSet(navigableSetInput)
                .map(mapInput)
                .sequencedMap(sequencedMapInput)
                .sortedMap(sortedMapInput)
                .navigableMap(navigableMapInput);

        // Setters snapshot immediately; mutations before build cannot leak into the builder.
        collectionInput.add("late");
        listInput.add("late");
        sequencedCollectionInput.add("late");
        setInput.add("late");
        sequencedSetInput.add("late");
        sortedSetInput.add("late");
        navigableSetInput.add("late");
        mapInput.put("late", "2");
        sequencedMapInput.put("late", "2");
        sortedMapInput.put("late", "2");
        navigableMapInput.put("late", "2");

        CollectionPolicies value = builder
                .addItem("collection-adder")
                .addListItem("list-adder")
                .addSequenceItem("sequence-adder")
                .addSetItem("set-adder")
                .addSequencedSetItem("sequence-set-adder")
                .addSortedSetItem("sorted-set-adder")
                .addNavigableSetItem("navigable-set-adder")
                .putEntry("map-adder", "2")
                .putSequencedEntry("sequence-map-adder", "2")
                .putSortedEntry("sorted-map-adder", "2")
                .putNavigableEntry("navigable-map-adder", "2")
                .build();

        check(!value.collection().contains("late"), "Collection setter snapshots input");
        check(!value.list().contains("late"), "List setter snapshots input");
        check(!value.sequencedCollection().contains("late"),
                "SequencedCollection setter snapshots input");
        check(!value.set().contains("late"), "Set setter snapshots input");
        check(!value.sequencedSet().contains("late"), "SequencedSet setter snapshots input");
        check(!value.sortedSet().contains("late"), "SortedSet setter snapshots input");
        check(!value.navigableSet().contains("late"), "NavigableSet setter snapshots input");
        check(!value.map().containsKey("late"), "Map setter snapshots input");
        check(!value.sequencedMap().containsKey("late"), "SequencedMap setter snapshots input");
        check(!value.sortedMap().containsKey("late"), "SortedMap setter snapshots input");
        check(!value.navigableMap().containsKey("late"), "NavigableMap setter snapshots input");

        expectThrows(UnsupportedOperationException.class, () -> value.collection().add("x"));
        expectThrows(UnsupportedOperationException.class, () -> value.list().add("x"));
        expectThrows(UnsupportedOperationException.class,
                () -> value.sequencedCollection().addFirst("x"));
        expectThrows(UnsupportedOperationException.class, () -> value.set().add("x"));
        expectThrows(UnsupportedOperationException.class, () -> value.sequencedSet().addFirst("x"));
        expectThrows(UnsupportedOperationException.class, () -> value.sortedSet().add("x"));
        expectThrows(UnsupportedOperationException.class, () -> value.navigableSet().pollFirst());
        expectThrows(UnsupportedOperationException.class, () -> value.map().put("x", "x"));
        expectThrows(UnsupportedOperationException.class, () -> value.sequencedMap().putFirst("x", "x"));
        expectThrows(UnsupportedOperationException.class, () -> value.sortedMap().put("x", "x"));
        expectThrows(UnsupportedOperationException.class, () -> value.navigableMap().pollFirstEntry());

        // Each build gets a separate immutable snapshot from the mutable builder state.
        CollectionPolicies first = value;
        CollectionPolicies second = builder.addListItem("after-first-build").build();
        check(!first.list().contains("after-first-build"), "built value detached from builder");
        check(second.list().contains("after-first-build"), "builder remains reusable");
    }

    private static void testCollectionCopyFromSnapshot() {
        ArrayList<String> sourceList = new ArrayList<>(List.of("source"));
        LinkedHashMap<String, String> sourceMap = new LinkedHashMap<>(Map.of("source", "1"));
        CollectionPolicies source = new CollectionPolicies(
                sourceList,
                sourceList,
                sourceList,
                new LinkedHashSet<>(sourceList),
                new LinkedHashSet<>(sourceList),
                new TreeSet<>(sourceList),
                new TreeSet<>(sourceList),
                sourceMap,
                sourceMap,
                new TreeMap<>(sourceMap),
                new TreeMap<>(sourceMap)
        );

        CollectionPoliciesBuilder copyBuilder = CollectionPoliciesBuilder.from(source);
        sourceList.add("late-source-change");
        sourceMap.put("late-source-change", "2");
        CollectionPolicies copy = copyBuilder.build();

        check(!copy.list().contains("late-source-change"), "copyFrom snapshots collections");
        check(!copy.map().containsKey("late-source-change"), "copyFrom snapshots maps");
    }

    private static void testCollectionOptOut() {
        ArrayList<String> mutable = new ArrayList<>(List.of("value"));
        ArrayDeque<String> queue = new ArrayDeque<>(List.of("queued"));
        MutableCollectionOptOut value = MutableCollectionOptOutBuilder.builder()
                .values(mutable)
                .queue(queue)
                .build();

        check(value.values() == mutable, "explicit opt-out preserves collection reference");
        check(value.queue() == queue, "explicit opt-out supports otherwise unsupported collection type");
        value.values().add("mutable");
        check(mutable.contains("mutable"), "opt-out result remains mutable");
    }

    private static void testJakartaValidationOptional() {
        ValidatedCustomer.Address address = new ValidatedCustomer.Address("Chicago");

        // OPTIONAL retains an ordinary build path for callers that deliberately validate elsewhere.
        ValidatedCustomer unvalidated = ValidatedCustomerBuilder.builder()
                .name("")
                .address(address)
                .tags(List.of())
                .build();
        check(unvalidated.name().isEmpty(), "optional mode retains unvalidated build");

        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ValidatedCustomer value = ValidatedCustomerBuilder.builder()
                .name("Ada")
                .address(address)
                .addTag("admin")
                .build(success.validator(), StrictValidation.class);

        check(success.lastObject == value, "validator receives the constructed value");
        check(success.phases.equals(List.of(
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean"
                )),
                "Jakarta validation phases execute in order");
        check(success.lastConstructorArguments.length == 3,
                "canonical constructor arguments are validated");
        @SuppressWarnings("unchecked")
        List<String> validatedTags = (List<String>) success.lastConstructorArguments[2];
        expectThrows(UnsupportedOperationException.class, () -> validatedTags.add("mutable"));
        check(success.lastGroups.length == 1
                        && success.lastGroups[0] == StrictValidation.class,
                "validation groups are forwarded exactly");
        expectThrows(UnsupportedOperationException.class, () -> value.tags().add("mutable"));

        ValidationRecorder failure = new ValidationRecorder(FailurePhase.BEAN);
        ConstraintViolationException violation = expectThrows(
                ConstraintViolationException.class,
                () -> ValidatedCustomerBuilder.builder()
                        .name("")
                        .address(address)
                        .addTag("")
                        .build(failure.validator())
        );
        check(violation.getConstraintViolations().size() == 1,
                "constraint violations are preserved");

        expectThrows(NullPointerException.class,
                () -> ValidatedCustomerBuilder.builder()
                        .name("Ada")
                        .address(address)
                        .addTag("admin")
                        .build((Validator) null));
        expectThrows(NullPointerException.class,
                () -> ValidatedCustomerBuilder.builder()
                        .name("Ada")
                        .address(address)
                        .addTag("admin")
                        .build(success.validator(), (Class<?>[]) null));
    }

    private static void testJakartaValidationRequired() {
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        expectThrows(IllegalStateException.class,
                () -> RequiredValidatedCustomerBuilder.builder().build(success.validator()));
        check(success.lastObject == null,
                "builder-required checks run before Jakarta validation");

        RequiredValidatedCustomer value = RequiredValidatedCustomerBuilder.builder()
                .name("Grace")
                .build(success.validator());
        check(value.name().equals("Grace"), "required Jakarta validation build");
    }

    private static void testJakartaValidationGeneric() {
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ValidatedGeneric<String> value = ValidatedGenericBuilder.<String>builder()
                .value("generic")
                .build(success.validator());
        check(value.value().equals("generic"), "generic validated builder");
    }

    private static void testJakartaConstructorValidationPhases() {
        ConstructorValidatedClass.resetConstructionCount();
        ValidationRecorder parameterFailure = new ValidationRecorder(FailurePhase.PARAMETERS);
        ConstraintViolationException parameterViolation = expectThrows(
                ConstraintViolationException.class,
                () -> ConstructorValidatedClassBuilder.builder()
                        .value("")
                        .build(parameterFailure.validator())
        );
        check(parameterViolation.getConstraintViolations().size() == 1,
                "constructor parameter violations are reported");
        check(ConstructorValidatedClass.constructionCount() == 0,
                "parameter violations prevent constructor side effects");
        check(parameterFailure.phases.equals(List.of("constructorParameters")),
                "parameter failure stops before construction");
        check(parameterFailure.lastConstructorArguments[0].equals(""),
                "executable validation receives the actual constructor argument");

        ConstructorValidatedClass.resetConstructionCount();
        ValidationRecorder returnFailure = new ValidationRecorder(FailurePhase.RETURN_VALUE);
        expectThrows(ConstraintViolationException.class,
                () -> ConstructorValidatedClassBuilder.builder()
                        .value("return")
                        .build(returnFailure.validator()));
        check(ConstructorValidatedClass.constructionCount() == 1,
                "return-value validation occurs after construction");
        check(returnFailure.phases.equals(List.of(
                        "constructorParameters",
                        "constructorReturnValue",
                        "bean"
                )),
                "return and bean violations are evaluated together");

        ConstructorValidatedClass.resetConstructionCount();
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ConstructorValidatedClass value = ConstructorValidatedClassBuilder.builder()
                .value("valid")
                .build(success.validator());
        check(value.value().equals("valid"), "constructor-validated class build");
        check(ConstructorValidatedClass.constructionCount() == 1,
                "successful validation constructs exactly once");
    }

    private static void testJakartaValidatedOverloadedConstructor() {
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ValidatedOverloaded value = ValidatedOverloadedBuilder.builder()
                .count(2)
                .names(new String[]{"Ada", "Grace"})
                .build(success.validator());
        check(value.count() == 2, "validated selected constructor primitive argument");
        check(value.names().length == 2, "validated selected constructor array argument");
        check(success.lastConstructorArguments[0].equals(2),
                "primitive constructor argument is boxed for executable validation");
        check(((String[]) success.lastConstructorArguments[1]).length == 2,
                "array constructor argument is forwarded");

        ValidatedOverloaded copy = ValidatedOverloadedBuilder.from(value)
                .count(3)
                .build(success.validator());
        check(copy.count() == 3, "validated copy builder uses selected constructor");
    }

    private static void testJakartaValidationCheckedException() throws IOException {
        ValidationRecorder success = new ValidationRecorder(FailurePhase.NONE);
        ValidatedCheckedConstruction value = ValidatedCheckedConstructionBuilder.builder()
                .value("ok")
                .build(success.validator());
        check(value.value().equals("ok"), "validated build preserves checked throws");

        expectThrows(IOException.class,
                () -> ValidatedCheckedConstructionBuilder.builder()
                        .value("io-failure")
                        .build(success.validator()));
    }

    private enum FailurePhase {
        NONE,
        PARAMETERS,
        RETURN_VALUE,
        BEAN
    }

    private static final class ValidationRecorder implements InvocationHandler {
        private final FailurePhase failurePhase;
        private final List<String> phases = new ArrayList<>();
        private Object lastObject;
        private Object[] lastConstructorArguments = new Object[0];
        private Class<?>[] lastGroups = new Class<?>[0];

        private ValidationRecorder(FailurePhase failurePhase) {
            this.failurePhase = failurePhase;
        }

        private Validator validator() {
            return (Validator) Proxy.newProxyInstance(
                    Validator.class.getClassLoader(),
                    new Class<?>[]{Validator.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "validate" -> validateBean(args);
                case "forExecutables" -> executableValidator();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ValidationRecorder";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        }

        private Object validateBean(Object[] args) {
            phases.add("bean");
            this.lastObject = args[0];
            this.lastGroups = ((Class<?>[]) args[1]).clone();
            return violations(FailurePhase.BEAN);
        }

        private ExecutableValidator executableValidator() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "validateConstructorParameters" -> validateConstructorParameters(args);
                case "validateConstructorReturnValue" -> validateConstructorReturnValue(args);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ExecutableValidationRecorder";
                default -> throw new UnsupportedOperationException(method.toString());
            };
            return (ExecutableValidator) Proxy.newProxyInstance(
                    ExecutableValidator.class.getClassLoader(),
                    new Class<?>[]{ExecutableValidator.class},
                    handler
            );
        }

        private Object validateConstructorParameters(Object[] args) {
            phases.add("constructorParameters");
            this.lastConstructorArguments = ((Object[]) args[1]).clone();
            this.lastGroups = ((Class<?>[]) args[2]).clone();
            return violations(FailurePhase.PARAMETERS);
        }

        private Object validateConstructorReturnValue(Object[] args) {
            phases.add("constructorReturnValue");
            this.lastObject = args[1];
            this.lastGroups = ((Class<?>[]) args[2]).clone();
            return violations(FailurePhase.RETURN_VALUE);
        }

        private Set<?> violations(FailurePhase phase) {
            return failurePhase == phase ? Set.of(constraintViolation()) : Set.of();
        }

        private ConstraintViolation<?> constraintViolation() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "TestConstraintViolation";
                default -> null;
            };
            return (ConstraintViolation<?>) Proxy.newProxyInstance(
                    ConstraintViolation.class.getClassLoader(),
                    new Class<?>[]{ConstraintViolation.class},
                    handler
            );
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(Class<T> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (type.isInstance(throwable)) {
                return type.cast(throwable);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + throwable, throwable);
        }
        throw new AssertionError("Expected " + type.getName() + " but nothing was thrown");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
