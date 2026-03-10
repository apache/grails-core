package liquibase.ext.hibernate.database;

import liquibase.Scope;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.ext.hibernate.customfactory.CustomMetadataFactory;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import liquibase.ext.hibernate.database.connection.HibernateDriver;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.ServiceRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for all Hibernate Databases. This extension interacts with Hibernate by creating standard liquibase.database.Database implementations that
 * bridge what Liquibase expects and the Hibernate APIs.
 */
public abstract class HibernateDatabase extends AbstractJdbcDatabase {

    private Metadata metadata;
    protected Dialect dialect;

    private boolean indexesForForeignKeys = false;
    public static final String DEFAULT_SCHEMA = "HIBERNATE";
    public static final String HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS = "hibernate.temp.use_jdbc_metadata_defaults";

    public HibernateDatabase() {
        setDefaultCatalogName(DEFAULT_SCHEMA);
        setDefaultSchemaName(DEFAULT_SCHEMA);
    }

    public boolean requiresPassword() {
        return false;
    }

    public boolean requiresUsername() {
        return false;
    }

    public String getDefaultDriver(String url) {
        if (url.startsWith("hibernate")) {
            return HibernateDriver.class.getName();
        }
        return null;
    }

    public int getPriority() {
        return PRIORITY_DEFAULT;
    }


    @Override
    public void setConnection(DatabaseConnection conn) {
        super.setConnection(conn);

        try {
            Scope.getCurrentScope().getLog(getClass()).info("Reading hibernate configuration " + getConnection().getURL());

            this.metadata = buildMetadata();

            afterSetup();
        } catch (DatabaseException e) {
            throw new UnexpectedLiquibaseException(e);
        }

    }

    /**
     * Called by {@link #createMetadataSources()} to determine the correct dialect name based on url parameters, configuration files, etc.
     */
    protected String findDialectName() {
        return getHibernateConnection().getProperties().getProperty(AvailableSettings.DIALECT);
    }

    /**
     * Returns the dialect determined during database initialization.
     */
    public Dialect getDialect() {
        return dialect;
    }

    /**
     * Return the hibernate {@link Metadata} used by this database.
     */
    public Metadata getMetadata() {
        return metadata;
    }


    /**
     * Convenience method to return the underlying HibernateConnection in the JdbcConnection returned by {@link #getConnection()}
     */
    protected HibernateConnection getHibernateConnection() {
        DatabaseConnection originalConnection = getConnection();
        if (originalConnection instanceof liquibase.database.jvm.JdbcConnection) {
            java.sql.Connection underlyingConnection = ((liquibase.database.jvm.JdbcConnection) originalConnection).getUnderlyingConnection();
            if (underlyingConnection instanceof HibernateConnection) {
                return (HibernateConnection) underlyingConnection;
            } else {
                throw new UnexpectedLiquibaseException("Underlying connection is not a HibernateConnection: " + underlyingConnection.getClass().getName());
            }
        } else if (originalConnection instanceof HibernateConnection) {
            return (HibernateConnection) originalConnection;
        } else {
            throw new UnexpectedLiquibaseException("Unknown connection type: " + originalConnection.getClass().getName());
        }
    }

