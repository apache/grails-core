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

### 2. Generator Initialization Failure (NPE)
**Symptoms:**
- `java.lang.NullPointerException` at `org.hibernate.id.enhanced.SequenceStyleGenerator.generate`
- Message: `Cannot invoke "org.hibernate.id.enhanced.DatabaseStructure.buildCallback(...)" because "this.databaseStructure" is null`

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

### 4. JpaFromProvider & JpaCriteriaQueryCreator (Joins and Aliases)
**Symptoms:**
- `NullPointerException: Cannot invoke "jakarta.persistence.criteria.Join.alias(String)" because "table" is null`
- Association projection paths fail to resolve correctly in complex queries.

**Description:**
Referencing an association in a projection (e.g., `projections { property('owner.name') }`) requires an automatic join that wasn't previously necessary or was handled differently. The fix in `JpaFromProvider` requires robust mock handling in tests to avoid NPEs during alias assignment.

---

### 5. HibernateQuery Event ClassCastException
**Symptoms:**
- `java.lang.ClassCastException: class org.grails.datastore.mapping.query.event.PreQueryEvent cannot be cast to class org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent`

**Description:**
The event listener in `HibernateQuerySpec` incorrectly expects `AbstractPersistenceEvent` while `PreQueryEvent` and `PostQueryEvent` now extend `AbstractQueryEvent`.

---

### 6. MappingException: Class 'java.util.Set' does not implement 'UserCollectionType'
**Symptoms:**
- `org.hibernate.MappingException: Class 'java.util.Set' does not implement 'org.hibernate.usertype.UserCollectionType'`
- Affects `BasicCollectionInQuerySpec`.

**Description:**
Hibernate 7 changed how collection types are resolved. Some tests using `hasMany` with default collection types are failing because Hibernate 7 expects a specific `UserCollectionType` implementation when a custom type is inferred or explicitly mapped.

---

### 7. TerminalPathException in SQM Paths
**Symptoms:**
- `org.hibernate.query.sqm.TerminalPathException: Terminal path 'id' has no attribute 'id'`
- Affects `PredicateGeneratorSpec` and `WhereQueryBugFixSpec`.

**Description:**
In Hibernate 7, once a path is resolved to a terminal attribute (like `id`), further navigation on that path (e.g., trying to access a property on the ID) triggers this exception. This affects how GORM constructs subqueries and criteria filters.

---

### 8. IDENTITY Generator Default in TCK
**Symptoms:**
- `HibernateMappingFactorySpec` failure: `entity.mapping.identifier.generator == ValueGenerator.NATIVE` condition not satisfied.

**Description:**
The TCK Manager now globally sets `id generator: 'identity'` to avoid `SequenceStyleGenerator` issues in Hibernate 7. This causes tests that expect the default `NATIVE` generator to fail.

---

### 9. HibernateGormStaticApi HQL Overloads
**Symptoms:**
- `HibernateGormStaticApiSpec` failures related to `executeQuery` and `executeUpdate`.

**Description:**
Hibernate 7's stricter query parameter rules and the removal of certain `Query` overloads require that HQL strings be handled carefully, especially when mixing positional and named parameters or passing GORM-specific options (like `flushMode`).
