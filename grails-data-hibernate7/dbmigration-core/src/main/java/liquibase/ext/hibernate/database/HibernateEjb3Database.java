package liquibase.ext.hibernate.database;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.metamodel.ManagedType;

import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;

/**
 * Database implementation for "ejb3" hibernate configurations.
 */
public class HibernateEjb3Database extends HibernateDatabase {

    protected EntityManagerFactory entityManagerFactory;

    @Override
    public String getShortName() {
        return "hibernateEjb3";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Hibernate EJB3";
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        return Optional.ofNullable(conn.getURL())
                .map(url -> url.startsWith("hibernate:ejb3:"))
                .orElse(false);
    }

    /**
     * Calls {@link #createEntityManagerFactoryBuilder()} to create and save the entity manager factory.
     */
    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        EntityManagerFactoryBuilderImpl builder = createEntityManagerFactoryBuilder();
        this.entityManagerFactory = builder.build();
        Metadata metadata = builder.getMetadata();

        String dialectString = findDialectName();
        if (dialectString != null) {
            try {
                dialect = (Dialect) Class.forName(dialectString)
                        .getDeclaredConstructor()
                        .newInstance();
                Scope.getCurrentScope().getLog(getClass()).info("Using dialect " + dialectString);
            } catch (Exception e) {
                throw new DatabaseException(e);
            }
        } else {
            Scope.getCurrentScope()
                    .getLog(getClass())
                    .info("Could not determine hibernate dialect, using HibernateGenericDialect");
            dialect = new HibernateGenericDialect();
        }

        return metadata;
    }

    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        MyHibernatePersistenceProvider persistenceProvider = new MyHibernatePersistenceProvider();

        Map<String, Object> properties = new HashMap<>();
        properties.put(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString());
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());
        properties.put(
                AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA,
                getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA));

        String path = Optional.ofNullable(getHibernateConnection().getPath())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection path is null"));

        return (EntityManagerFactoryBuilderImpl) persistenceProvider.getEntityManagerFactoryBuilderOrNull(
                path, properties, null);
    }

    @Override
    public String getProperty(String name) {
        return Optional.ofNullable(entityManagerFactory)
                .map(emf -> (String) emf.getProperties().get(name))
                .or(() -> Optional.ofNullable(super.getProperty(name)))
                .orElse(null);
    }

    @Override
    protected String findDialectName() {
        return Optional.ofNullable(super.findDialectName())
                .or(() -> Optional.ofNullable(entityManagerFactory)
                        .map(emf -> (String) emf.getProperties().get(AvailableSettings.DIALECT)))
                .orElse(null);
    }

    /**
     * Adds sources based on what is in the saved entityManagerFactory
     */
    @Override
    protected void configureSources(MetadataSources sources) {
        Optional.ofNullable(entityManagerFactory)
                .map(EntityManagerFactory::getMetamodel)
                .ifPresent(metamodel -> metamodel.getManagedTypes().stream()
                        .map(ManagedType::getJavaType)
                        .filter(java.util.Objects::nonNull)
                        .forEach(sources::addAnnotatedClass));

        Arrays.stream(Package.getPackages()).forEach(sources::addPackage);
    }

    private static class MyHibernatePersistenceProvider extends HibernatePersistenceProvider {

        private void setField(final Object obj, String fieldName, final Object value)
                throws NoSuchFieldException, IllegalAccessException {
            final Field declaredField = obj.getClass().getDeclaredField(fieldName);
            if (declaredField.trySetAccessible()) {
                declaredField.set(obj, value);
            } else {
                throw new IllegalAccessException("Cannot access field: " + fieldName);
            }
        }

        @Override
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(
                String persistenceUnitName, Map properties, ClassLoader providedClassLoader) {
            return super.getEntityManagerFactoryBuilderOrNull(persistenceUnitName, properties, providedClassLoader);
        }

        @Override
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(
                PersistenceUnitDescriptor persistenceUnitDescriptor, Map integration, ClassLoader providedClassLoader) {
            try {
                setField(persistenceUnitDescriptor, "jtaDataSource", null);
                setField(persistenceUnitDescriptor, "transactionType", PersistenceUnitTransactionType.RESOURCE_LOCAL);
            } catch (Exception ex) {
                Scope.getCurrentScope().getLog(getClass()).severe(null, ex);
            }
            return super.getEntityManagerFactoryBuilder(persistenceUnitDescriptor, integration, providedClassLoader);
        }
    }
}