    /**
     * Called by {@link #setConnection(DatabaseConnection)} to create the Metadata stored in this database.
     * If the URL path is configured for a {@link CustomMetadataFactory}, create the metadata from that class.
     * Otherwise, it delegates to {@link #buildMetadataFromPath()}
     */
    protected final Metadata buildMetadata() throws DatabaseException {
        String path = getHibernateConnection().getPath();
        if (!path.contains("/")) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(path);
                if (CustomMetadataFactory.class.isAssignableFrom(clazz)) {
                    try {
                        return ((CustomMetadataFactory) clazz.getDeclaredConstructor().newInstance()).getMetadata(this, getHibernateConnection());
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException e) {
                        throw new DatabaseException(e);
                    }
                }
            } catch (ClassNotFoundException ignore) {
                //not really a class, continue
            }
        }

        return buildMetadataFromPath();
    }

    /**
     * Called by {@link #buildMetadata()} when a {@link CustomMetadataFactory} is not configured.
     * Default implementation passes the results of {@link #createMetadataSources()} to {@link #configureSources(MetadataSources)} and then calls {@link #configureMetadataBuilder(MetadataBuilder)}
     * but this method can be overridden with any provider-specific implementations needed.
     */
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        MetadataSources sources = createMetadataSources();
        configureSources(sources);

        MetadataBuilder metadataBuilder = sources.getMetadataBuilder();
        configureMetadataBuilder(metadataBuilder);

        AtomicReference<Throwable> thrownException = new AtomicReference<>();
        AtomicReference<Metadata> result = new AtomicReference<>();

        Thread t = new Thread(() -> result.set(metadataBuilder.build()));
        t.setContextClassLoader(Scope.getCurrentScope().getClassLoader());
        t.setUncaughtExceptionHandler((_t,e) -> thrownException.set(e));
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new DatabaseException(e);
        }
        Throwable thrown = thrownException.get();
        if (thrown != null) {
            throw new DatabaseException(thrown);
        }
        return result.get();
    }


    /**
     * Creates the base {@link MetadataSources} to use for this database.
     * Normally, the result of this method is passed through {@link #configureSources(MetadataSources)}.
     */
    protected MetadataSources createMetadataSources() throws DatabaseException {
        String dialectString = findDialectName();
        if (dialectString != null) {
            try {
                dialect = (Dialect) Thread.currentThread().getContextClassLoader().loadClass(dialectString).getDeclaredConstructor().newInstance();
                Scope.getCurrentScope().getLog(getClass()).info("Using dialect " + dialectString);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                throw new DatabaseException(e);
            }
        } else {
            Scope.getCurrentScope().getLog(getClass()).info("Could not determine hibernate dialect, using HibernateGenericDialect");
            dialect = new HibernateGenericDialect();
        }


        ServiceRegistry standardRegistry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, dialect)
                .applySetting(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString())
                .addService(ConnectionProvider.class, new NoOpConnectionProvider())
                .addService(MultiTenantConnectionProvider.class, new NoOpMultiTenantConnectionProvider())
                .build();

        return new MetadataSources(standardRegistry);
    }


    /**
     * Adds any implementation-specific sources to the given {@link MetadataSources}
     */
    protected abstract void configureSources(MetadataSources sources) throws DatabaseException;


    protected void configurePhysicalNamingStrategy(String physicalNamingStrategy, MetadataBuilder builder) throws DatabaseException {
        String namingStrategy;
        namingStrategy = getHibernateConnection().getProperties().getProperty(AvailableSettings.PHYSICAL_NAMING_STRATEGY, physicalNamingStrategy);

        try {
            if (namingStrategy != null) {
                builder.applyPhysicalNamingStrategy((PhysicalNamingStrategy) Thread.currentThread().getContextClassLoader().loadClass(namingStrategy).getDeclaredConstructor().newInstance());
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new DatabaseException(e);
        }
    }

    protected void configureImplicitNamingStrategy(String implicitNamingStrategy, MetadataBuilder builder) throws DatabaseException {
        String namingStrategy;
        namingStrategy = getHibernateConnection().getProperties().getProperty(AvailableSettings.IMPLICIT_NAMING_STRATEGY, implicitNamingStrategy);

        try {
            if (namingStrategy != null) {
                switch (namingStrategy) {
                    case "default":
                    case "jpa":
                        builder.applyImplicitNamingStrategy(org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl.INSTANCE);
                        break;
                    case "legacy-hbm":
                        builder.applyImplicitNamingStrategy(org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyHbmImpl.INSTANCE);
                        break;
                    case "legacy-jpa":
                        builder.applyImplicitNamingStrategy(org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl.INSTANCE);
                        break;
                    case "component-path":
                        builder.applyImplicitNamingStrategy(org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl.INSTANCE);
                        break;
                    default:
                        builder.applyImplicitNamingStrategy((ImplicitNamingStrategy) Thread.currentThread().getContextClassLoader().loadClass(namingStrategy).getDeclaredConstructor().newInstance());
                        break;
                }

            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Perform any post-configuration setting logic.
     */
    protected void afterSetup() {
        if (dialect instanceof MySQLDialect) {
            indexesForForeignKeys = true;
        }
    }


    /**
     * Called by {@link #buildMetadataFromPath()} to do final configuration on the {@link MetadataBuilder} before {@link MetadataBuilder#build()} is called.
     */
    protected void configureMetadataBuilder(MetadataBuilder metadataBuilder) throws DatabaseException {
        configureImplicitNamingStrategy(getProperty(AvailableSettings.IMPLICIT_NAMING_STRATEGY), metadataBuilder);
        configurePhysicalNamingStrategy(getProperty(AvailableSettings.PHYSICAL_NAMING_STRATEGY), metadataBuilder);
        metadataBuilder.enableGlobalNationalizedCharacterDataSupport(
            Boolean.parseBoolean(getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA)));
    }

    /**
     * Returns the value of the given property. Should return the value given as a connection URL first, then fall back to configuration-specific values.
     */
    public String getProperty(String name) {
        return getHibernateConnection().getProperties().getProperty(name);
    }


    /** Required for snapshot auto-increment detection of identity/sequence columns managed by Hibernate. */
    @Override
    public boolean supportsAutoIncrement() {
        return true;
    }

    @Override
    public boolean createsIndexesForForeignKeys() {
        return indexesForForeignKeys;
    }

    @Override
    public Integer getDefaultPort() {
        return 0;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return false;
    }

    @Override
    public boolean supportsTablespaces() {
        return false;
    }

    @Override
    protected String getConnectionCatalogName() throws DatabaseException {
        return getDefaultCatalogName();
    }

    @Override
    protected String getConnectionSchemaName() {
        return getDefaultSchemaName();
    }

    @Override
    public String getDefaultSchemaName() {
        return DEFAULT_SCHEMA;
    }

    @Override
    public String getDefaultCatalogName() {
        return DEFAULT_SCHEMA;
    }

    @Override
    public boolean isSafeToRunUpdate() throws DatabaseException {
        return true;
    }

    @Override
    public boolean isCaseSensitive() {
        return false;
    }

    @Override
    public boolean supportsSchemas() {
        return true;
    }

    @Override
    public boolean supportsCatalogs() {
        return true;
    }

}
