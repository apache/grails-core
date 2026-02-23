# Hibernate 7 / Grails 8 Upgrade Issues

Extracted from the full Grails 8 modernisation roadmap (`UPGRADE.md`), this document covers only the Hibernate-specific issues.

---


---

## 1. Module Consolidation: Remove `grails-data-hibernate6`

The `8.0.x-hibernate7` branch ships three Hibernate modules. Only two should reach GA:

| Module | Hibernate Version | Binder Architecture | Gaps | Ship in Grails 8? |
|--------|-------------------|---------------------|------|-------------------|
| `grails-data-hibernate5` | 5.6-jakarta | Monolithic `GrailsDomainBinder` (to be refactored) | None (production) | **Yes** — legacy path |
| `grails-data-hibernate7` | 7.2 | Fully decomposed `domainbinding/binder/` hierarchy + generators + entity hierarchy | None | **Yes** — modern path |

`grails-data-hibernate7` supersedes all `grails-data-hibernate6` work — the 56 files unique to `hibernate6` are the old binder pattern that `hibernate7` replaced with 176 new files in the decomposed hierarchy.
- `hibernate6` still has known gaps (multitenancy, proxy support) that `hibernate7` resolved.
- Maintaining 3 modules instead of 2 triples the surface area for Hibernate API changes.

**Removal plan:**
3. Verify no other modules reference `grails-data-hibernate6` in their build files.

---

## 2. Binder Refactoring (Apply to Both Versions)

The `8.0.x-hibernate7` branch decomposes the monolithic `GrailsDomainBinder` into a proper hierarchy in `grails-data-hibernate7`. **This refactoring must also be applied to `grails-data-hibernate5`.**

**New binder hierarchy in `grails-data-hibernate7`:**
- `RootBinder`, `SubClassBinder`, `SubclassMappingBinder`, `DiscriminatorPropertyBinder`
- `ComponentBinder`, `CollectionSecondPassBinder`, `DependentKeyValueBinder`
- `UnidirectionalOneToManyBinder`, `SimpleValueColumnBinder`, `PrimaryKeyValueCreator`
- `HibernateCriteriaBuilder` refactored with `CriteriaMethodInvoker` and `CriteriaMethods` enum
- `GrailsHibernatePersistentEntity` and `GrailsHibernatePersistentProperty` hierarchy enriched
- Each binder has its own Spock spec (testable in isolation)

Having `grails-data-hibernate5` use a monolithic `GrailsDomainBinder` while `grails-data-hibernate7` has proper binder separation would make long-term maintenance significantly harder.

---

## 3. Version Selection Mechanism

Users must be able to choose their Hibernate version at dependency resolution time without classpath conflicts.

**Proposed approach — Maven classifier + Gradle `requireCapability`:**

```groovy
// Option A: Hibernate 5.6-jakarta (legacy, default for migration)
implementation 'org.apache.grails:grails-data-hibernate:8.0.0:hibernate56'

// Option B: Hibernate 7.2 (modern, recommended for new projects)
implementation 'org.apache.grails:grails-data-hibernate:8.0.0:hibernate72'
```

Or via Gradle feature variants:

```groovy
dependencies {
    implementation('org.apache.grails:grails-data-hibernate:8.0.0') {
        requireCapability 'org.apache.grails:grails-data-hibernate-7.2'
    }
}
```

**Goals:**
- No classpath conflicts (only one Hibernate version on the classpath at a time).
- BOM/platform support for transitive Hibernate version alignment.
- Clear migration path: start with 5.6, switch to 7.2 when ready.
- Grails Forge can offer the choice at project creation time.

---

## 4. Liquibase / Database Migration Plugin Concerns

### Hibernate 5.6 path
No license issue. The Legacy Database Migration plugin pins to Liquibase 4.27.0 and `liquibase-hibernate5`, both Apache 2.0 licensed. This continues to work as in Grails 7.

