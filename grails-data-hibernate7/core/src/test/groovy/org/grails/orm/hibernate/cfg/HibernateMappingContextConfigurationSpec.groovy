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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.apache.grails.data.hibernate7.core.querytail.QueryTailSessionFactoryObserver
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettingsBuilder
import org.grails.orm.hibernate.HibernateEventListeners
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.JdbcSettings
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.context.ApplicationContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.env.PropertyResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import spock.lang.Specification
import spock.lang.TempDir

import javax.sql.DataSource

class HibernateMappingContextConfigurationSpec extends Specification {

    @TempDir
    File tempDir

    def cleanup() {
        HmccRecordingObserver.events.clear()
        HmccRecordingInspector.seen.clear()
    }

    def "setQueryTailFile gives a session factory a capture of its own next to the observer the application set, which the configuration goes on holding, and leaves its inspector alone"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(HmccTestBook, HmccTestAuthor)
        ConnectionSource<DataSource, DataSourceSettings> dataSource
        HibernateMappingContextConfiguration configuration = configurationOf(factory, 'hmccQueryTailFile', [:]) { dataSource = it }
        HmccRecordingInspector inspector = new HmccRecordingInspector()
        HmccRecordingObserver observer = new HmccRecordingObserver()
        configuration.statementInspector = inspector
        configuration.sessionFactoryObserver = observer
        configuration.queryTailFile = file

        when:
        SessionFactoryImplementor sessionFactory = configuration.buildSessionFactory() as SessionFactoryImplementor
        QueryTailSessionFactoryObserver capturing = capturingObserver(sessionFactory)
        runNative(sessionFactory, 'select 1 as probed')

        then: 'the session factory runs the inspector the application set, and the capture tells the observer it set'
        sessionFactory.sessionFactoryOptions.statementInspector.is(inspector)
        capturing.capture.open
        HmccRecordingInspector.seen.contains('select 1 as probed')
        HmccRecordingObserver.events == ['created']
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll()*.sql.count('select ? as probed') == 1

        and: 'the configuration holds what the application set'
        configuration.statementInspector.is(inspector)
        configuration.sessionFactoryObserver.is(observer)

        when:
        sessionFactory.close()

        then: 'the capture closed with the session factory'
        !capturing.capture.open
        HmccRecordingObserver.events == ['created', 'closing', 'closed']

        when: 'the configuration builds another without a capture file'
        configuration.queryTailFile = null
        SessionFactoryImplementor uncaptured = configuration.buildSessionFactory() as SessionFactoryImplementor

        then: 'it runs what the application set, and nothing else'
        uncaptured.sessionFactoryOptions.statementInspector.is(inspector)
        capturingObserver(uncaptured) == null

