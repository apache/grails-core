/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package grails.test.hibernate

import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.hibernate.Session
import org.hibernate.SessionFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.PropertySource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.DefaultTransactionAttribute

import grails.config.Config
import org.grails.config.PropertySourcesConfig
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.BootstrapContextImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.support.BeanDefinitionRegistry

/**
 * Specification for Hibernate tests
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class HibernateSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager
    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()
    @Shared @AutoCleanup('close') ApplicationContext applicationContext

    static class TestGrailsBytecodeProvider extends GrailsBytecodeProvider {

        @Override
        @CompileStatic(TypeCheckingMode.SKIP)
        protected ByteBuddyProxyHelper createProxyHelper() {
            try {
                def byteBuddyStateClass = Class.forName('org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState')
                def byteBuddyStateConstructor = byteBuddyStateClass.getDeclaredConstructor()
                byteBuddyStateConstructor.setAccessible(true)
                def byteBuddyState = byteBuddyStateConstructor.newInstance()
                return new ByteBuddyProxyHelper(byteBuddyState as org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState)
            } catch (e) {
                throw new RuntimeException('Failed to instantiate ByteBuddyState using reflection', e)
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void setupSpec() {

        List<PropertySourceLoader> propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader, getClass().getClassLoader())
        ResourceLoader resourceLoader = new DefaultResourceLoader()
        MutablePropertySources propertySources = new MutablePropertySources()
        PropertySourceLoader ymlLoader = propertySourceLoaders.find { it.getFileExtensions().toList().contains('yml') }
        if (ymlLoader) {
            load(resourceLoader, ymlLoader, 'application.yml').each {
                propertySources.addLast(it)
            }
        }
        PropertySourceLoader groovyLoader = propertySourceLoaders.find { it.getFileExtensions().toList().contains('groovy') }
        if (groovyLoader) {
            load(resourceLoader, groovyLoader, 'application.groovy').each {
                propertySources.addLast(it)
            }
        }
        propertySources.addFirst(new MapPropertySource('defaults', getConfiguration()))
        Config config = new PropertySourcesConfig(propertySources)
        List<Class> domainClasses = getDomainClasses()

        HibernateDatastoreSpringInitializer initializer
        if (!domainClasses) {
            String packageName = getPackageToScan(config)
            initializer = new HibernateDatastoreSpringInitializer(config, packageName)
        } else {
            initializer = new HibernateDatastoreSpringInitializer(config, domainClasses)
        }

        initializer.beanDefinitions = { ->
            dataSource(org.springframework.jdbc.datasource.DriverManagerDataSource) {
                driverClassName = 'org.h2.Driver'
                url = 'jdbc:h2:mem:test;DB_CLOSE_DELAY=-1'
                username = 'sa'
                password = ''
            }
            hibernateBytecodeProvider(TestGrailsBytecodeProvider)
        }

        applicationContext = initializer.configure()
        hibernateDatastore = applicationContext.getBean(HibernateDatastore)
        transactionManager = hibernateDatastore.getTransactionManager()
    }

    /**
     * The transaction status
     */
    TransactionStatus transactionStatus

    void setup() {
        transactionStatus = transactionManager.getTransaction(new DefaultTransactionAttribute())
    }

    void cleanup() {
        if (isRollback()) {
            transactionManager.rollback(transactionStatus)
        } else {
            transactionManager.commit(transactionStatus)
        }
    }

    /**
     * @return The configuration
     */
    Map<String,Object> getConfiguration() {
        [
            (Settings.SETTING_DB_CREATE)             : 'create-drop',
            'hibernate.proxy_factory_class'          : 'org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory',
            'hibernate.dialect'                      : 'org.hibernate.dialect.H2Dialect',
            'jakarta.persistence.validation.mode'    : 'none'
        ] as Map<String, Object>
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected InFlightMetadataCollectorImpl getCollector() {
        def bootstrapServiceRegistry = getServiceRegistry()
                .getParentServiceRegistry()
                .getParentServiceRegistry() as BootstrapServiceRegistry

        def bytecodeProvider = applicationContext.getBean('hibernateBytecodeProvider')
        def dataSource = applicationContext.getBean('dataSource')

        def serviceRegistry = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySetting('hibernate.dialect', H2Dialect.class.getName())
                .applySetting('jakarta.persistence.jdbc.url', 'jdbc:h2:mem:test;DB_CLOSE_DELAY=-1')
                .applySetting('jakarta.persistence.jdbc.driver', 'org.h2.Driver')
                .applySetting('jakarta.persistence.nonJtaDataSource', dataSource)
                .addService(org.hibernate.bytecode.spi.BytecodeProvider.class, (org.hibernate.bytecode.spi.BytecodeProvider) bytecodeProvider)
                .applySetting('hibernate.bytecode.allow_enhancement_as_proxy', 'false')
                .build()
        def options = new MetadataBuilderImpl(
                new MetadataSources(serviceRegistry)
        ).getMetadataBuildingOptions()
        new InFlightMetadataCollectorImpl(
                new BootstrapContextImpl( serviceRegistry, options)
                , options)
    }

    protected ServiceRegistryImplementor getServiceRegistry() {
        (hibernateDatastore.sessionFactory as SessionFactoryImpl)
                .getServiceRegistry()
    }

    /**
     * @return the current session factory
     */
    SessionFactory getSessionFactory() {
        hibernateDatastore.getSessionFactory()
    }

    /**
     * @return the current Hibernate session
     */
    Session getHibernateSession() {
        getSessionFactory().getCurrentSession()
    }

    /**
     * Whether to rollback on each test (defaults to true)
     */
    boolean isRollback() {
        return true
    }

    /**
     * @return The domain classes
     */
    List<Class> getDomainClasses() { [] }

    /**
     * Obtains the default package to scan
     *
     * @param config The configuration
     * @return The package to scan
     */
    protected String getPackageToScan(Config config) {
        config.getProperty('grails.codegen.defaultPackage', getClass().package.name)
    }

    private List<PropertySource> load(ResourceLoader resourceLoader, PropertySourceLoader loader, String filename) {
        if (canLoadFileExtension(loader, filename)) {
            Resource appYml = resourceLoader.getResource(filename)
            return loader.load(appYml.getDescription(), appYml) as List<PropertySource>
        } else {
            return Collections.emptyList()
        }
    }

    private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
        return Arrays
            .stream(loader.fileExtensions)
            .map { String extension -> extension.toLowerCase() }
            .anyMatch { String extension -> name.toLowerCase().endsWith(extension) }
    }
}
