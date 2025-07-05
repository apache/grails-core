package org.grails.orm.hibernate.cfg

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.BootstrapContextImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.hibernate.dialect.H2Dialect
import org.hibernate.boot.spi.MetadataContributor

class GrailsDomainBinderSpec extends HibernateGormDatastoreSpec {

    void "Test save()"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()
        def persistentEntity = createPersistentEntity("Book", [title: String, author: String, publishedYear: Integer])
        grailsDomainBinder.bindRoot(persistentEntity, collector,"sessionFactoryName")
        println("when")
        then:
        1 == 1
    }

    HibernatePersistentEntity createPersistentEntity(String className, Map<String, Class> properties) {
        def classLoader = new GroovyClassLoader()
        def classText = """
        import grails.gorm.annotation.Entity
        import grails.gorm.hibernate.HibernateEntity
        @Entity
        class ${className} implements HibernateEntity<${className}> {
            ${properties.collect { name, type -> "${type.simpleName} ${name}" }.join('\n            ')}

            static mapping = {
                id generator: 'native'
                version false
            }
        }
    """

        def clazz = classLoader.parseClass(classText)
        getMappingContext().addPersistentEntity(clazz) as HibernatePersistentEntity
    }

    private InFlightMetadataCollectorImpl getCollector() {
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

    private HibernateMappingContext getMappingContext() {
        manager.hibernateDatastore.getMappingContext()
    }

    private GrailsDomainBinder getGrailsDomainBinder() {
        def registry = getServiceRegistry()
        registry
                .getParentServiceRegistry()
                .getService(ClassLoaderService.class)
                .loadJavaServices(MetadataContributor.class)
                .find { it instanceof GrailsDomainBinder }
    }

    private ServiceRegistryImplementor getServiceRegistry() {
        (manager.hibernateDatastore.sessionFactory as SessionFactoryImpl)
                .getServiceRegistry()
    }
}