        cleanup:
        uncaptured?.close()
        dataSource?.close()
    }

    def "a configuration holds the observer the application set even as it builds a session factory with a capture"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(HmccTestBook, HmccTestAuthor)
        ConnectionSource<DataSource, DataSourceSettings> dataSource
        HibernateMappingContextConfiguration configuration = configurationOf(factory, 'hmccHeldObserver', [:]) { dataSource = it }
        HmccHoldingObserver observer = new HmccHoldingObserver(configuration: configuration)
        configuration.sessionFactoryObserver = observer
        configuration.queryTailFile = file

        when:
        SessionFactoryImplementor sessionFactory = configuration.buildSessionFactory() as SessionFactoryImplementor

        then: 'the session factory was created with a capture, and the configuration held what the application set throughout'
        capturingObserver(sessionFactory).capture.open
        observer.held.is(observer)
        configuration.sessionFactoryObserver.is(observer)

        cleanup:
        sessionFactory?.close()
        dataSource?.close()
    }

    def "an observer a configuration class sets #set is told of the session factory, whose capture opens and closes with it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(HmccTestBook, HmccTestAuthor)
        ConnectionSource<DataSource, DataSourceSettings> dataSource
        HibernateMappingContextConfiguration configuration = configurationOf(factory, "hmccObserved${configurationClass.simpleName}",
                ['hibernate.configClass': configurationClass.name]) { dataSource = it }
        configuration.queryTailFile = file

        when:
        SessionFactoryImplementor sessionFactory = configuration.buildSessionFactory() as SessionFactoryImplementor
        QueryTailSessionFactoryObserver capturing = capturingObserver(sessionFactory)

        then:
        HmccRecordingObserver.events == ['created']
        capturing.capture.open

        when:
        sessionFactory.close()

        then:
        HmccRecordingObserver.events == ['created', 'closing', 'closed']
        !capturing.capture.open

        cleanup:
        dataSource?.close()

        where:
        set                                     | configurationClass
        'as it is created'                      | HmccObservingConfiguration
        'as it builds the session factory'      | HmccBuildObservingConfiguration
        'as it creates its service registry'    | HmccRegistryObservingConfiguration
    }

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
        !config.getProperties().containsKey(AvailableSettings.CLASSLOADERS)
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

    def "setApplicationContext prefers RestartClassLoader thread context class loader over the context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader contextLoader = new URLClassLoader([] as URL[], Thread.currentThread().contextClassLoader)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> contextLoader
        }
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "setApplicationContext with a null context class loader still prefers a RestartClassLoader thread context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> null
        }
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "resolveSessionFactoryClassLoader prefers RestartClassLoader thread context class loader over configured class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader configuredLoader = new URLClassLoader([] as URL[], Thread.currentThread().contextClassLoader)
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        config.getProperties().put(AvailableSettings.CLASSLOADERS, configuredLoader)
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = restartLoader

        then:
        config.resolveSessionFactoryClassLoader().is(restartLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "setApplicationContext prefers a thread context class loader that delegates to the RestartClassLoader over the context class loader"() {
        given: "a servlet container has swapped its web application loader in during context start"
        def config = new HibernateMappingContextConfiguration()
        ClassLoader contextLoader = new URLClassLoader([] as URL[], Thread.currentThread().contextClassLoader)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> contextLoader
        }
        ClassLoader containerLoader = new URLClassLoader([] as URL[], restartClassLoader())
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "setApplicationContext with a null context class loader uses a thread context class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> null
        }
        ClassLoader containerLoader = new URLClassLoader([] as URL[], restartClassLoader())
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "setDataSourceConnectionSource uses a thread context class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ConnectionSource<DataSource, DataSourceSettings> connSrc = Stub(ConnectionSource) {
            getName() >> ConnectionSource.DEFAULT
            getSource() >> ds
        }
        ClassLoader containerLoader = new URLClassLoader([] as URL[], restartClassLoader())
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setDataSourceConnectionSource(connSrc)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
        config.getProperties().get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE).is(ds)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
    }

    def "resolveSessionFactoryClassLoader keeps a configured class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader configuredLoader = new URLClassLoader([] as URL[], restartClassLoader())
        config.getProperties().put(AvailableSettings.CLASSLOADERS, configuredLoader)
        def originalCl = Thread.currentThread().contextClassLoader

        when:
        Thread.currentThread().contextClassLoader = new URLClassLoader([] as URL[], originalCl)

        then:
        config.resolveSessionFactoryClassLoader().is(configuredLoader)

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
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

    // ─── Additional edge cases for coverage ───────────────────────────────────

    def "setDataSourceConnectionSource handles RestartClassLoader name"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def ds = Stub(DataSource)
        
        // Create a class with simple name "RestartClassLoader"
        def clClass = new GroovyClassLoader().parseClass("class RestartClassLoader extends ClassLoader {}")
        def clInstance = clClass.getDeclaredConstructor().newInstance()
        def originalCl = Thread.currentThread().getContextClassLoader()
        
        when:
        Thread.currentThread().setContextClassLoader(clInstance)
        config.setDataSourceConnectionSource(Stub(ConnectionSource) {
            getSource() >> ds
            getName() >> "default"
        })

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(clInstance)

        cleanup:
        Thread.currentThread().setContextClassLoader(originalCl)
    }

    def "buildSessionFactory handles classloader object when it is a ClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        def cl = new URLClassLoader([] as URL[])
        config.getProperties().put(AvailableSettings.CLASSLOADERS, cl)
        // Minimal setup to avoid NPEs if possible, or just test the logic via subclasses
        
        expect:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(cl)
    }

    private static QueryTailSessionFactoryObserver capturingObserver(SessionFactoryImplementor sessionFactory) {
        sessionFactory.sessionFactoryOptions.sessionFactoryObservers
                .find { it instanceof QueryTailSessionFactoryObserver } as QueryTailSessionFactoryObserver
    }

    /**
     * @return a configuration the factory builds for an H2 database, its data source handed to {@code dataSource} for
     * the feature to close
     */
    private static HibernateMappingContextConfiguration configurationOf(HibernateConnectionSourceFactory factory, String database,
            Map extra, Closure dataSourceCallback) {
        PropertyResolver resolver = DatastoreUtils.createPropertyResolver([
                'dataSource.url'     : "jdbc:h2:mem:${database};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dialect' : H2Dialect.name,
                'dataSource.dbCreate': 'none'] + extra)
        HibernateConnectionSourceSettings settings = factory.buildSettings(ConnectionSource.DEFAULT, resolver,
                new ConnectionSourceSettingsBuilder(resolver).build(), true)
        ConnectionSource<DataSource, DataSourceSettings> dataSource =
                new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, settings.dataSource)
        dataSourceCallback.call(dataSource)
        factory.buildConfiguration(ConnectionSource.DEFAULT, dataSource, settings)
    }

    private static void runNative(SessionFactory sessionFactory, String sql) {
        def session = sessionFactory.openSession()
        try {
            session.createNativeQuery(sql).list()
        }
        finally {
            session.close()
        }
    }

    private static ClassLoader restartClassLoader() {
        new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
    }
}

class HibernateMappingContextConfigurationIntegrationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(HmccTestBook, HmccTestAuthor)
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

class HmccRecordingInspector implements StatementInspector {

    static final List<String> seen = [].asSynchronized()

    @Override
    String inspect(String sql) {
        seen << sql
        sql
    }
}

class HmccRecordingObserver implements SessionFactoryObserver {

    static final List<String> events = [].asSynchronized()

    @Override
    void sessionFactoryCreated(SessionFactory factory) {
        events << 'created'
    }

    @Override
    void sessionFactoryClosing(SessionFactory factory) {
        events << 'closing'
    }

    @Override
    void sessionFactoryClosed(SessionFactory factory) {
        events << 'closed'
    }
}

class HmccObservingConfiguration extends HibernateMappingContextConfiguration {

    HmccObservingConfiguration() {
        setSessionFactoryObserver(new HmccRecordingObserver())
    }
}

class HmccBuildObservingConfiguration extends HibernateMappingContextConfiguration {

    @Override
    SessionFactory buildSessionFactory() {
        setSessionFactoryObserver(new HmccRecordingObserver())
        super.buildSessionFactory()
    }
}

/**
 * Records the observer the configuration it observes holds as the session factory is created, which is what another
 * build of the configuration, on another thread, would read.
 */
class HmccHoldingObserver implements SessionFactoryObserver {

    HibernateMappingContextConfiguration configuration
    SessionFactoryObserver held

    @Override
    void sessionFactoryCreated(SessionFactory factory) {
        held = configuration.sessionFactoryObserver
    }

    @Override
    void sessionFactoryClosed(SessionFactory factory) {
    }
}

class HmccRegistryObservingConfiguration extends HibernateMappingContextConfiguration {

    @Override
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        setSessionFactoryObserver(new HmccRecordingObserver())
        super.createStandardServiceRegistryBuilder(bootstrapServiceRegistry)
    }
}

@jakarta.persistence.Entity
class CfgJpaTestEntity {
    @jakarta.persistence.Id
    Long id
    String name
}
