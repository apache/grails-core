/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.orm.bootstrap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.bootstrap.AbstractDatastoreInitializer
import org.grails.datastore.gorm.jdbc.connections.CachedDataSourceConnectionSourceFactory
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.config.DatastoreServiceMethodInvokingFactoryBean
import org.grails.datastore.mapping.core.connections.AbstractConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.grails.orm.hibernate.support.HibernateDatastoreConnectionSourcesRegistrar
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.PlatformTransactionManager

import javax.sql.DataSource

/**
 * Class that handles the details of initializing GORM for Hibernate
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@Slf4j
class HibernateDatastoreSpringInitializer extends AbstractDatastoreInitializer {
    public static final String SESSION_FACTORY_BEAN_NAME = "sessionFactory"
    public static final String DEFAULT_DATA_SOURCE_NAME = Settings.SETTING_DATASOURCE
    public static final String DATA_SOURCES = Settings.SETTING_DATASOURCES;
    public static final String TEST_DB_URL = "jdbc:h2:mem:grailsDb;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"

    String defaultDataSourceBeanName = ConnectionSource.DEFAULT
    String defaultSessionFactoryBeanName = SESSION_FACTORY_BEAN_NAME
    Set<String> dataSources = [defaultDataSourceBeanName] as Set<String>
    boolean enableReload = false
    boolean grailsPlugin = false

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, String... packages) {
        super(configuration, packages)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(Map configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(this.configuration)
    }

    HibernateDatastoreSpringInitializer(Map configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(this.configuration)
    }


    @CompileStatic
    void configureDataSources(PropertyResolver config) {

        Set<String> dataSourceNames = new HashSet<String>()

        if(config == null) {
            dataSourceNames = [defaultDataSourceBeanName] as Set
        }
        else {
            Map dataSources = config.getProperty(DATA_SOURCES, Map.class, Collections.emptyMap())

            if (dataSources != null && !dataSources.isEmpty()) {
                dataSourceNames.addAll( AbstractConnectionSources.toValidConnectionSourceNames(dataSources) )
            }
            Map dataSource = (Map)config.getProperty(DEFAULT_DATA_SOURCE_NAME, Map.class, Collections.emptyMap())
            if (dataSource != null && !dataSource.isEmpty()) {
                dataSourceNames.add( ConnectionSource.DEFAULT )
            }
        }
        this.dataSources = dataSourceNames
    }

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        getClass().classLoader.loadClass('org.grails.plugin.hibernate.support.HibernatePersistenceContextInterceptor')
    }

    /**
     * Configures an in-memory test data source, don't use in production
     */
    @Override
    ApplicationContext configure() {
        GenericApplicationContext applicationContext = createApplicationContext()
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }

    protected String getTestDbUrl() {
        TEST_DB_URL
    }

    @CompileStatic
    ApplicationContext configureForDataSource(DataSource dataSource) {
        GenericApplicationContext applicationContext = createApplicationContext()
        applicationContext.beanFactory.registerSingleton(DEFAULT_DATA_SOURCE_NAME, dataSource)
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }

    public Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        ApplicationEventPublisher eventPublisher = super.findEventPublisher(beanDefinitionRegistry)
        Closure beanDefinitions = {
            def common = getCommonConfiguration(beanDefinitionRegistry, "hibernate")
            common.delegate = delegate
            common.call()

            // for unwrapping / inspecting proxies
            hibernateProxyHandler(HibernateProxyHandler)

            def config = this.configuration
            final boolean isGrailsPresent = isGrailsPresent()
            dataSourceConnectionSourceFactory(CachedDataSourceConnectionSourceFactory)
            hibernateConnectionSourceFactory(HibernateConnectionSourceFactory, persistentClasses as Class[]) { bean ->
                bean.autowire = true
                dataSourceConnectionSourceFactory = ref('dataSourceConnectionSourceFactory')
            }
            hibernateDatastore(HibernateDatastore, config, hibernateConnectionSourceFactory, eventPublisher) { bean->
                bean.primary = true
            }
            sessionFactory(hibernateDatastore:'getSessionFactory') { bean->
                bean.primary = true
            }
            transactionManager(hibernateDatastore:"getTransactionManager") { bean->
                bean.primary = true
            }
            autoTimestampEventListener(hibernateDatastore:"getAutoTimestampEventListener")
            getBeanDefinition("transactionManager").beanClass = PlatformTransactionManager
            hibernateDatastoreConnectionSourcesRegistrar(HibernateDatastoreConnectionSourcesRegistrar, dataSources)
            // domain model mapping context, used for configuration
            grailsDomainClassMappingContext(hibernateDatastore:"getMappingContext")

            loadDataServices(null)
                    .each {serviceName, serviceClass->
                        "$serviceName"(DatastoreServiceMethodInvokingFactoryBean, serviceClass) {
                            targetObject = ref("hibernateDatastore")
                            targetMethod = 'getService'
                            arguments = [serviceClass]
                        }
                    }

            if(isGrailsPresent) {
                if(ClassUtils.isPresent("org.grails.plugin.hibernate.support.AggregatePersistenceContextInterceptor")) {
                    ClassLoader cl = ClassUtils.getClassLoader()
                    persistenceInterceptor(cl.loadClass("org.grails.plugin.hibernate.support.AggregatePersistenceContextInterceptor"), ref("hibernateDatastore"))
                    proxyHandler(cl.loadClass("org.grails.datastore.gorm.proxy.ProxyHandlerAdapter"), ref('hibernateProxyHandler'))
                }


                boolean osivEnabled = config.getProperty("hibernate.osiv.enabled", Boolean, true)
                boolean isWebApplication = beanDefinitionRegistry?.containsBeanDefinition("dispatcherServlet") ||
                        beanDefinitionRegistry?.containsBeanDefinition("grailsControllerHelper")

                if (isWebApplication && osivEnabled && ClassUtils.isPresent("org.grails.plugin.hibernate.support.GrailsOpenSessionInViewInterceptor")) {
                    ClassLoader cl = ClassUtils.getClassLoader()
                    openSessionInViewInterceptor(cl.loadClass("org.grails.plugin.hibernate.support.GrailsOpenSessionInViewInterceptor")) {
                        hibernateDatastore = ref("hibernateDatastore")
                    }
                }
            }
        }
        return beanDefinitions
    }


    protected GenericApplicationContext createApplicationContext() {
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        if (configuration instanceof ConfigurableEnvironment) {
            applicationContext.environment = (ConfigurableEnvironment) configuration
        }
        applicationContext
    }

}
