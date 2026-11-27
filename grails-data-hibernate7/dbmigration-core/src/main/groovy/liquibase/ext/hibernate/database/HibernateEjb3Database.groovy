package liquibase.ext.hibernate.database

import java.lang.reflect.Field

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceUnitTransactionType
import jakarta.persistence.metamodel.ManagedType
import jakarta.persistence.metamodel.Metamodel

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import liquibase.Scope
import liquibase.database.DatabaseConnection
import liquibase.exception.DatabaseException
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.AvailableSettings
import org.hibernate.dialect.Dialect
import org.hibernate.jpa.HibernatePersistenceProvider
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor

/**
 * Database implementation for "ejb3" hibernate configurations.
 */
@CompileStatic
@POJO // see HibernateDatabase
class HibernateEjb3Database extends HibernateDatabase {

    protected EntityManagerFactory entityManagerFactory

    @Override
    String getShortName() {
        return 'hibernateEjb3'
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return 'Hibernate EJB3'
    }

    @Override
    boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        String url = conn.getURL()
        return url != null && url.startsWith('hibernate:ejb3:')
    }

    /**
     * Calls {@link #createEntityManagerFactoryBuilder()} to create and save the entity manager factory.
     */
    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        EntityManagerFactoryBuilderImpl builder = createEntityManagerFactoryBuilder()
        this.entityManagerFactory = builder.build()
        Metadata metadata = builder.getMetadata()

        // this.@dialect: direct access to the inherited protected field. A bare assignment would compile
        // to a dynamic property set, which a @POJO class cannot service.
        String dialectString = findDialectName()
        if (dialectString != null) {
            try {
                this.@dialect = (Dialect) Class.forName(dialectString)
                        .getDeclaredConstructor()
                        .newInstance()
                Scope.getCurrentScope().getLog(getClass()).info('Using dialect ' + dialectString)
            } catch (Exception e) {
                throw new DatabaseException(e)
            }
        } else {
            Scope.getCurrentScope()
                    .getLog(getClass())
                    .info('Could not determine hibernate dialect, using HibernateGenericDialect')
            this.@dialect = new HibernateGenericDialect()
        }

        return metadata
    }

    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        MyHibernatePersistenceProvider persistenceProvider = new MyHibernatePersistenceProvider()

        Map<String, Object> properties = new HashMap<>()
        properties.put(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString())
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString())
        properties.put(
                AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA,
                getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA))

        String path = getHibernateConnection().getPath()
        if (path == null) {
            throw new IllegalStateException('Hibernate connection path is null')
        }

        return (EntityManagerFactoryBuilderImpl) persistenceProvider.getEntityManagerFactoryBuilderOrNull(
                path, properties, null)
    }

    @Override
    String getProperty(String name) {
        if (entityManagerFactory != null) {
            String value = (String) entityManagerFactory.getProperties().get(name)
            if (value != null) {
                return value
            }
        }
        return super.getProperty(name)
    }

    @Override
    protected String findDialectName() {
        String dialectName = super.findDialectName()
        if (dialectName != null) {
            return dialectName
        }
        return entityManagerFactory != null ?
                (String) entityManagerFactory.getProperties().get(AvailableSettings.DIALECT) : null
    }

    /**
     * Adds sources based on what is in the saved entityManagerFactory
     */
    @Override
    protected void configureSources(MetadataSources sources) {
        Metamodel metamodel = entityManagerFactory != null ? entityManagerFactory.getMetamodel() : null
        if (metamodel != null) {
            for (ManagedType<?> managedType : metamodel.getManagedTypes()) {
                Class<?> javaType = managedType.getJavaType()
                if (javaType != null) {
                    sources.addAnnotatedClass(javaType)
                }
            }
        }

        for (Package pkg : Package.getPackages()) {
            sources.addPackage(pkg)
        }
    }

    private static class MyHibernatePersistenceProvider extends HibernatePersistenceProvider {

        private void setField(final Object obj, String fieldName, final Object value)
                throws NoSuchFieldException, IllegalAccessException {
            final Field declaredField = obj.getClass().getDeclaredField(fieldName)
            if (declaredField.trySetAccessible()) {
                declaredField.set(obj, value)
            } else {
                throw new IllegalAccessException('Cannot access field: ' + fieldName)
            }
        }

        // Re-declared so the protected superclass method becomes accessible from the enclosing
        // class, which is in this package but is not a HibernatePersistenceProvider subclass.
        @Override
        @SuppressWarnings('UnnecessaryOverridingMethod')
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(
                String persistenceUnitName, Map properties, ClassLoader providedClassLoader) {
            return super.getEntityManagerFactoryBuilderOrNull(persistenceUnitName, properties, providedClassLoader)
        }

        @Override
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(
                PersistenceUnitDescriptor persistenceUnitDescriptor, Map integration, ClassLoader providedClassLoader) {
            try {
                setField(persistenceUnitDescriptor, 'jtaDataSource', null)
                setField(persistenceUnitDescriptor, 'transactionType', PersistenceUnitTransactionType.RESOURCE_LOCAL)
            } catch (Exception ex) {
                Scope.getCurrentScope().getLog(getClass()).severe(null, ex)
            }
            return super.getEntityManagerFactoryBuilder(persistenceUnitDescriptor, integration, providedClassLoader)
        }

    }

}
