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

/**
 * Database implementation for "classic" hibernate configurations.
 */
public class HibernateClassicDatabase extends HibernateDatabase {

    protected Configuration configuration;

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

        ServiceRegistry standardRegistry = configuration
                .getStandardServiceRegistryBuilder()
                .applySettings(config.getProperties())
                .addService(ConnectionProvider.class, new NoOpConnectionProvider())
                .addService(MultiTenantConnectionProvider.class, new NoOpMultiTenantConnectionProvider())
                .build();

        config.buildSessionFactory(standardRegistry);
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
