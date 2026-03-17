package liquibase.ext.hibernate.database;

import java.util.Optional;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/**
 * Database implementation for "classic" hibernate configurations.
 */
public class HibernateClassicDatabase extends HibernateDatabase {

    protected Configuration configuration;
    // Track the registry so we can close it later
    private ServiceRegistry serviceRegistry;

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        return Optional.ofNullable(conn.getURL())
                .map(url -> url.startsWith("hibernate:classic:"))
                .orElse(false);
    }

    @Override
    protected String findDialectName() {
        return Optional.ofNullable(super.findDialectName())
                .or(() -> Optional.ofNullable(configuration).map(c -> c.getProperty(AvailableSettings.DIALECT)))
                .orElse(null);
    }

    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        this.configuration = new Configuration();
        String path = Optional.ofNullable(getHibernateConnection().getPath())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection path is null"));
        this.configuration.configure(path);

        return super.buildMetadataFromPath();
    }

    @Override
    protected void configureSources(MetadataSources sources) {
        Configuration config = new Configuration(sources);
        String path = Optional.ofNullable(getHibernateConnection().getPath())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection path is null"));
        config.configure(path);

        config.setProperty(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString());
        config.setProperty("hibernate.cache.use_second_level_cache", "false");

        // Assign to the class field instead of a local variable
        this.serviceRegistry = configuration
                .getStandardServiceRegistryBuilder()
                .applySettings(config.getProperties())
                .addService(ConnectionProvider.class, new NoOpConnectionProvider())
                .addService(MultiTenantConnectionProvider.class, new NoOpMultiTenantConnectionProvider())
                .build();

        // We build the factory to finalize the configuration, but we don't
        // need to hold a reference to it here if we aren't using it.
        config.buildSessionFactory(serviceRegistry);
    }

    @Override
    public void close() throws DatabaseException {
        try {
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
        } finally {
            super.close();
        }
    }

    @Override
    public String getShortName() {
        return "hibernateClassic";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Hibernate Classic";
    }
}