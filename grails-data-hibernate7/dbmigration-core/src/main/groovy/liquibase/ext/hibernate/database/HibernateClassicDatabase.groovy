package liquibase.ext.hibernate.database

import groovy.transform.CompileStatic
import liquibase.database.DatabaseConnection
import liquibase.exception.DatabaseException
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Configuration
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import org.hibernate.service.ServiceRegistry

/**
 * Database implementation for "classic" hibernate configurations.
 */
@CompileStatic
class HibernateClassicDatabase extends HibernateDatabase {

    protected Configuration configuration
    // Track the registry so we can close it later
    private ServiceRegistry serviceRegistry

    @Override
    boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        String url = conn.getURL()
        return url != null && url.startsWith('hibernate:classic:')
    }

    @Override
    protected String findDialectName() {
        String dialectName = super.findDialectName()
        if (dialectName != null) {
            return dialectName
        }
        return configuration != null ? configuration.getProperty(AvailableSettings.DIALECT) : null
    }

    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        this.configuration = new Configuration()
        String path = getHibernateConnection().getPath()
        if (path == null) {
            throw new IllegalStateException('Hibernate connection path is null')
        }
        this.configuration.configure(path)

        return super.buildMetadataFromPath()
    }

    @Override
    protected void configureSources(MetadataSources sources) {
        Configuration config = new Configuration(sources)
        String path = getHibernateConnection().getPath()
        if (path == null) {
            throw new IllegalStateException('Hibernate connection path is null')
        }
        config.configure(path)

        config.setProperty(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString())
        config.setProperty('hibernate.cache.use_second_level_cache', 'false')

        // Assign to the class field instead of a local variable
        this.serviceRegistry = configuration
                .getStandardServiceRegistryBuilder()
                .applySettings(config.getProperties())
                .addService(ConnectionProvider, new NoOpConnectionProvider())
                .addService(MultiTenantConnectionProvider, new NoOpMultiTenantConnectionProvider())
                .build()

        // We build the factory to finalize the configuration, but we don't
        // need to hold a reference to it here if we aren't using it.
        config.buildSessionFactory(serviceRegistry)
    }

    @Override
    void close() throws DatabaseException {
        try {
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry)
            }
        } finally {
            super.close()
        }
    }

    @Override
    String getShortName() {
        return 'hibernateClassic'
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return 'Hibernate Classic'
    }

}
