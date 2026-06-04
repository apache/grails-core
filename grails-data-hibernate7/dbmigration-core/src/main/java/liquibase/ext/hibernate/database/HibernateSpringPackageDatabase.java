package liquibase.ext.hibernate.database;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.spi.PersistenceUnitInfo;

import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.jpa.boot.spi.Bootstrap;

import org.springframework.core.NativeDetector;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.SmartPersistenceUnitInfo;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Database implementation for "spring" hibernate configurations that scans packages. If specifying a bean, {@link HibernateSpringBeanDatabase} is used.
 */
public class HibernateSpringPackageDatabase extends JpaPersistenceDatabase {

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        return Optional.ofNullable(conn.getURL())
                .map(url -> url.startsWith("hibernate:spring:") && !isXmlFile(conn))
                .orElse(false);
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 10; // want this to be picked over HibernateSpringBeanDatabase if it is not xml file
    }

    /**
     * Return true if the given path is a spring XML file.
     */
    @SuppressWarnings("PMD.CloseResource")
    protected boolean isXmlFile(DatabaseConnection connection) {
        HibernateConnection hibernateConnection = getHibernateConnection(connection);

        if (hibernateConnection == null || hibernateConnection.getPath() == null) {
            return false;
        }

        String path = hibernateConnection.getPath();

        // If it looks like a path, treat as XML
        if (path.contains("/")) {
            return true;
        }

        // Use Context ClassLoader for resource lookup (PMD #11 compliance)
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ClassPathResource resource = new ClassPathResource(path, loader);

        try {
            return resource.exists() && !resource.getFile().isDirectory();
        } catch (IOException e) {
            return false;
        }
    }

    private HibernateConnection getHibernateConnection(DatabaseConnection conn) {
        if (conn instanceof HibernateConnection hc) {
            return hc;
        }
        if (conn instanceof liquibase.database.jvm.JdbcConnection jdbc &&
                jdbc.getUnderlyingConnection() instanceof HibernateConnection hc) {
            return hc;
        }
        return null;
    }

    @Override
    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        DefaultPersistenceUnitManager internalPersistenceUnitManager = new DefaultPersistenceUnitManager();

        // Fix: Use Thread Context ClassLoader for J2EE/Spring compliance (PMD #11)
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        internalPersistenceUnitManager.setResourceLoader(new DefaultResourceLoader(contextClassLoader));

        // Fix: Use try-with-resources to ensure connection is handled correctly (PMD #10)
        try (HibernateConnection connection = getHibernateConnection()) {
            String path = connection.getPath();
            if (path == null) {
                throw new IllegalStateException("Hibernate connection path is null");
            }
            String[] packagesToScan = path.split(",");

            for (String packageName : packagesToScan) {
                Scope.getCurrentScope().getLog(getClass()).info("Found package " + packageName);
            }

            internalPersistenceUnitManager.setPackagesToScan(packagesToScan);
            internalPersistenceUnitManager.preparePersistenceUnitInfos();
            PersistenceUnitInfo persistenceUnitInfo = internalPersistenceUnitManager.obtainDefaultPersistenceUnitInfo();
            HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();

            if (persistenceUnitInfo instanceof SmartPersistenceUnitInfo) {
                ((SmartPersistenceUnitInfo) persistenceUnitInfo)
                        .setPersistenceProviderPackageName(jpaVendorAdapter.getPersistenceProviderRootPackage());
            }

            Map<String, String> map = new HashMap<>();
            map.put(AvailableSettings.DIALECT, getProperty(AvailableSettings.DIALECT));
            map.put(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString());
            map.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());
            map.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, connection.getProperties().getProperty(AvailableSettings.PHYSICAL_NAMING_STRATEGY));
            map.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY, connection.getProperties().getProperty(AvailableSettings.IMPLICIT_NAMING_STRATEGY));
            map.put(AvailableSettings.SCANNER_DISCOVERY, "");
            map.put(EnversSettings.AUDIT_TABLE_PREFIX, connection.getProperties().getProperty(EnversSettings.AUDIT_TABLE_PREFIX, ""));
            map.put(EnversSettings.AUDIT_TABLE_SUFFIX, connection.getProperties().getProperty(EnversSettings.AUDIT_TABLE_SUFFIX, "_AUD"));
            map.put(EnversSettings.REVISION_FIELD_NAME, connection.getProperties().getProperty(EnversSettings.REVISION_FIELD_NAME, "REV"));
            map.put(EnversSettings.REVISION_TYPE_FIELD_NAME, connection.getProperties().getProperty(EnversSettings.REVISION_TYPE_FIELD_NAME, "REVTYPE"));
            map.put(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA, getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA));
            map.put(AvailableSettings.TIMEZONE_DEFAULT_STORAGE, getProperty(AvailableSettings.TIMEZONE_DEFAULT_STORAGE));

            PersistenceUnitInfoDescriptor persistenceUnitInfoDescriptor = createPersistenceUnitInfoDescriptor(persistenceUnitInfo);
            return (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(persistenceUnitInfoDescriptor, map);

        }
    }

    public PersistenceUnitInfoDescriptor createPersistenceUnitInfoDescriptor(PersistenceUnitInfo info) {
        final List<String> mergedClassesAndPackages = new ArrayList<>(info.getManagedClassNames());
        if (info instanceof SmartPersistenceUnitInfo) {
            mergedClassesAndPackages.addAll(((SmartPersistenceUnitInfo) info).getManagedPackages());
        }
        return new PersistenceUnitInfoDescriptor(info) {
            @Override
            public List<String> getManagedClassNames() {
                return mergedClassesAndPackages;
            }

            @Override
            public void pushClassTransformer(EnhancementContext enhancementContext) {
                if (!NativeDetector.inNativeImage()) {
                    super.pushClassTransformer(enhancementContext);
                }
            }
        };
    }

    @Override
    public String getShortName() {
        return "hibernateSpringPackage";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Hibernate Spring Package";
    }
}
