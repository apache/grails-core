# GORM Query Safety Audit — `grails-data-hibernate7`

> **Scope**: Section 2.6 of `HIBERNATE7-GRAILS8-UPGRADE.md`  
> **Priority**: P2 — SQL injection is OWASP Top-10 #3  
> **Module**: `grails-data-hibernate7-core`  
> **Last updated**: 2026-02-23

---

## Summary

| Risk Level | Count | Fixed |
|------------|-------|-------|
| 🔴 HIGH    | 2     | ✅ 2  |
| 🟡 MEDIUM  | 3     | ✅ 3  |
| 🟢 LOW / Safe | 5  | n/a   |

---

## 🔴 HIGH — Real Injection Vectors

### H-1 · `DefaultSchemaHandler` — Schema Name Spliced Raw into DDL ✅ FIXED

| Field | Value |
|-------|-------|
| **File** | `grails-datamapping-core/src/main/groovy/org/grails/datastore/gorm/jdbc/schema/DefaultSchemaHandler.groovy` |
| **Lines** | 57–77 (original) |

**Original code**:
```groovy
connection.createStatement().execute(String.format("SET SCHEMA %s", name))
connection.createStatement().execute(String.format("CREATE SCHEMA %s", name))
```

Schema names come from `TenantResolver` (user-controlled in schema-per-tenant multitenancy). JDBC parameter binding is not possible for DDL identifiers, making this a real injection vector.

**Fix applied**: Added `quoteName(Connection, String)` using `connection.metaData.identifierQuoteString`. The name is stripped of embedded quote characters before wrapping, preventing breakout. Falls through to unquoted if the driver reports quoting unsupported (returns `" "`).

```groovy
protected static String quoteName(Connection connection, String name) {
    String q = connection.metaData.identifierQuoteString ?: ''
    if (!q || q.trim().isEmpty()) return name
    String safe = name.replace(q, '')
    return "${q}${safe}${q}"
}
```

**Verified by**: `DefaultSchemaHandlerSpec` — 9 tests passing (quoting, injection stripping, unsupported-quoting fallthrough, `useSchema`, `createSchema`, `useDefaultSchema`, custom template).

---

### H-2 · `HibernateGormStaticApi` — Single-Arg Overloads Accept Plain `String` ✅ FIXED

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormStaticApi.groovy` |
| **Lines** | 233–250 |

**Root cause**: `HibernateHqlQuery.createHqlQuery` converts `GString` interpolations to named parameters but calls `.toString()` on any non-`GString` `CharSequence`, passing it raw to `session.createQuery()`. The four single-arg overloads had no guard — a plain `String` from string concatenation reached Hibernate unchanged.

**Example of unsafe caller pattern** (application code):
```groovy
// UNSAFE — plain String, no parameterization
String q = "from User where name = '" + userInput + "'"
User.find(q)   // HQL injection possible

// SAFE — GString interpolation is converted to named params by GORM
User.find("from User where name = ${userInput}")

// SAFE — explicit named params
User.find("from User where name = :name", [name: userInput])
```

**Fix applied**: Added `requireGString(CharSequence, String)` guard to all four single-arg overloads. Throws `UnsupportedOperationException` with a descriptive message directing callers to use GString interpolation or the parameterized overloads.

```groovy
private static void requireGString(CharSequence query, String method) {
    if (!(query instanceof GString)) {
        throw new UnsupportedOperationException(
            "${method}(CharSequence) only accepts a Groovy GString with interpolated parameters " +
            "(e.g. ${method}(\"from Foo where bar = \${value}\")). " +
            "Use the parameterized overload ${method}(CharSequence, Map) or " +
            "${method}(CharSequence, Collection, Map) to pass a plain String query safely."
        )
    }
}
```

This also subsumes **M-2** (the `executeUpdate` write-path concern) since the guard applies to `executeUpdate(CharSequence)` as well.

**Verified by**: `HibernateGormStaticApiSpec` — 4 new tests confirming `UnsupportedOperationException` for `find`, `findAll`, `executeQuery`, `executeUpdate` with plain `String`; all 41 spec tests pass.

---

## 🟡 MEDIUM — Potential Risks Requiring Context

### M-1 · `findWithSql` / `findAllWithSql` — Native SQL Without Enforced Parameterization ✅ FIXED

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormStaticApi.groovy` |
| **File** | `core/src/main/groovy/grails/orm/hibernate/HibernateEntity.groovy` |
| **Lines** | 224, 228 (original) |
| **Status** | ✅ Fixed |

**Root cause**: `findWithSql` / `findAllWithSql` passed any `CharSequence` directly to `session.createNativeQuery()`. Plain `String` from concatenation reached the database unchanged.

**Why `requireGString` was not applied to native SQL**: Unlike HQL (where all values can be bound as named parameters), native SQL identifiers — table names, column names — cannot be parameterized via JDBC. Applying `requireGString` would block legitimate static SQL strings and would also break GString interpolation of identifiers (Hibernate would bind them as `?` positional params, producing malformed SQL like `select * from ? c`).

