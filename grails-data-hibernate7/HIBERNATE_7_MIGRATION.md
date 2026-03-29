# Hibernate 7 Migration Roadmap

This document outlines the state of the Hibernate 7 migration in Grails Core. The project has transitioned to a **Spring 7** and **Spring Boot 4** baseline.

## Current Status

The migration has moved past the "namespace stalemate" by implementing a dedicated compatibility layer for Hibernate 7.

### 1. Spring ORM Fork for Hibernate 7
Since Spring 7 removed the `org.springframework.orm.hibernate5` package and its `javax.*` support, we have forked the necessary Spring ORM classes into:
`org.grails.orm.hibernate.support.hibernate7`

These classes have been migrated to:
- **Jakarta Namespace**: All `javax.persistence`, `javax.transaction`, and `javax.servlet` imports have been replaced with their `jakarta.*` equivalents.
- **Hibernate 7 API**: Updated to accommodate the removal of legacy APIs (e.g., `Criteria`, `DetachedCriteria`, `Session.load`).

### 2. Resolved Challenges
- **Namespace Conflict**: Resolved by forking and migrating the support layer.
- **Runtime Type Conflicts**: The `GroovyCastException` encountered in Spring 6.2 has been resolved by using our forked `SessionHolder` and `HibernateTransactionManager` which are native to the `jakarta` namespace.
- **Boot 4 Modularization**: Updated auto-configuration to use the new Spring Boot 4 modular packages (e.g., `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`).

## Summary of Completed Work

- [x] **Forked Support Layer**: Created `org.grails.orm.hibernate.support.hibernate7` with full `jakarta` support.
- [x] **`HibernateTemplate` Refactor**: Replaced removed APIs (`load`, `saveOrUpdate`, `iterate`) with modern Hibernate 7 equivalents (`getReference`, `persist/merge`).
- [x] **`HibernateDatastore`**: Updated to support `GrailsBytecodeProvider` injection and fixed transaction manager return type compatibility.
- [x] **`HibernateConnectionSourceFactory`**: Updated to propagate `bytecodeProvider` and correctly handle `jakarta.persistence.nonJtaDataSource`.
- [x] **`HibernateMappingContextConfiguration`**: Updated to apply `jakarta` settings to the `StandardServiceRegistryBuilder`.
- [x] **`HibernateDatastoreSpringInitializer`**: Refactored to register the bytecode provider as a managed bean.
- [x] **Multi-DataSource Support**: Resolved infinite recursion in child datastore initialization and fixed `ClassCastException` in `bytecodeProvider` safe retrieval.
- [x] **Integrator Signature Alignment**: Fixed `AbstractMethodError` by correctly implementing the 3-parameter `integrate` signature required by Hibernate 7.2.5.Final.
- [x] **Core TCK Validation**: Verified `hibernate7-core` with 2000+ tests (1986 successes, 0 failures), confirming stability of the new persistence logic.
- [x] **Spring Boot 4 Alignment**: Added `spring-boot-jdbc` and `spring-boot-hibernate` dependencies and updated auto-configuration imports.

## Immediate Next Steps


2. **TCK Validation**: Continue running the full GORM TCK suite to identify any subtle behavioral differences in the new `upsert` logic (persist vs merge).
3. **Dependency Cleanup**: Remove any remaining legacy `javax` shims that are no longer required in the Spring 7 environment.
4. **Documentation**: Update the official GORM Hibernate documentation to reflect the requirement for `jakarta` namespace and the removal of legacy Criteria APIs.
