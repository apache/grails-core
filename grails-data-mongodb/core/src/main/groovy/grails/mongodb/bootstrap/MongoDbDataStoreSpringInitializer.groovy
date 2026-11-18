/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.mongodb.bootstrap

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.util.ClassUtils

import grails.mongodb.MongoEntity
import org.grails.datastore.gorm.bootstrap.AbstractDatastoreInitializer
import org.grails.datastore.gorm.plugin.support.PersistenceContextInterceptorAggregator
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.config.DatastoreServiceMethodInvokingFactoryBean
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory

/**
 * Used to initialize GORM for MongoDB outside of Grails
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@InheritConstructors
class MongoDbDataStoreSpringInitializer extends AbstractDatastoreInitializer {

    public static final String DEFAULT_DATABASE_NAME = 'test'

    public static final String DATASTORE_TYPE = 'mongo'
    protected String mongoBeanName = 'mongo'
    protected String mongoOptionsBeanName = 'mongoOptions'
    protected String databaseName = DEFAULT_DATABASE_NAME
    protected Closure defaultMapping
    protected MongoClientSettings mongoOptions
    protected MongoClient mongo

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        DatastorePersistenceContextInterceptor
    }

    @Override
    protected boolean isMappedClass(String datastoreType, Class cls) {
        return MongoEntity.isAssignableFrom(cls) || super.isMappedClass(datastoreType, cls)
    }

    /**
     * Configures for an existing Mongo instance
     * @param mongo The instance of Mongo
     * @return The configured ApplicationContext
     */
    @CompileStatic
    ApplicationContext configure() {
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        if (mongo != null) {
            applicationContext.beanFactory.registerSingleton(mongoBeanName, mongo)
        }
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }

    @Override
    Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        return {
            def callable = getCommonConfiguration(beanDefinitionRegistry, 'mongo')
            callable.delegate = delegate
            callable.call()
            // The publisher is registered as a definition rather than constructed here so that the
            // datastore holds a reference the container can build, which is what lets the context
            // be processed ahead of time. Outside an application context there is nothing to
            // publish through, so the no-op publisher stands in as it did before.
            grailsDatastoreEventPublisher(findEventPublisherClass(beanDefinitionRegistry))
            // The configuration is referenced rather than passed. Passing it puts the resolver
            // itself in the definition, and generating code for a definition means writing out
            // whatever it holds: an environment carries the machine's own variables, so the
            // generated source ends up containing the build machine's environment, and the
            // application reads its settings from wherever it was built rather than where it runs.
            Object configurationReference = configurationReference(beanDefinitionRegistry)
            if (mongo == null) {
                mongoConnectionSourceFactory(MongoConnectionSourceFactory) { bean ->
                    bean.autowire = true
                }
                mongoDatastore(MongoDatastore, configurationReference, ref('mongoConnectionSourceFactory'), ref('grailsDatastoreEventPublisher'), mappedClasses(DATASTORE_TYPE))
                mongo(mongoDatastore: 'getMongoClient')
            }
            else {
                mongoDatastore(MongoDatastore, mongo, configurationReference, ref('grailsDatastoreEventPublisher'), mappedClasses(DATASTORE_TYPE))
            }

            mongoMappingContext(mongoDatastore: 'getMappingContext')

            if (!secondaryDatastore) {
                registerAlias('mongoMappingContext', 'grailsDomainClassMappingContext')
            }

            mongoTransactionManager(mongoDatastore: 'getTransactionManager')
            mongoAutoTimestampEventListener(mongoDatastore: 'getAutoTimestampEventListener')
            mongoPersistenceInterceptor(getPersistenceInterceptorClass(), ref('mongoDatastore'))
            mongoPersistenceContextInterceptorAggregator(PersistenceContextInterceptorAggregator)
            def transactionManagerBeanName = TRANSACTION_MANAGER_BEAN
            if (!containsRegisteredBean(delegate, beanDefinitionRegistry, transactionManagerBeanName)) {
                beanDefinitionRegistry.registerAlias('mongoTransactionManager', transactionManagerBeanName)
            }

            def classLoader = getClass().getClassLoader()
            if (isWebApplicationRegistry(beanDefinitionRegistry) && ClassUtils.isPresent(OSIV_CLASS_NAME, classLoader)) {
                String interceptorName = 'mongoOpenSessionInViewInterceptor'
                "${interceptorName}"(ClassUtils.forName(OSIV_CLASS_NAME, classLoader)) {
                    datastore = ref('mongoDatastore')
                }
            }

            loadDataServices(secondaryDatastore ? 'mongo' : null)
                    .each { serviceName, serviceClass ->
                        "$serviceName"(DatastoreServiceMethodInvokingFactoryBean, serviceClass) {
                            targetObject = ref('mongoDatastore')
                            targetMethod = 'getService'
                            arguments = [serviceClass]
                        }
                    }

        }
    }

    /**
     * Sets the name of the Mongo bean to use
     */
    @Deprecated
    void setMongoBeanName(String mongoBeanName) {
        this.mongoBeanName = mongoBeanName
    }
    /**
     * The name of the MongoOptions bean
     *
     * @param mongoOptionsBeanName The mongo options bean name
     */
    @Deprecated
    void setMongoOptionsBeanName(String mongoOptionsBeanName) {
        this.mongoOptionsBeanName = mongoOptionsBeanName
    }
    /**
     * Sets the MongoOptions instance to use when constructing the Mongo instance
     */
    void setMongoOptions(MongoClientSettings mongoOptions) {
        this.mongoOptions = mongoOptions
    }
    /**
     * Sets a pre-existing Mongo instance to configure for
     * @param mongoClient The Mongo instance
     */
    void setMongoClient(MongoClient mongoClient) {
        this.mongo = mongoClient
    }
    /**
     * Sets the name of the MongoDB database to use
     */
    void setDatabaseName(String databaseName) {
        this.databaseName = databaseName
    }

    /**
     * Sets the default MongoDB GORM mapping configuration
     */
    @Deprecated
    void setDefaultMapping(Closure defaultMapping) {
        this.defaultMapping = defaultMapping
    }
}
