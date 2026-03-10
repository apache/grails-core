package liquibase.ext.hibernate.database;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.PersistenceUnitTransactionType;
import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;

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
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getURL().startsWith("hibernate:ejb3:");
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
                dialect = (Dialect) Class.forName(dialectString).getDeclaredConstructor().newInstance();
                Scope.getCurrentScope().getLog(getClass()).info("Using dialect " + dialectString);
            } catch (Exception e) {
                throw new DatabaseException(e);
            }
        } else {
            Scope.getCurrentScope().getLog(getClass()).info("Could not determine hibernate dialect, using HibernateGenericDialect");
            dialect = new HibernateGenericDialect();
        }

        return metadata;
    }

    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        MyHibernatePersistenceProvider persistenceProvider = new MyHibernatePersistenceProvider();

        Map<String, Object> properties = new HashMap<>();
        properties.put(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString());
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());
        properties.put(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA, getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA));

        final EntityManagerFactoryBuilderImpl builder = (EntityManagerFactoryBuilderImpl) persistenceProvider.getEntityManagerFactoryBuilderOrNull(getHibernateConnection().getPath(), properties, null);
        return builder;
    }

    @Override
    public String getProperty(String name) {
        String property = null;
        if (entityManagerFactory != null) {
            property = (String) entityManagerFactory.getProperties().get(name);
        }

        if (property == null) {
            return super.getProperty(name);
        } else {
            return property;
        }

    }

    @Override
    protected String findDialectName() {
        String dialectName = super.findDialectName();
        if (dialectName != null) {
            return dialectName;
        }

        return (String) entityManagerFactory.getProperties().get(AvailableSettings.DIALECT);
    }

    /**
     * Adds sources based on what is in the saved entityManagerFactory
     */
    @Override
    protected void configureSources(MetadataSources sources) throws DatabaseException {
        for (ManagedType<?> managedType : entityManagerFactory.getMetamodel().getManagedTypes()) {
            Class<?> javaType = managedType.getJavaType();
            if (javaType == null) {
                continue;
            }
            sources.addAnnotatedClass(javaType);
        }

        Package[] packages = Package.getPackages();
        for (Package p : packages) {
            sources.addPackage(p);
        }
    }

    private static class MyHibernatePersistenceProvider extends HibernatePersistenceProvider {

        private void setField(final Object obj, String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
            final Field declaredField = obj.getClass().getDeclaredField(fieldName);
            if (declaredField.trySetAccessible()) {
                declaredField.set(obj, value);
            } else {
                throw new IllegalAccessException("Cannot access field: " + fieldName);
            }
        }

        @Override
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(String persistenceUnitName, Map properties, ClassLoader providedClassLoader) {
            return super.getEntityManagerFactoryBuilderOrNull(persistenceUnitName, properties, providedClassLoader);
        }

        @Override
        protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(PersistenceUnitDescriptor persistenceUnitDescriptor, Map integration, ClassLoader providedClassLoader) {
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
