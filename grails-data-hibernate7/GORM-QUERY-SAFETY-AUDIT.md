# GORM Query Safety Audit — `grails-data-hibernate7`

> **Scope**: Section 2.6 of `HIBERNATE7-GRAILS8-UPGRADE.md`  
> **Priority**: P2 — SQL injection is OWASP Top-10 #3  
> **Module**: `grails-data-hibernate7-core`

---

## Summary

| Risk Level | Count |
|------------|-------|
| 🔴 HIGH    | 2     |
| 🟡 MEDIUM  | 3     |
| 🟢 LOW / Safe | 5  |

---

## 🔴 HIGH — Real Injection Vectors


---

### H-2 · `HibernateGormStaticApi` — String-concatenated Queries Bypass GString Parameterization

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormStaticApi.groovy` |
| **Lines** | 238–244, 248, 233 |

**Code**:
```groovy
List  executeQuery(CharSequence query)          // line 238 — no params at all
Integer executeUpdate(CharSequence query)        // line 243 — no params at all
D       find(CharSequence query)                 // line 248 — no params at all
List<D> findAll(CharSequence query)              // line 233 — no params at all
```

The mitigation in `HibernateHqlQuery.createHqlQuery` (line 128) converts `GString` interpolations to named parameters. **However**, this only applies when the caller passes a Groovy `GString`. If the caller passes an already-evaluated `String` (e.g. from string concatenation), the GString check fires `false` and the raw interpolated string reaches `session.createQuery(hqlToUse)` unchanged.

**Example of unsafe caller pattern** (application code):
```groovy
// UNSAFE — userInput is a plain String after concatenation
String q = "from User where name = '" + userInput + "'"
User.find(q)                    // reaches Hibernate as raw HQL

// SAFE — GString is parameterized at GORM layer
User.find("from User where name = ${userInput}")
```

**Recommended fix**:
- Deprecate the single-argument `executeQuery(CharSequence)` and `executeUpdate(CharSequence)` overloads; require callers to pass params.
- Add a runtime `String` (non-`GString`) guard that logs a warning when no parameters are supplied and the query contains literals that look like values.

---

## 🟡 MEDIUM — Potential Risks Requiring Context

### M-1 · `findWithSql` / `findAllWithSql` — Native SQL Without Enforced Parameterization

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormStaticApi.groovy` |
| **Lines** | 224, 228 |

```groovy
D       findWithSql(CharSequence sql, Map args = Collections.emptyMap())
List<D> findAllWithSql(CharSequence query, Map args = Collections.emptyMap())
```

Both take a `CharSequence` that is passed to `session.createNativeQuery(hqlToUse, clazz)` with `isNative = true`. GString detection still runs, so `GString` interpolations are converted to named params. But:
1. String-concatenated SQL bypasses the GString check (same as H-2).
2. Native SQL carries no Hibernate type-safety — column names and table names cannot be parameterized at all.
3. The `Map args` default value is an empty map, making unparameterized calls easy to write accidentally.

**Recommended fix**: Rename to `findWithNativeSql` / `findAllWithNativeSql` to make the risk explicit. Add a deprecation note requiring `namedParams` or `positionalParams` argument.

---

### M-2 · `executeUpdate(CharSequence query)` Single-Argument Overload — Structural Risk

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormStaticApi.groovy` |
| **Lines** | 243–244, 549–555 |

`executeUpdate` is particularly dangerous because it performs writes. The parameterized overloads exist (`executeUpdate(query, Map params, Map args)` at line 549 and `executeUpdate(query, Collection, Map)` at line 554) but the no-args overload (line 243) remains and is the shortest form, likely the most commonly used.

Same root cause as H-2 but elevated concern because of the write path.

**Recommended fix**: `@Deprecated` the single-argument form; point callers to `executeUpdate(query, params, args)`.

---

### M-3 · `HibernateGormInstanceApi.nextId()` — GString with Class Name in HQL

| Field | Value |
|-------|-------|
| **File** | `core/src/main/groovy/org/grails/orm/hibernate/HibernateGormInstanceApi.groovy` |
| **Line** | 178 |

```groovy
String hql = "select max(e.id) from ${persistentEntity.name} e"
```

`persistentEntity.name` is a class name from framework metadata — **not user input** — so this is not an active injection risk. However:
1. The GString interpolation reaches `buildNamedParameterQueryFromGString` which tries to bind `persistentEntity.name` as a named parameter (`:p0`), producing malformed HQL: `select max(e.id) from :p0 e`.
2. In practice this does not crash because… wait — class names are structural. This should be a plain `String.format` or concatenation, not a GString, to avoid confusing the parameterizer.

**Recommended fix**: Change to plain concatenation:
```groovy
String hql = "select max(e.id) from " + persistentEntity.name + " e"
```

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

Both methods use `JpaQueryBuilder` to construct parameterized HQL. All values are bound via `query.setParameter(JpaQueryBuilder.PARAMETER_NAME_PREFIX + (i+1), parameters.get(i))`. No user-controlled strings are concatenated into the query.

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

| ID | Action | Target | Priority |
|----|--------|--------|----------|
| A-1 | Validate / quote schema name in `DefaultSchemaHandler.useSchema` and `createSchema` using JDBC identifier quoting | `DefaultSchemaHandler.groovy` | P1 (H-1) |
| A-2 | Deprecate `executeQuery(CharSequence)` and `executeUpdate(CharSequence)` single-arg overloads; require parameterized forms | `HibernateGormStaticApi.groovy` | P2 (H-2) |
| A-3 | Add runtime warning when a plain `String` (not `GString`) is passed to a no-param query API | `HibernateHqlQuery.java` | P2 (H-2) |
| A-4 | Rename `findWithSql` / `findAllWithSql` to `findWithNativeSql` / `findAllWithNativeSql`; deprecate old names | `HibernateGormStaticApi.groovy` | P2 (M-1) |
| A-5 | Change `nextId()` HQL from GString to plain concatenation to avoid accidental parameterization of class name | `HibernateGormInstanceApi.groovy:178` | P3 (M-3) |
| A-6 | Add Groovy AST transform or type-checking extension emitting a compile-time warning for `GString` passed to `executeQuery` / `executeUpdate` / `find` / `findAll` (Groovy 5 feature) | New AST transform | P2 (8.1+) |
| A-7 | Document safe query patterns in GORM reference guide: prefer named params, prefer criteria API, never concatenate user input | GORM docs | P2 |