### Hibernate 7.2 path
PR [liquibase/liquibase-hibernate#844](https://github.com/liquibase/liquibase-hibernate/pull/844) Database Migration plugin is for Hibernate 7 support to `liquibase-hibernate`, and it targets Liquibase 4.29.2, , both Apache 2.0 licensed.


---

## 5. Hibernate Entity Static Trait — Groovy 5 Joint Compilation Failure

**Context:** `HibernateEntity` (in `grails-data-hibernate5-core`) has 5 static methods with generic return types. `HibernateMappingContext.java` in the same module directly imports the trait. Under Groovy 5, the stub generator produces invalid Java:

```java
// Generated stub (INVALID Java)
@groovy.transform.Generated()
static java.util.List<D> findAllWithSql(java.lang.CharSequence sql);
//                    ^ ERROR: non-static type variable D cannot be
//                      referenced from static context
```

In Java, a class-level type parameter (`D`) cannot be referenced from a static method. The Groovy trait works at runtime but the stub generator doesn't account for this.

**Comparison with other traits:**

| Trait | Module | Static Methods | Java Files Import It? | Result in Groovy 5 |
|-------|--------|----------------|-----------------------|-------------------|
| `GormEntity` | grails-datamapping-core | 87 | No | Works |
| `HibernateEntity` | grails-data-hibernate5-core | 5 | **Yes** (`HibernateMappingContext.java`) | **Fails** |
| `MongoEntity` | grails-data-mongodb | 18 | No | Works |

**Decision options:**

| Approach | Description | Trade-off |
|----------|-------------|-----------|
| **Convert Java class to Groovy** | Rewrite `HibernateMappingContext.java` as `.groovy` so it goes through Groovy compilation | Slower Groovy compilation |

---

## 6. `HibernateMappingBuilder` — `@CompileStatic` Compatibility (Groovy 5)

`HibernateMappingBuilder` uses `methodMissing` to handle arbitrary domain property names (e.g. `title`, `name`, `dateCreated`). Under Groovy 5, `@CompileStatic` on the class means the compiler cannot see `methodMissing`-dispatched calls as valid. Required changes:

1. **Replace `methodMissing` for common DSL methods with explicit typed methods** — `methodMissing` remains as the generic fallback but typed DSL entrypoints must exist for the compiler.
2. **Add `@DelegatesTo` to all closure-accepting methods** — allows the Groovy 5 static type checker to validate the DSL body passed by callers.
3. **`Entity.getSort()` type resolution** — `Entity<P>` declares `Object getSort()` returning `defaultSort`; `Mapping` declares a `SortConfig sort` field. Under `@CompileStatic` in `HibernateMappingBuilder`, `mapping.getSort()` resolves to `Object`. Fix: explicit cast `(SortConfig) mapping.getSort()`.
4. **`PropertyConfig.typeParams` (`Properties` vs `Map`)** — `typeParams` is typed as `java.util.Properties`; assigning a raw `Map` fails `@CompileStatic`. Use `Properties.put(Object, Object)` (not `setProperty`, which stringifies values and breaks integer-preserving type params).
5. **`setUnique` overload resolution** — `Property.setUnique` has three overloads: `boolean`, `String` (uniqueness group), and `List<String>`. `@CompileStatic` requires explicit `instanceof` guards for each overload.
6. **`importFrom` handling** — The `importFrom` constraint keyword (used in `static constraints`) must remain handled in `methodMissing`, delegating to `ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy()`. Removing it causes `importFrom` constraints to be silently ignored at mapping time, breaking column-length DDL generation.
All done in `HibernateMappingBuilder`.

---

## 7. Why Hibernate 7 Matters

- **Jakarta Persistence 3.2** compliance (Jakarta EE 11).
- **Spring Boot 4** ships with Hibernate 7.2 as its default ORM provider.
- Hibernate 7 drops many deprecated 5.x APIs that GORM currently wraps.
- Better SQL generation, stateless session improvements, annotation processor support.

---

## 8. Recommended Action for `grails-data-hibernate7`

Do **not** drop Hibernate 5.6 support in 8.0.0. Ship both:

| Path | Module | Status |
|------|--------|--------|
| Hibernate 5.6-jakarta | `grails-data-hibernate5` | Safe default for migration from Grails 7 |
| Hibernate 7.2 | `grails-data-hibernate7` | Modern target; recommended for new projects |


**Priority**: CRITICAL  
**Effort**: Very High (binder refactoring must be applied to both versions; module consolidation required)


### 2.6 GORM Query Safety Audit

**Current state**: GORM's HQL/`@Query` support accepts string interpolation in queries. Dynamic finders are safe by design (parameterized). But `executeQuery()`, `executeUpdate()`, and `@Query` with GString interpolation can produce SQL injection.

**Potentially for Grails 8**:
- Add compile-time warning for GString-interpolated HQL queries (AST transform or type checking extension)
- Document safe query patterns prominently
- Consider deprecating `executeQuery(String)` in favor of parameterized-only API
- Audit all internal GORM query construction for injection vectors

**Priority**: **P2** - SQL injection is OWASP #3. Framework should make the safe path the easy path.
**Effort**: Medium

### 1.4 Static Compilation Coverage Expansion

**Current state in grails-core/src/main**: 45 out of 73 Groovy files (62%) have `@CompileStatic` or `@GrailsCompileStatic`.

Without `@CompileStatic`, every Groovy method call goes through dynamic dispatch:
- MetaClass lookup per call
- No JIT inlining
- Indy call site caching defeated by metaclass changes (ties into 1.1)
- GraalVM native image impossible for dynamically-dispatched code

**Required work**:
1. Audit all `.groovy` files in `src/main` across every module for `@CompileStatic` eligibility

### 2.6 GORM Query Safety Audit

**Current state**: GORM's HQL/`@Query` support accepts string interpolation in queries. Dynamic finders are safe by design (parameterized). But `executeQuery()`, `executeUpdate()`, and `@Query` with GString interpolation can produce SQL injection.

**Potentially for Grails 8**:
- Add compile-time warning for GString-interpolated HQL queries (AST transform or type checking extension)
- Document safe query patterns prominently
- Consider deprecating `executeQuery(String)` in favor of parameterized-only API
- Audit all internal GORM query construction for injection vectors

**Priority**: **P2** - SQL injection is OWASP #3. Framework should make the safe path the easy path.
**Effort**: Medium

**8.1+ scope** (requires Groovy 5 features, deeper changes):
- Replace GORM dynamic finders with AST-generated static methods
- Replace `NamedCriteriaProxy.methodMissing` with generated methods
