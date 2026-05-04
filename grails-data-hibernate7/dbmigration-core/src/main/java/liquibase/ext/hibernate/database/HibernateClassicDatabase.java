package liquibase.ext.hibernate.database;

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

    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getURL().startsWith("hibernate:classic:");
    }

    @Override
    protected String findDialectName() {
        String dialectName = super.findDialectName();

        if (dialectName == null) {
            dialectName = configuration.getProperty(AvailableSettings.DIALECT);
        }
        return dialectName;
    }


    protected Metadata buildMetadataFromPath() throws DatabaseException {
        this.configuration = new Configuration();
        this.configuration.configure(getHibernateConnection().getPath());

        return super.buildMetadataFromPath();
    }

    @Override
    protected void configureSources(MetadataSources sources) throws DatabaseException {
        Configuration config = new Configuration(sources);
        config.configure(getHibernateConnection().getPath());

        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.cache.use_second_level_cache", "false");

        ServiceRegistry standardRegistry = configuration.getStandardServiceRegistryBuilder()
                .applySettings(config.getProperties())
                .addService(ConnectionProvider.class, new NoOpConnectionProvider())
                .addService(MultiTenantConnectionProvider.class, new NoOpConnectionProvider())
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