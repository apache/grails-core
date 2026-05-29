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
package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.orm.hibernate.HibernateEventListeners
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.hibernate.Interceptor
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.H2Dialect
import org.springframework.context.ApplicationContext
import org.springframework.context.support.StaticMessageSource

/**
 * Specs for {@link HibernateConnectionSourceFactory} using the shared H2 datastore
 * infrastructure from {@link HibernateGormDatastoreSpec}.
 */
class HibernateConnectionSourceFactorySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Foo])
    }

    private static Map<String, String> h2Config() {
        [
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'dataSource.formatSql'   : 'true',
            'hibernate.flush.mode'   : 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto' : 'create',
        ]
    }

    void "Test hibernate connection factory creates an open session factory"() {
        when: "A factory is used to create a session factory"
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(h2Config()))
        def query = connectionSource.source.getCriteriaBuilder().createQuery(Foo)
        query.select(query.from(Foo))

        then: "The session factory is created and queryable"
        connectionSource.source.openSession().createQuery(query).list().size() == 0

        when: "The connection source is closed"
        connectionSource.close()

        then: "The session factory is closed"
        connectionSource.source.isClosed()
    }

    void "getPersistentClasses returns the classes passed to the constructor"() {
        when:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)

        then:
        factory.persistentClasses == [Foo] as Class[]
    }

    void "getMappingContext is a HibernateMappingContext populated with the entity after create()"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(h2Config()))

        expect:
        factory.mappingContext instanceof HibernateMappingContext
        factory.mappingContext.getPersistentEntity(Foo.name) != null

        cleanup:
        connectionSource?.close()
    }

    void "create() with a named connection source propagates the name"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create("secondary", DatastoreUtils.createPropertyResolver(h2Config()))

        expect:
        connectionSource.name == "secondary"

        cleanup:
        connectionSource?.close()
    }

    void "buildConfiguration throws ConfigurationException for a non-HibernateMappingContextConfiguration configClass"() {
        given: "Settings with a configClass that is not a subclass of HibernateMappingContextConfiguration"
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()
        settings.hibernate.configClass = Configuration  // plain Configuration, not the subclass

        // Provide a minimal DataSource connection source to drive buildConfiguration
        def dsConfig = [
            'dataSource.url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'   : 'update',
            'dataSource.dialect'    : H2Dialect.name,
            'hibernate.hbm2ddl.auto': 'create',
        ]
        def dataSourceCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(dsConfig))

        when:
        factory.buildConfiguration(ConnectionSource.DEFAULT, dataSourceCs, settings)

        then:
        thrown(ConfigurationException)
    }

    void "setMessageSource stores the provided message source"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def source = new StaticMessageSource()

        when:
        factory.setMessageSource(source)

        then:
        factory.messageSource.is(source)
    }

    void "the shared datastore mapping context has Foo registered as a persistent entity"() {
        expect:
        getMappingContext().getPersistentEntity(Foo.name) != null
    }

    void "getBytecodeProvider returns the provider passed to the constructor"() {
        given:
        def provider = new GrailsBytecodeProvider()
        def factory = new HibernateConnectionSourceFactory(provider, Foo)

        expect:
        factory.getBytecodeProvider().is(provider)
    }

    void "setHibernateEventListeners stores the event listeners"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def listeners = new HibernateEventListeners()

        when:
        factory.setHibernateEventListeners(listeners)

        then:
        factory.@hibernateEventListeners.is(listeners)
    }

    void "setInterceptor stores the interceptor"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def interceptor = Mock(Interceptor)

        when:
        factory.setInterceptor(interceptor)

        then:
        factory.@interceptor.is(interceptor)
    }

    void "setDataSourceConnectionSourceFactory stores the factory"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def dscFactory = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()

        when:
        factory.setDataSourceConnectionSourceFactory(dscFactory)

        then:
        factory.@dataSourceConnectionSourceFactory.is(dscFactory)
    }

    void "getConnectionSourcesConfigurationKey returns SETTING_DATASOURCES"() {
        expect:
        new HibernateConnectionSourceFactory(Foo).getConnectionSourcesConfigurationKey() == Settings.SETTING_DATASOURCES
    }

    void "setApplicationContext stores the context and uses it as messageSource"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def ctx = Mock(ApplicationContext)

        when:
        factory.setApplicationContext(ctx)

        then:
        factory.@applicationContext.is(ctx)
        factory.@messageSource.is(ctx)
    }

    void "buildRuntimeSettings builds HibernateConnectionSourceSettings from PropertyResolver"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def resolver = DatastoreUtils.createPropertyResolver(h2Config())

        when:
        def settings = factory.buildRuntimeSettings(ConnectionSource.DEFAULT, resolver, null)

        then:
        settings != null
        settings instanceof HibernateConnectionSourceSettings
    }

    void "buildSettings for default datasource builds settings without prefix"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def resolver = DatastoreUtils.createPropertyResolver(h2Config())

        when:
        def settings = factory.buildSettings(ConnectionSource.DEFAULT, resolver, null, true)

        then:
        settings != null
        settings instanceof HibernateConnectionSourceSettings
    }

    void "buildSettings for named datasource builds settings with datasource prefix"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def resolver = DatastoreUtils.createPropertyResolver(h2Config())

        when:
        def settings = factory.buildSettings('secondary', resolver, null, false)

        then:
        settings != null
        settings instanceof HibernateConnectionSourceSettings
    }

    void "buildConfiguration with a naming strategy configures it on the configuration"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()
        settings.hibernate.naming_strategy = PhysicalNamingStrategyStandardImpl
        def dsConfig = DatastoreUtils.createPropertyResolver([
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'hibernate.hbm2ddl.auto' : 'create',
        ])
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, dsConfig)

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
    }

    void "buildConfiguration with annotatedClasses, annotatedPackages, packagesToScan"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()
        settings.hibernate.annotatedClasses = [Foo]
        settings.hibernate.annotatedPackages = ["org.grails.orm.hibernate.connections"]
        settings.hibernate.packagesToScan = ["org.grails.orm.hibernate.connections"]
        def dsConfig = DatastoreUtils.createPropertyResolver([
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'hibernate.hbm2ddl.auto' : 'create',
        ])
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, dsConfig)

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
    }

    void "buildConfiguration with mappingLocations and mappingDirectoryLocations"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()
        def xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE hibernate-mapping PUBLIC "-//Hibernate/Hibernate Mapping DTD 3.0//EN" "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
