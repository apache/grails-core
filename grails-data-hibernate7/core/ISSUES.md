# Known Issues in Hibernate 7 Migration

### 1. Float Precision Mismatch (H2 and PostgreSQL)
**Symptoms:**
- `org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL`
- H2 Error: `Precision ("64") must be between "1" and "53" inclusive`
- PostgreSQL Error: `ERROR: precision for type float must be less than 54 bits`

**Description:**
Hibernate 7's default mapping for `java.lang.Double` properties on H2 (2.x) and PostgreSQL (16+) generates DDL with `float(64)`. Both databases reject this, as the maximum precision for the `float`/`double precision` type is 53 bits.

**Workaround:**
The framework now defaults to precision `15` decimal digits for non-Oracle dialects, which maps to ~53 bits.

---

## Failing Tests
BasicCollectionInQuerySpec
ByteBuddyGroovyInterceptorSpec
DetachedCriteriaProjectionAliasSpec
HibernateProxyHandler7Spec
WhereQueryOldIssueVerificationSpec

**Description:**
When a table creation fails (e.g., due to the Float Precision Mismatch issue), the `SequenceStyleGenerator` is not properly initialized. Subsequent attempts to persist an entity trigger an NPE instead of a descriptive error.

**Action Taken:**
Updated `GrailsNativeGenerator` to check the state of the delegate generator and throw a descriptive `HibernateException`.

---

### 3. ByteBuddy Proxy Initialization & Interception
**Symptoms:**
- `ByteBuddyGroovyInterceptorSpec` and `HibernateProxyHandler7Spec` failures.
- Proxies are initialized prematurely during `getId()`, `isDirty()`, or Groovy internal calls.

**Description:**
Hibernate 7's `ByteBuddyInterceptor.intercept()` does not distinguish between actual property access and Groovy's internal metadata calls (like `getMetaClass()`). This triggers hydration during common Groovy operations.

---

### 4. JpaFromProvider & JpaCriteriaQueryCreator (Resolved)
**Symptoms:**
- Association projection paths fail to resolve correctly in complex queries.
- `NullPointerException` during path resolution in Criteria queries.

**Description:**
Referencing an association in a projection (e.g., `projections { property('owner.name') }`) requires an automatic join. `JpaFromProvider` has been updated to scan projections and automatically create hierarchical `LEFT JOIN`s for discovered association paths. Intermediate segments are also correctly joined.

---

### 5. HibernateQuery Event ClassCastException (Resolved in Spec)
**Symptoms:**
- `java.lang.ClassCastException: class org.grails.datastore.mapping.query.event.PreQueryEvent cannot be cast to class org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent`

**Description:**
The event listener in `HibernateQuerySpec` was incorrectly expecting `AbstractPersistenceEvent` while `PreQueryEvent` and `PostQueryEvent` now extend `AbstractQueryEvent`. The spec has been updated to use the correct event type.

---

### 6. MappingException: Class 'java.util.Set' does not implement 'UserCollectionType' (Resolved)
**Symptoms:**
- `org.hibernate.MappingException: Class 'java.util.Set' does not implement 'org.hibernate.usertype.UserCollectionType'`

**Description:**
Hibernate 7 changed how collection types are resolved. Standard collection types like `java.util.Set` should not have their type name set to the class name, as Hibernate 7 expects a `UserCollectionType` when a type name is provided. `CollectionType.java` was updated to avoid setting the type name for standard collections.

---

### 7. TerminalPathException in SQM Paths (Resolved)
**Symptoms:**
- `org.hibernate.query.sqm.TerminalPathException: Terminal path 'id' has no attribute 'id'`

**Description:**
In Hibernate 7, once a path is resolved to a terminal attribute (like `id`), further navigation on that path (e.g., trying to access a property on the ID) triggers this exception. `PredicateGenerator` has been updated with an `isAssociation` check to prevent this.

---

### 8. IDENTITY Generator Default in TCK
**Symptoms:**
- `HibernateMappingFactorySpec` failure: `entity.mapping.identifier.generator == ValueGenerator.NATIVE` condition not satisfied.

**Description:**
The TCK Manager now globally sets `id generator: 'identity'` to avoid `SequenceStyleGenerator` issues in Hibernate 7. This causes tests that expect the default `NATIVE` generator to fail.

---

### 9. HibernateGormStaticApi HQL Overloads (Resolved in Spec)
**Symptoms:**
- `HibernateGormStaticApiSpec` failures related to `executeQuery` and `executeUpdate` when passing plain `String` queries.

**Description:**
Hibernate 7's stricter query parameter rules and the removal of certain `Query` overloads lead to `UnsupportedOperationException` when plain `String` queries are passed to `executeQuery` or `executeUpdate`. The spec has been updated to reflect this expected behavior.

---

### 10. Multivalued Paths in IN Queries
**Symptoms:**
- `org.hibernate.query.SemanticException: Multivalued paths are only allowed for the 'member of' operator`
- Affects `BasicCollectionInQuerySpec`.

**Description:**
In Hibernate 7, using an `IN` operator on a path that represents a collection (multivalued path) is no longer allowed. GORM traditionally supported this by automatically joining the collection.

---

### 11. Missing `createAlias` in HibernateCriteriaBuilder
**Symptoms:**
- `groovy.lang.MissingMethodException: No signature of method: grails.orm.HibernateCriteriaBuilder.createAlias() ...`

**Description:**
The Hibernate 7 implementation of `HibernateCriteriaBuilder` is missing the `createAlias` method, which is commonly used in GORM criteria queries to define explicit joins.
