# Hibernate 7 Migration Progress Report - GORM Core

## Overview
This document summarizes the approaches taken, challenges encountered, and future steps for upgrading the GORM Hibernate implementation to Hibernate 7.





## Hibernate 7 Key Constraints & Best Practices

### Identifier Generators
- **Avoid Deprecated `configure`:** Do **not** use the three-parameter `configure(Type, Properties, ServiceRegistry)` method in `IdentifierGenerator` (or its subclasses like `SequenceStyleGenerator`). It is marked for removal in Hibernate 7.
- **Prefer modern initialization:** Use the `GeneratorCreationContext` provided by `setCustomIdGeneratorCreator` to perform initialization. If a manual call to `configure` is absolutely necessary for legacy bridge logic, be aware it may trigger warnings or fail in future versions.

## Challenges & Failures

### 1. Proxy Initialization Behavior
- **Issue:** In `Hibernate7GroovyProxySpec`, `Location.proxy(id)` returns an object that is already initialized (`Hibernate.isInitialized(location) == true`), even after clearing the session.
- **Attempts:** Tried `session.getReference()`, `session.byId().getReference()`, and using fresh sessions.
- **Status:** Ongoing investigation. Debugging indicates that Hibernate 7's bytecode enhancement or session management might be reporting Groovy proxies as initialized even when they haven't fetched their target.

## Strategy for GrailsDomainBinder Refactoring

### Goal
To decompose the monolithic `GrailsDomainBinder` (over 2000 lines) into smaller, specialized binder classes within the `org.grails.orm.hibernate.cfg.domainbinding` package. This improves maintainability and enables unit testing of specific binding logic.

### Refactoring Pattern
Each new binder should follow this structure:
1.  **Dependencies as Fields:** Define `private final` fields for all dependent binders and utility classes.
2.  **Public Constructor:** A constructor that takes essential state (e.g., `PersistentEntityNamingStrategy`) and initializes simple dependencies internally.
3.  **Protected Constructor for Testing:** A second constructor that accepts all dependencies as arguments. This allows unit tests to inject mocks for all collaborating classes.
4.  **Core Method:** A public method that contains the logic previously held in `GrailsDomainBinder` (e.g., `bindCollectionSecondPass`).

### Refactored Binders
- [x] `BasicValueIdCreator`: Handles the creation of `BasicValue` for identifiers. It uses Hibernate 7's `setCustomIdGeneratorCreator` to map GORM generator names to modern `Generator` implementations.

#### Implementation
```java
    private void initializeGeneratorFactories() {
        generatorFactories.put("identity", (context, mappedId) -> new GrailsIdentityGenerator(context, mappedId));

        BiFunction<GeneratorCreationContext, Identity, Generator> sequenceFactory = (context, mappedId) -> new GrailsSequenceStyleGenerator(context, mappedId);
        generatorFactories.put("sequence", sequenceFactory);
        generatorFactories.put("sequence-identity", sequenceFactory);

        generatorFactories.put("increment", (context, mappedId) -> new IncrementGenerator());
        generatorFactories.put("uuid", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("uuid2", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("assigned", (context, mappedId) -> new Assigned());
        generatorFactories.put("table", (context, mappedId) -> new TableGenerator());
        generatorFactories.put("enhanced-table", (context, mappedId) -> new TableGenerator());
        generatorFactories.put("hilo", (context, mappedId) -> new SequenceStyleGenerator());
    }
```

#### GrailsSequenceStyleGenerator Implementation
```java
public class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {
    public GrailsSequenceStyleGenerator(GeneratorCreationContext context, org.grails.orm.hibernate.cfg.Identity mappedId) {
        var generatorProps = Optional.ofNullable(mappedId).map(Identity::getProperties).orElse(new Properties());
        // Use 2-parameter configure from IdentifierGenerator / SequenceStyleGenerator
        super.configure(context, generatorProps);
        this.registerExportables(context.getDatabase());
    }
}
```

#### Test Status (`SequenceGeneratorsSpec`)
| Generator | Status | Error (if FAILED) |
| :--- | :--- | :--- |
| `identity` | [x] PASS | |
| `native` | [x] PASS | |
| `uuid` | [x] PASS | |
| `assigned` | [x] PASS | |
| `sequence` | [x] PASS | |
| `table` | [x] PASS | |
| `increment` | [ ] POSTPONED | `Table "org.grails.orm.hibernate.cfg.domainbinding.EntityWithIncrement" not found` |

### GrailsIncrementGenerator Fix Strategy (Postponed)

#### Problem
The `increment` generator fails because:
1.  **Quoted Table Name:** Hibernate 7 appears to be quoting the table name which defaults to the FQN, and H2 cannot find it.
2.  **Initialization:** In Hibernate 7, `IncrementGenerator` needs explicit initialization of its SQL context to avoid NPEs.

#### Current State
Partial work was done to address initialization and table name resolution, but the test still fails with table-not-found errors in H2. Further investigation into how Hibernate 7 resolves and quotes table names for the `increment` generator is required.

- [x] `SimpleIdBinder`: Orchestrates the binding of simple identifiers by coordinating `BasicValueIdCreator`, `SimpleValueBinder`, and `PropertyBinder`.
- [x] `PropertyBinder`: Binds `PersistentProperty` to Hibernate `Property`, handling cascade behaviors, access strategies (including Groovy traits), and lazy loading configurations.
- [x] `ManyToOneBinder`: Specialized binder for many-to-one associations, handling composite identifiers and circularity.
- [x] `SimpleValueBinder`: Handles the binding of simple values (columns, types, etc.) to Hibernate `SimpleValue`.

### Testing Strategy
Unit tests should be created for each new binder class (e.g., `CollectionBinderSpec`). These tests should:
- Use the protected constructor to inject mocks.
- Verify interactions with dependent binders using Spock's `Mock()` and `1 * ...` syntax.
- Ensure that the complex logic of `GrailsDomainBinder` is covered by isolated unit tests rather than relying solely on integration tests.

## Future Steps

1.  **Resolve Proxy Initialization:** Determine why proxies are returning as initialized in `Hibernate7GroovyProxySpec`. Investigate if Hibernate 7's bytecode enhancement or ByteBuddy factory settings are interfering.
3.  **Address `Session.save()` usage:** Systematically find and replace `save()` with `persist()` or `merge()` across the codebase and TCK where direct Hibernate session access is used.
