# Current Issues & Progress - GORM 7 Stateless Refactor

## Status Summary
- **TCK Progress**: ~92 failures -> **23 failures remaining**.
- **Memory Scaling**: O(M+N) verified; physical map sharing with logical prefixing (Hybrid Isolation) is stable.
- **Groovy 4 / Java 24 Stability**: Core AST transformations (`ServiceTransformation`, `TransactionalTransform`, `DirtyCheckingTransformer`) stabilized for Groovy 4 compliance.
- **Current Focus**: **Hibernate 7 Stabilization**. Shifting from Core mapping to resolving regressions and compilation errors in the H7 submodule.

## Completed in Last Session
1.  **Core GORM 7 Stabilization**:
    - **Service/Entity Transformations**: Fully stabilized for Groovy 4, ensuring `@Generated` visibility and correct method modifier inheritance.
    - **GormRegistry**: Expanded to support per-connection `PlatformTransactionManager` resolution.
    - **Test Coverage**: All tests in `:grails-datastore-core`, `:grails-datamapping-validation`, and `:grails-datamapping-core` are confirmed passing.

## Classes Touched (Verification Required)
- `org.grails.datastore.gorm.services.transform.ServiceTransformation` (Modifier, annotation, and @Generated fixes)
- `org.grails.datastore.gorm.GormRegistry` (Added PlatformTransactionManager support)
- `org.grails.datastore.mapping.reflect.AstUtils` (Annotation duplication guards)
- `org.grails.datastore.gorm.services.implementers.AbstractStringQueryImplementer` (Constant string support and manual validation)
- `org.grails.datastore.gorm.services.implementers.FindOneStringQueryImplementer` (Return type compatibility for plain strings)
- `org.grails.datastore.gorm.services.implementers.FindAllByImplementer` (Parameter type validation)
- `org.grails.datastore.mapping.services.DefaultServiceRegistrySpec` (Fixed TestService compilation error)
- `grails.gorm.services.transform.ServiceTransformSpec` (Refactored to package + compiled classes)
- `org.grails.compiler.gorm.GormEntityTransformSpec` (Fixed lifecycle and registration)
- `grails.gorm.services.MethodValidationTransformSpec` (Fixed @Generated detection and registration)

## In Progress / Blocked (Hibernate 7 Focus)
1.  **[BUG] H7 Static/Instance API Visibility**: `isFailOnError()` and `isMarkDirty()` in `HibernateGormInstanceApi` require visibility updates to match new public core contract.
2.  **[BUG] H7 Constructor Mismatch**: `HibernateGormStaticApi` requires update to support `AbstractGormApi$ConstantDatastoreResolver`.
3.  **[BUG] Many-to-Many NPE**: `ManyToManySpec` (Persistence TCK) reports NPE in `AbstractSession.retrieveAll` when initializing collections.

## Next Steps
1.  Resolve all compilation errors in `:grails-data-hibernate7-core`.
2.  Run H7 unit tests and aggregate failures.
3.  Address remaining 23 TCK failures within the H7 context.