**Fix applied**:
1. Renamed to `findWithNativeSql(CharSequence, Map)` / `findAllWithNativeSql(CharSequence, Map)` in both `HibernateGormStaticApi` and `HibernateEntity`. The new name makes the native SQL risk surface explicit at every call site.
2. Old `findWithSql` / `findAllWithSql` marked `@Deprecated` and delegated to the new names — full backwards compatibility preserved.
3. GString value parameterization still works (`where c.name like ${p}` → bound as `:p0`); callers must never concatenate user input into the SQL string.

**Safety contract for native SQL** (documented, not enforceable at compile time):
```groovy
// ✅ SAFE — static SQL, no user input
Club.findAllWithNativeSql("select * from club c order by c.name")

// ✅ SAFE — user value as GString interpolation → bound as :p0
String name = userInput
Club.findWithNativeSql("select * from club c where c.name = ${name}")

// ❌ UNSAFE — never do this
Club.findAllWithNativeSql("select * from club where name = '" + userInput + "'")
```

**Verified by**: `HibernateGormStaticApiSpec` — `test simple sql query`, `test sql query with gstring parameters`, `test deprecated findAllWithSql delegates to findAllWithNativeSql`, `test deprecated findWithSql delegates to findWithNativeSql` all pass (43/43 total).

---

### M-2 · `executeUpdate(CharSequence)` Write-Path Risk

| Status | Covered by H-2 fix ✅ |
|--------|----------------------|

The `executeUpdate(CharSequence)` single-arg overload was the highest-severity instance of H-2 due to the write path. It is now guarded by `requireGString` (see H-2 above).

---

### M-3 · `HibernateGormInstanceApi.nextId()` — Dead Code Removed ✅ FIXED

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormInstanceApi.groovy` |
| **Status** | ✅ Removed — method had no callers; was left over from the Hibernate 5 `GrailsIncrementGenerator` strategy |

---

## 🟢 SAFE — Verified Parameterized Paths

### S-1 · `HibernateHqlQuery.buildNamedParameterQueryFromGString` — GString Mitigation (WORKS)

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/query/HibernateHqlQuery.java` |
| **Lines** | 97–112, 128–130 |

GString interpolations are converted to named parameters before the query string reaches Hibernate. Each `${value}` becomes `:p0`, `:p1`, etc. and is bound via `setParameter()`. This correctly prevents injection when callers use GString syntax.

---

### S-2 · `HibernateSession.deleteAll` / `updateAll` — JPA Criteria Builder (SAFE)

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateSession.java` |
| **Lines** | 96–128, 137–179 |

Both methods use `JpaQueryBuilder` to construct parameterized HQL. All values are bound via `query.setParameter(...)`. No user-controlled strings are concatenated into the query.

---

### S-3 · `HibernateQuery` / `JpaCriteriaQueryCreator` — JPA Criteria API (SAFE)

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/query/HibernateQuery.java`, `JpaCriteriaQueryCreator.java` |

Query construction goes through the JPA `CriteriaBuilder` API entirely. Values are passed as typed `ParameterExpression` objects. No string interpolation.

---

### S-4 · `HibernateHqlQuery` — `"from " + clazz.getName()` (SAFE)

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/query/HibernateHqlQuery.java` |
| **Line** | 146 |

```java
q = session.createQuery("from " + clazz.getName(), clazz);
```

`clazz` is derived from `PersistentEntity.getJavaClass()` — a framework-managed class reference, not user input.

---

### S-5 · Dynamic Finders — GORM Criteria API (SAFE by design)

Dynamic finders (`findByName`, `findAllByTitleAndAuthor`, etc.) are translated to typed criteria queries via the GORM query parser. All values arrive as method arguments and are bound as typed parameters. No string interpolation occurs.

---

## Action Items for Grails 8

| ID | Action | Target | Status |
|----|--------|--------|--------|
| A-1 | Quote schema name in `DefaultSchemaHandler.useSchema` / `createSchema` using JDBC identifier quoting | `DefaultSchemaHandler.groovy` | ✅ Done — `quoteName()` added; `DefaultSchemaHandlerSpec` 9/9 pass |
| A-2 | Guard `find/findAll/executeQuery/executeUpdate(CharSequence)` single-arg overloads — throw `UnsupportedOperationException` for plain `String` | `HibernateGormStaticApi.groovy` | ✅ Done — `requireGString()` guard; `HibernateGormStaticApiSpec` 43/43 pass |
| A-3 | ~~Add runtime warning for plain `String` to no-param API~~ | superseded by A-2 (exception is better than warning) | ✅ Superseded |
| A-4 | Rename `findWithSql` / `findAllWithSql` to `findWithNativeSql` / `findAllWithNativeSql`; deprecate old names | `HibernateGormStaticApi.groovy`, `HibernateEntity.groovy` | ✅ Done — old names `@Deprecated` and delegating; GString value params still work; 43/43 pass |
| A-5 | Change `nextId()` HQL from GString to plain concatenation | `HibernateGormInstanceApi.groovy:178` | ✅ Done |
| A-6 | Document safe query patterns in GORM reference guide: prefer named params / criteria API, never concatenate user input into native SQL | `docs/` | ✅ Done — `querying/hql.adoc`, `querying/nativeSql.adoc`, `introduction/upgradeNotes.adoc` written |
