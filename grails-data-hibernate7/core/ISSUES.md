# Known Issues in Hibernate 7 Migration


## Failing Tests
BasicCollectionInQuerySpec
ByteBuddyGroovyInterceptorSpec
DetachedAssociationFunctionSpec
DetachedCriteriaProjectionAliasSpec
HibernateProxyHandler7Spec
WhereQueryOldIssueVerificationSpec


---

### 3. ByteBuddy Proxy Initialization & Interception
**Symptoms:**
- `ByteBuddyGroovyInterceptorSpec` and `HibernateProxyHandler7Spec` failures.
- Proxies are initialized prematurely during `getId()`, `isDirty()`, or Groovy internal calls.

**Description:**
Hibernate 7's `ByteBuddyInterceptor.intercept()` does not distinguish between actual property access and Groovy's internal metadata calls (like `getMetaClass()`). This triggers hydration during common Groovy operations.

---

---

---

### 6. MappingException: Class 'java.util.Set' does not implement 'UserCollectionType'
**Symptoms:**
- `org.hibernate.MappingException: Class 'java.util.Set' does not implement 'org.hibernate.usertype.UserCollectionType'`
- Affects `BasicCollectionInQuerySpec`.

**Description:**
Hibernate 7 changed how collection types are resolved. Some tests using `hasMany` with default collection types are failing during `buildSessionFactory`.

---
---

### 8. IDENTITY Generator Default in TCK
**Symptoms:**
- `HibernateMappingFactorySpec` failure: `entity.mapping.identifier.generator == ValueGenerator.NATIVE` condition not satisfied.

**Description:**
The TCK Manager now globally sets `id generator: 'identity'` to avoid `SequenceStyleGenerator` issues in Hibernate 7. This causes tests that expect the default `NATIVE` generator to fail.

---

