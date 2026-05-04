package liquibase.ext.hibernate.database;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.spi.PersistenceUnitInfo;

import liquibase.Scope;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.SmartPersistenceUnitInfo;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.connection.HibernateConnection;

/**
 * Database implementation for "spring" hibernate configurations that scans packages. If specifying a bean, {@link HibernateSpringBeanDatabase} is used.
 */
public class HibernateSpringPackageDatabase extends JpaPersistenceDatabase {

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getURL().startsWith("hibernate:spring:") && !isXmlFile(conn);
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 10; //want this to be picked over HibernateSpringBeanDatabase if it is not xml file
    }

    /**
     * Return true if the given path is a spring XML file.
     */
    protected boolean isXmlFile(DatabaseConnection connection) {
        HibernateConnection hibernateConnection;
        if (connection instanceof JdbcConnection) {
            hibernateConnection = ((HibernateConnection) ((JdbcConnection) connection).getUnderlyingConnection());
        } else if (connection instanceof HibernateConnection) {
            hibernateConnection = (HibernateConnection) connection;
        } else {
            return false;
        }


        String path = hibernateConnection.getPath();
        if (path.contains("/")) {
            return true;
        }
        ClassPathResource resource = new ClassPathResource(path);
        try {
            if (resource.exists() && !resource.getFile().isDirectory()) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }


    }

    @Override
    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        DefaultPersistenceUnitManager internalPersistenceUnitManager = new DefaultPersistenceUnitManager();
        internalPersistenceUnitManager.setResourceLoader(new DefaultResourceLoader(Scope.getCurrentScope().getClassLoader()));

        String[] packagesToScan = getHibernateConnection().getPath().split(",");

        for (String packageName : packagesToScan) {
            Scope.getCurrentScope().getLog(getClass()).info("Found package " + packageName);
        }

        internalPersistenceUnitManager.setPackagesToScan(packagesToScan);

        internalPersistenceUnitManager.preparePersistenceUnitInfos();
        PersistenceUnitInfo persistenceUnitInfo = internalPersistenceUnitManager.obtainDefaultPersistenceUnitInfo();
        HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();

        if (persistenceUnitInfo instanceof SmartPersistenceUnitInfo) {
            ((SmartPersistenceUnitInfo) persistenceUnitInfo).setPersistenceProviderPackageName(jpaVendorAdapter.getPersistenceProviderRootPackage());
        }

        Map<String, String> map = new HashMap<>();
        map.put(AvailableSettings.DIALECT, getProperty(AvailableSettings.DIALECT));
        map.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());
        map.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, getHibernateConnection().getProperties().getProperty(AvailableSettings.PHYSICAL_NAMING_STRATEGY));
        map.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY, getHibernateConnection().getProperties().getProperty(AvailableSettings.IMPLICIT_NAMING_STRATEGY));
        map.put(AvailableSettings.SCANNER_DISCOVERY, "");	// disable scanning of all classes and hbm.xml files. Only scan speficied packages
        map.put(EnversSettings.AUDIT_TABLE_PREFIX,getHibernateConnection().getProperties().getProperty(EnversSettings.AUDIT_TABLE_PREFIX,""));
        map.put(EnversSettings.AUDIT_TABLE_SUFFIX,getHibernateConnection().getProperties().getProperty(EnversSettings.AUDIT_TABLE_SUFFIX,"_AUD"));
        map.put(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA, getProperty(AvailableSettings.USE_NATIONALIZED_CHARACTER_DATA));

        EntityManagerFactoryBuilderImpl builder = (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(persistenceUnitInfo, map);
        
        return builder;
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
