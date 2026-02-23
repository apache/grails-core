# Removal Deprecation Warnings — grails-data-hibernate7-core

Warnings collected by compiling `grails-data-hibernate7-core` with `-Xlint:removal`.  
These APIs are **marked for removal** in a future Hibernate / JDK release and must be migrated.

Generated from: Hibernate `7.1.11.Final`

---

| Fully Qualified Class | Line | Warning |
|---|---|---|
| `org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor` | 286 | `EntityMetamodel in org.hibernate.tuple.entity` has been deprecated and marked for removal |
| `org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor` | 305 | `EntityMetamodel in org.hibernate.tuple.entity` has been deprecated and marked for removal |

---

## Summary by API

| Deprecated API | Affected Classes | Occurrences |
|---|---|---|
| `LockOptions` (`org.hibernate`) | `GrailsHibernateTemplate` | 3 |
| `Session.get(Class,Object)` / `get(Class,Object,LockOptions)` | `GrailsHibernateTemplate` | 2 |
| `Session.refresh(Object,LockOptions)` | `GrailsHibernateTemplate` | 1 |
| `SchemaAutoTooling` (`org.hibernate.boot`) | `HibernateDatastore` | 4 |
| `IdentifierGenerator.configure(Type,Properties,ServiceRegistry)` | `GrailsIncrementGenerator` | 1 |
| `KeyValue/SimpleValue.createGenerator(Dialect,RootClass)` | `GrailsOneToOne` | 2 |
| `EntityMetamodel` (`org.hibernate.tuple.entity`) | `ClosureEventListener`, `ClosureEventTriggeringInterceptor` | 3 |
