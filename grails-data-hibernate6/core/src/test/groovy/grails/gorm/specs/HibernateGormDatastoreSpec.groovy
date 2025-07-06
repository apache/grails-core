package grails.gorm.specs

import org.apache.grails.data.hibernate6.core.GrailsDataHibernate6TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.BootstrapContextImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.spi.MetadataContributor
import org.hibernate.dialect.H2Dialect
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.service.spi.ServiceRegistryImplementor

/**
 * The original GormDataStoreSpec destroyed the setup
 * between tests instead of at the end of all tests
 * It also wqs default configured for H2 which
 * made it break with some Java types.
 * Finally, it loaded all the test Entities,
 * now it can be setup individually.
 */
class HibernateGormDatastoreSpec extends GrailsDataTckSpec<GrailsDataHibernate6TckManager> {

    void setupSpec() {
        manager.grailsConfig = [
                'dataSource.url'               : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'          : 'create-drop',
                'dataSource.formatSql'         : 'true',
                'dataSource.logSql'            : 'true',
                'hibernate.flush.mode'         : 'COMMIT',
                'hibernate.cache.queries'      : 'true',
                'hibernate.hbm2ddl.auto'       : 'create',
                'hibernate.jpa.compliance.cascade': 'true',
        ]
    }

    HibernatePersistentEntity createPersistentEntity(String className
                                                     , Map<String, Class> fieldProperties
                                                     , Map<String, String> staticMapping

    ) {
        def classLoader = new GroovyClassLoader()
        def classText = """
        package foo
        import grails.gorm.annotation.Entity
        import grails.gorm.hibernate.HibernateEntity
        @Entity
        class ${className} implements HibernateEntity<${className}> {

            ${fieldProperties.collect { name, type -> "${type.simpleName} ${name}" }.join('\n            ')}

            static mapping = {
                ${staticMapping.collect { name, value -> "${name} ${value}" }.join('\n            ')}
            }
        }
    """

        def clazz = classLoader.parseClass(classText)
        getMappingContext().addPersistentEntity(clazz) as HibernatePersistentEntity
    }

    protected InFlightMetadataCollectorImpl getCollector() {
        def bootstrapServiceRegistry = getServiceRegistry()
                .getParentServiceRegistry()
                .getParentServiceRegistry() as BootstrapServiceRegistry
        def serviceRegistry = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySetting("hibernate.dialect", H2Dialect.class.getName())
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .build()
        def options = new MetadataBuilderImpl(
                new MetadataSources(serviceRegistry)
        ).getMetadataBuildingOptions()
        new InFlightMetadataCollectorImpl(
                new BootstrapContextImpl( serviceRegistry, options)
                , options);
    }

    protected HibernateMappingContext getMappingContext() {
        manager.hibernateDatastore.getMappingContext()
    }

    protected GrailsDomainBinder getGrailsDomainBinder() {
        def registry = getServiceRegistry()
        registry
                .getParentServiceRegistry()
                .getService(ClassLoaderService.class)
                .loadJavaServices(MetadataContributor.class)
                .find { it instanceof GrailsDomainBinder }
    }

    protected ServiceRegistryImplementor getServiceRegistry() {
        (manager.hibernateDatastore.sessionFactory as SessionFactoryImpl)
                .getServiceRegistry()
    }
}
