package liquibase.ext.hibernate.database

import jakarta.persistence.spi.PersistenceUnitInfo

import groovy.transform.CompileStatic
import liquibase.database.DatabaseConnection
import liquibase.ext.hibernate.database.connection.HibernateDriver
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl
import org.hibernate.jpa.boot.spi.Bootstrap
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager

/**
 * Database implementation for JPA configurations.
 * This supports passing a JPA persistence XML file reference.
 */
@CompileStatic
class JpaPersistenceDatabase extends HibernateEjb3Database {

    @Override
    boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        String url = conn.getURL()
        return url != null && url.startsWith('jpa:persistence:')
    }

    @Override
    String getDefaultDriver(String url) {
        if (url != null && url.startsWith('jpa:persistence:')) {
            return HibernateDriver.getName()
        }
        return null
    }

    @Override
    String getShortName() {
        return 'jpaPersistence'
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return 'JPA Persistence'
    }

    @Override
    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        DefaultPersistenceUnitManager internalPersistenceUnitManager = new DefaultPersistenceUnitManager()

        String path = getHibernateConnection().getPath()
        if (path == null) {
            throw new IllegalStateException('Hibernate connection path is null')
        }

        internalPersistenceUnitManager.setPersistenceXmlLocation(path)

        internalPersistenceUnitManager.preparePersistenceUnitInfos()
        PersistenceUnitInfo persistenceUnitInfo = internalPersistenceUnitManager.obtainDefaultPersistenceUnitInfo()
        if (persistenceUnitInfo == null) {
            throw new IllegalStateException('No persistence unit info found for path: ' + path)
        }

        Map<String, Object> integration =
                (Map<String, Object>) Map.of(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString())
        return (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(persistenceUnitInfo, integration)
    }

}
