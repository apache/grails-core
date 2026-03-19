# Known Issues in Hibernate 7 Migration

### 1. Float Precision Mismatch (H2 and PostgreSQL)
**Symptoms:**
- `org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL`
- H2 Error: `Precision ("64") must be between "1" and "53" inclusive`
- PostgreSQL Error: `ERROR: precision for type float must be less than 54 bits`

**Description:**
Hibernate 7's default mapping for `java.lang.Double` properties on H2 (2.x) and PostgreSQL (16+) generates DDL with `float(64)`. Both databases reject this, as the maximum precision for the `float`/`double precision` type is 53 bits.

**Workaround:**
Explicitly set `precision` in the domain mapping (e.g., `amount precision: 10`) or use `sqlType: 'double precision'`.

---

### 2. Generator Initialization Failure (NPE)
**Symptoms:**
- `java.lang.NullPointerException` at `org.hibernate.id.enhanced.SequenceStyleGenerator.generate`
- Message: `Cannot invoke "org.hibernate.id.enhanced.DatabaseStructure.buildCallback(...)" because "this.databaseStructure" is null`

**Description:**
When a table creation fails (e.g., due to the Float Precision Mismatch issue), the `SequenceStyleGenerator` is not properly initialized. Subsequent attempts to persist an entity trigger an NPE instead of a descriptive error because Hibernate 7 does not check the state of the `databaseStructure` before use.

---

### 3. ByteBuddy Proxy Initialization
**Symptoms:**
- Proxies are initialized prematurely during `getId()`, `isDirty()`, or Groovy truthiness checks (`if (proxy)`).
- `Hibernate.isInitialized(proxy)` returns `true` when it should be `false`.

**Description:**
Hibernate 7's `ByteBuddyInterceptor.intercept()` does not distinguish between actual property access and Groovy's internal metadata calls (like `getMetaClass()`). Any interaction with the proxy object triggers the interceptor, which hydrates the instance. This breaks lazy loading expectations in Grails and dynamic Groovy environments.

---

### 4. JpaFromProvider NullPointerException (Resolved)
**Symptoms:**
- `NullPointerException` during path resolution in Criteria queries.

**Description:**
Occurs when a query projection references an association path that has not been joined in the `FROM` clause.

**Action Taken:**
Updated `JpaFromProvider` to scan projections and automatically create hierarchical `LEFT JOIN`s for discovered association paths.
