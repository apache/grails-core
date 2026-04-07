/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateEventListeners
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.JdbcSettings
import org.springframework.context.ApplicationContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import spock.lang.Specification

import javax.sql.DataSource

class HibernateMappingContextConfigurationSpec extends Specification {

    def "test HibernateMappingContextConfiguration defaults"() {
        given: "A new configuration"
        def config = new HibernateMappingContextConfiguration()

        expect: "it has expected default values"
        config.getNamingStrategyProvider() != null
        config.dataSourceName == 'default'
    }

    def "setBytecodeProvider stores the provider and getGrailsBytecodeProvider returns it"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def provider = new GrailsBytecodeProvider()

        when:
        config.setBytecodeProvider(provider)

        then:
        config.getGrailsBytecodeProvider().is(provider)
    }

    def "getGrailsBytecodeProvider creates a new GrailsBytecodeProvider when bytecodeProvider is null"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        expect:
        config.getGrailsBytecodeProvider() instanceof GrailsBytecodeProvider
    }

    def "setNamingStrategyProvider updates the naming strategy provider"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def provider = new NamingStrategyProvider()

        when:
        config.setNamingStrategyProvider(provider)

        then:
        config.getNamingStrategyProvider().is(provider)
    }

    def "getMappingCacheHolder returns null when no HibernateMappingContext is set"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        expect:
        config.getMappingCacheHolder() == null
    }

    def "getMappingCacheHolder delegates to the HibernateMappingContext when set"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def ctx = new HibernateMappingContext()
        config.setHibernateMappingContext(ctx)

        expect:
        config.getMappingCacheHolder() != null
        config.getMappingCacheHolder().is(ctx.getMappingCacheHolder())
    }

    def "setHibernateMappingContext stores the context"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def ctx = new HibernateMappingContext()

        when:
        config.setHibernateMappingContext(ctx)

        then:
        config.getMappingCacheHolder() != null
    }

    def "setSessionFactoryBeanName updates the bean name"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.setSessionFactoryBeanName("mySessionFactory")

        then:
        config.sessionFactoryBeanName == "mySessionFactory"
    }

    def "setDataSourceName updates the data source name"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.setDataSourceName("secondary")

        then:
        config.dataSourceName == "secondary"
    }

    def "setEventListeners stores the listener map"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def listeners = [save: "mySaveListener"]

        when:
        config.setEventListeners(listeners)

        then:
        config.eventListeners == listeners
    }

    def "setHibernateEventListeners stores the HibernateEventListeners instance"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def hel = new HibernateEventListeners()

        when:
        config.setHibernateEventListeners(hel)

        then:
        config.hibernateEventListeners.is(hel)
    }

    def "getServiceRegistry returns null before buildSessionFactory is called"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        expect:
        config.getServiceRegistry() == null
    }

    def "addAnnotatedClass adds a class to additionalClasses"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.addAnnotatedClass(String)

        then:
        noExceptionThrown()
    }

    def "addAnnotatedClasses adds multiple classes in batch"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.addAnnotatedClasses(String, Integer)

        then:
        noExceptionThrown()
    }

    def "addPackages adds multiple packages in batch"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        def result = config.addPackages("java.lang", "java.util")

        then:
        result.is(config)
    }

    def "setApplicationContext with null uses PathMatchingResourcePatternResolver"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.setApplicationContext(null)

        then:
        noExceptionThrown()
    }

    def "setApplicationContext without datasource bean sets session context properties"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> null
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().containsKey("hibernate.current_session_context_class")
        config.getProperties().containsKey("hibernate.bytecode.allow_enhancement_as_proxy")
        config.getProperties().containsKey("hibernate.bytecode.enhancement_metadata_cache")
        config.getProperties().containsKey("hibernate.enhancer.enableLazyInitialization")
        config.getProperties().containsKey("hibernate.enhancer.enableDirtyTracking")
        config.getProperties().containsKey("hibernate.enhancer.enableAssociationManagement")
        !config.getProperties().containsKey(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE)
    }

    def "setApplicationContext with datasource bean injects the datasource into properties"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            getBean("dataSource") >> ds
            getClassLoader() >> null
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE).is(ds)
    }

    def "setApplicationContext with classLoader sets classloaders property"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader cl = new URLClassLoader([] as URL[], Thread.currentThread().contextClassLoader)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> cl
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(cl)
    }

    def "setApplicationContext when datasource property already set does not overwrite it"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource existingDs = Stub(DataSource)
        config.getProperties().put(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, existingDs)
        DataSource anotherDs = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            getBean("dataSource") >> anotherDs
            getClassLoader() >> null
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE).is(existingDs)
    }

    def "setApplicationContext with non-default dataSourceName uses correct bean name"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        config.setDataSourceName("secondary")
        DataSource ds = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource_secondary") >> true
            getBean("dataSource_secondary") >> ds
            getClassLoader() >> null
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE).is(ds)
    }

    def "setDataSourceConnectionSource sets dataSourceName, DataSource, and classLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ConnectionSource<DataSource, DataSourceSettings> connSrc = Stub(ConnectionSource) {
            getName() >> "secondary"
            getSource() >> ds
        }

        when:
        config.setDataSourceConnectionSource(connSrc)

        then:
        config.dataSourceName == "secondary"
        config.getProperties().get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE).is(ds)
        config.getProperties().containsKey("hibernate.current_session_context_class")
        config.getProperties().containsKey(AvailableSettings.CLASSLOADERS)
    }

    def "createBootstrapServiceRegistryBuilder returns a non-null builder"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        def builder = config.createBootstrapServiceRegistryBuilder()

        then:
        builder instanceof BootstrapServiceRegistryBuilder
    }

    def "createStandardServiceRegistryBuilder returns a non-null builder"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder().build()

        when:
        def builder = config.createStandardServiceRegistryBuilder(bsr)

        then:
        builder instanceof StandardServiceRegistryBuilder

        cleanup:
        bsr.close()
    }

    def "matchesFilter returns false for a non-annotated class"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def resolver = new PathMatchingResourcePatternResolver()
        def readerFactory = new CachingMetadataReaderFactory(resolver)
        def resources = resolver.getResources("classpath:org/grails/orm/hibernate/cfg/NamingStrategyProvider.class")

        when:
        boolean matched = false
        for (def resource : resources) {
            if (resource.readable) {
                def reader = readerFactory.getMetadataReader(resource)
                matched = config.matchesFilter(reader, readerFactory)
                break
            }
        }

        then:
        !matched
    }

    def "matchesFilter returns true for an @jakarta.persistence.Entity annotated class"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def resolver = new PathMatchingResourcePatternResolver()
        def readerFactory = new CachingMetadataReaderFactory(resolver)
        def resources = resolver.getResources("classpath*:org/grails/orm/hibernate/cfg/CfgJpaTestEntity.class")

        when:
        boolean matched = false
        for (def resource : resources) {
            if (resource.readable) {
                def reader = readerFactory.getMetadataReader(resource)
                matched = config.matchesFilter(reader, readerFactory)
                break
            }
        }

        then:
        matched
    }

    def "scanPackages on a package with no annotated classes throws no exception"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.scanPackages("java.io")

        then:
        noExceptionThrown()
    }

    def "scanPackages discovers @Entity annotated domain classes and calls addAnnotatedClasses"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.scanPackages("org.grails.orm.hibernate.cfg")

        then:
        noExceptionThrown()
    }
}

class HibernateMappingContextConfigurationIntegrationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HmccTestBook, HmccTestAuthor])
    }

    def "buildSessionFactory produces a working session factory via HibernateDatastore"() {
        expect:
        sessionFactory != null
        !sessionFactory.isClosed()
    }

    def "getServiceRegistry is non-null after the session factory is built"() {
        expect:
        datastore.sessionFactory != null
    }

    def "HibernateDatastore mappingContext is a HibernateMappingContext with registered entities"() {
        when:
        def ctx = mappingContext

        then:
        ctx instanceof HibernateMappingContext
        ctx.getPersistentEntity(HmccTestBook.name) != null
        ctx.getPersistentEntity(HmccTestAuthor.name) != null
    }

    def "HibernateMappingContextConfiguration addAnnotatedClasses is used by buildSessionFactory"() {
        when:
        def entities = mappingContext.persistentEntities

        then:
        !entities.isEmpty()
    }
}

@Entity
class HmccTestBook implements HibernateEntity<HmccTestBook> {
    String title
    HmccTestAuthor author
    static belongsTo = [author: HmccTestAuthor]
}

@Entity
class HmccTestAuthor implements HibernateEntity<HmccTestAuthor> {
    String name
    static hasMany = [books: HmccTestBook]
}

@jakarta.persistence.Entity
class CfgJpaTestEntity {
    @jakarta.persistence.Id
    Long id
    String name
}
