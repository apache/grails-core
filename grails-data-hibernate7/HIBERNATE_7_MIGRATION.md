# Hibernate 7 Migration Roadmap

This document outlines the state of the Hibernate 7 migration in Grails Core and the necessary steps to achieve full stability once the project transitions to **Spring 7**.

## Current Status & Roadblocks

The migration is currently in a "namespace stalemate" due to the **Spring 6.2** baseline.

### 1. The Namespace Conflict (javax vs jakarta)
Spring 6.2 provides Hibernate support via the `org.springframework.orm.hibernate5` package. Despite its name, this package is a legacy shim that is hardcoded to:
- Use `javax.sql.DataSource`
- Expect Hibernate 5/6 internal structures.
- Use `javax.servlet` references in its Web support.

Hibernate 7 has fully migrated to the `jakarta.*` namespace and requires `jakarta.sql.DataSource`.

### 2. Runtime Type Conflicts (`GroovyCastException`)
Attempts to bridge the gap by creating custom Grails-side versions of `SessionHolder` and `SessionFactoryUtils` using the `jakarta` namespace result in runtime failures. Because `GrailsHibernateTransactionManager` must inherit from Spring's `HibernateTransactionManager`, the Spring superclass logic continues to bind `javax`-based holders to the thread, which cannot be cast to our `jakarta`-based counterparts.

## Migration Path (Spring 7)

Spring 7.0 officially supersedes the `orm.hibernate5` package with native Hibernate 7 support. Once the `spring7` branch is merged, the following actions must be taken:

### 1. Update Transaction Management
- **Switch Superclass**: `GrailsHibernateTransactionManager` should be updated to extend the new Spring 7 equivalent (likely `org.springframework.orm.hibernate.HibernateTransactionManager` or a renamed JPA-integrated variant).
- **Remove Hacks**: The custom `org.grails.orm.hibernate.support.SessionHolder` and `SessionFactoryUtils` (currently deleted to stabilize the build) will no longer be needed as Spring will natively handle the `jakarta` namespace.

### 2. Standardize Bytecode Provider
- **Refactor `GrailsBytecodeProvider`**: The `IllegalAccessError` with Hibernate 7's `ByteBuddyState` (which has a package-private constructor) was solved by making the provider a Spring bean.
- **Testing**: Continue using the reflection-based `TestGrailsBytecodeProvider` in `HibernateSpec` until Hibernate 7 provides a public way to instantiate `ByteBuddyState`.

### 3. Dependency Cleanup
- **Remove Legacy Jars**: Remove `javax.validation:validation-api` and other legacy shims currently required to keep the Hibernate 7 `Integrator`s happy in a Spring 6 environment.
- **Jakarta Validation**: Fully migrate to `jakarta.validation-api` 3.0+.

### 4. Metadata & Integrators
- Hibernate 7 changed the `Integrator.integrate` method signature.
- **Action**: Ensure `EventListenerIntegrator` and `MetadataIntegrator` are using the updated `Metadata` and `BootstrapServiceRegistry` parameters correctly.

## Summary of Completed Work (Preserved in `core`)
- [x] **`HibernateDatastore`**: Updated to support `GrailsBytecodeProvider` injection.
- [x] **`HibernateConnectionSourceFactory`**: Updated to propagate `bytecodeProvider` and correctly handle `jakarta.persistence.nonJtaDataSource`.
- [x] **`HibernateMappingContextConfiguration`**: Updated to apply `jakarta` settings to the `StandardServiceRegistryBuilder`.
- [x] **`HibernateDatastoreSpringInitializer`**: Refactored to register the bytecode provider as a managed bean.
- [x] **Multi-DataSource Support**: Resolved infinite recursion in child datastore initialization and fixed `ClassCastException` in `bytecodeProvider` safe retrieval.

## Immediate Next Steps (After Spring 7 Merge)
1. Re-run `ProxySpec` in the `grails-hibernate-groovy-proxy` example project.
2. Remove the `@IgnoreIf` annotations on TCK tests currently marked as incompatible with the Spring 6.2 shim.
3. Validate multi-tenancy (Schema/Database) which is currently sensitive to the `DataSource` unwrapping logic.