<hibernate-mapping>
</hibernate-mapping>"""
        def goodResource = Mock(org.springframework.core.io.Resource) {
            getInputStream() >> { new ByteArrayInputStream(xml.bytes) }
            getURL() >> { new java.net.URL("file:///dummy") }
        }
        def goodDirResource = Mock(org.springframework.core.io.Resource) {
            getFile() >> java.nio.file.Files.createTempDirectory("hbm-dir").toFile()
        }
        def badDirResource = Mock(org.springframework.core.io.Resource) {
            getFile() >> java.nio.file.Files.createTempFile("hbm-dir", ".txt").toFile()
        }
        settings.hibernate.mappingLocations = [goodResource] as org.springframework.core.io.Resource[]
        settings.hibernate.mappingDirectoryLocations = [goodDirResource] as org.springframework.core.io.Resource[]
        
        def dsConfig = DatastoreUtils.createPropertyResolver([
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'hibernate.hbm2ddl.auto' : 'create',
        ])
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, dsConfig)

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
        
        when: "using a bad directory location"
        settings.hibernate.mappingDirectoryLocations = [badDirResource] as org.springframework.core.io.Resource[]
        factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)
        
        then:
        thrown(IllegalArgumentException)
    }

    void "buildSettingsWithPrefix with empty prefix and nested properties"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def config = h2Config() + [
            "secondary.dataSource.url": "jdbc:h2:mem:secondaryDB",
        ]
        def resolver = DatastoreUtils.createPropertyResolver(config)

        when:
        def settings = factory.buildSettings('secondary', resolver, null, false)

        then:
        settings != null
    }

    void "buildConfiguration with an interceptor applies it to the configuration"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def interceptor = Mock(Interceptor)
        factory.setInterceptor(interceptor)
        def settings = new HibernateConnectionSourceSettings()
        def dsConfig = DatastoreUtils.createPropertyResolver([
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'hibernate.hbm2ddl.auto' : 'create',
        ])
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, dsConfig)

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
    }

    // -------------------------------------------------------------------------
    // buildConfiguration — applicationContext != null branch (L184)
    // -------------------------------------------------------------------------

    void "buildConfiguration applies applicationContext when set"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def ctx = Mock(org.springframework.context.ConfigurableApplicationContext) {
            containsBean(_) >> false
            getAutowireCapableBeanFactory() >> Mock(org.springframework.beans.factory.config.AutowireCapableBeanFactory)
        }
        factory.setApplicationContext(ctx)
        def settings = new HibernateConnectionSourceSettings()
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver([
                'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect'     : H2Dialect.name,
                'hibernate.hbm2ddl.auto' : 'create',
            ]))

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
    }

    // -------------------------------------------------------------------------
    // buildConfiguration — hibernateEventListeners != null branch (L209)
    // -------------------------------------------------------------------------

    void "buildConfiguration uses factory hibernateEventListeners when set"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def listeners = new HibernateEventListeners()
        factory.setHibernateEventListeners(listeners)
        def settings = new HibernateConnectionSourceSettings()
        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver([
                'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect'     : H2Dialect.name,
                'hibernate.hbm2ddl.auto' : 'create',
            ]))

        when:
        def config = factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        config != null
    }

    // -------------------------------------------------------------------------
    // extractDataSourceFallback — HibernateConnectionSourceSettings branch (L136)
    // -------------------------------------------------------------------------

    void "buildSettings propagates DataSource from HibernateConnectionSourceSettings fallback"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def resolver = DatastoreUtils.createPropertyResolver(h2Config())
        def fallback = new HibernateConnectionSourceSettings()
        fallback.dataSource.url = "jdbc:h2:mem:fallbackDB"

        when: "fallbackSettings is a HibernateConnectionSourceSettings — extractDataSourceFallback first branch"
        def settings = factory.buildSettings(ConnectionSource.DEFAULT, resolver, fallback, true)

        then:
        settings != null
    }

    // -------------------------------------------------------------------------
    // extractDataSourceFallback — DataSourceSettings branch (L139)
    // -------------------------------------------------------------------------

    void "buildRuntimeSettings with DataSourceSettings fallback hits second extractDataSourceFallback branch"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def resolver = DatastoreUtils.createPropertyResolver(h2Config())
        def dsFallback = new org.grails.datastore.gorm.jdbc.connections.DataSourceSettings()
        dsFallback.url = "jdbc:h2:mem:fallbackDB2"

        when: "fallbackSettings is a plain DataSourceSettings — extractDataSourceFallback second branch"
        def settings = factory.buildRuntimeSettings(ConnectionSource.DEFAULT, resolver, dsFallback)

        then:
        settings != null
    }

    // -------------------------------------------------------------------------
    // applyResources — IOException catch branch (L106-L110)
    // -------------------------------------------------------------------------

    void "buildConfiguration wraps IOException from bad config location in ConfigurationException"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()

        def badResource = Mock(org.springframework.core.io.Resource) {
            getURL() >> { throw new IOException("bad URL") }
            getFilename() >> "bad.cfg.xml"
        }
        settings.hibernate.configLocations = [badResource] as org.springframework.core.io.Resource[]

        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver([
                'dataSource.url'     : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect' : H2Dialect.name,
            ]))

        when:
        factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        thrown(ConfigurationException)
    }

    // -------------------------------------------------------------------------
    // configureNamingStrategy — Throwable catch branch (L123-L124)
    // -------------------------------------------------------------------------

    void "buildConfiguration wraps Throwable from bad naming strategy in ConfigurationException"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()

        // Use a class that IS a PhysicalNamingStrategy subtype but throws in configure path
        // — actually BeanUtils.instantiateClass on a class with no default constructor throws
        settings.hibernate.naming_strategy = BrokenNamingStrategy

        def dsCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver([
                'dataSource.url'     : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect' : H2Dialect.name,
            ]))

        when:
        factory.buildConfiguration(ConnectionSource.DEFAULT, dsCs, settings)

        then:
        thrown(ConfigurationException)
    }

    // -------------------------------------------------------------------------
    // buildSettings — non-empty datasources.dataSource qualified config (L303-L304)
    // -------------------------------------------------------------------------

    void "buildSettings for default datasource applies datasources.dataSource qualified settings when present"() {
        given:
        def factory = new HibernateConnectionSourceFactory(Foo)
        def config = h2Config() + [
            ("${Settings.SETTING_DATASOURCES}.${Settings.SETTING_DATASOURCE}.url".toString()): "jdbc:h2:mem:qualifiedDB",
        ]
        def resolver = DatastoreUtils.createPropertyResolver(config)

        when:
        def settings = factory.buildSettings(ConnectionSource.DEFAULT, resolver, null, true)

        then:
        settings != null
    }
}

@Entity
class Foo {
    String name
}

/**
 * A PhysicalNamingStrategy with no default constructor — instantiating it via BeanUtils throws,
 * which exercises the catch(Throwable) branch in configureNamingStrategy.
 */
class BrokenNamingStrategy extends PhysicalNamingStrategyStandardImpl {
    BrokenNamingStrategy(String requiredArg) {}
}
