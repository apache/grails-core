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

package org.grails.datastore.gorm.mongodb.boot.autoconfigure

import java.beans.Introspector
import java.util.function.Function
import java.util.function.Supplier

import groovy.transform.CompileStatic

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration
import org.springframework.boot.mongodb.autoconfigure.MongoClientFactory
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.transaction.PlatformTransactionManager

import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.services.Service

/**
 * Configures GORM for MongoDB in a Spring Boot application.
 *
 * <p>Ordered before Spring Boot's {@link MongoAutoConfiguration}. Unless the application declares a
 * {@link MongoClient} bean of its own, GORM builds the client - from Spring Boot's {@link MongoClientSettings} and
 * {@link MongoClientSettingsBuilderCustomizer}s, the way Spring Boot builds its own - and publishes it as the
 * {@code mongo} bean, so Spring Boot's client steps aside and everything that injects a {@code MongoClient} shares
 * GORM's. GORM then owns it: it connects when the datastore starts, and it is stopped for a CRaC checkpoint and
 * started again after the restore. A client the application declares is used as it is, and closing it, or stopping
 * it for a checkpoint, is left to the application.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Configuration
@ConditionalOnMissingBean(MongoDatastore)
@AutoConfigureBefore(MongoAutoConfiguration)
class MongoDbGormAutoConfiguration {

    @Bean
    PlatformTransactionManager mongoTransactionManager(MongoDatastore mongoDatastore) {
        mongoDatastore.getTransactionManager()
    }

    /**
     * The datastore around a {@link MongoClient} bean the application declares. Processed before
     * {@link GormClientConfiguration}, which declares one of its own otherwise.
     */
    @Order(1)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoClient)
    static class ApplicationClientConfiguration {

        @Bean
        MongoDatastore mongoDatastore(MongoClient mongo, ApplicationContext applicationContext) {
            createDatastore(applicationContext) { ConfigurableApplicationContext context ->
                new MongoDatastore(mongo, context.environment, new ConfigurableApplicationContextEventPublisher(context),
                        packagesOf(context))
            }
        }
    }

    /**
     * The datastore around a client GORM builds and owns, which it publishes as the {@code mongo} bean.
     */
    @Order(2)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(MongoClient)
    static class GormClientConfiguration {

        @Bean
        MongoDatastore mongoDatastore(ApplicationContext applicationContext,
                                      ObjectProvider<MongoClientSettings> mongoClientSettings,
                                      ObjectProvider<MongoClientSettingsBuilderCustomizer> customizers) {
            createDatastore(applicationContext) { ConfigurableApplicationContext context ->
                ConfigurableEnvironment environment = context.environment
                ConfigurableApplicationContextEventPublisher eventPublisher = new ConfigurableApplicationContextEventPublisher(context)
                MongoClientSettings settings = mongoClientSettings.getIfAvailable()
                if (settings == null) {
                    // Spring Boot's MongoDB support is not configured, so the client is built from grails.mongodb.
                    return new MongoDatastore(environment, eventPublisher, packagesOf(context))
                }
                List<MongoClientSettingsBuilderCustomizer> builderCustomizers = customizers.orderedStream().toList()
                new MongoDatastore({ new MongoClientFactory(builderCustomizers).createMongoClient(settings) } as Supplier<MongoClient>,
                        environment, eventPublisher, packagesOf(context))
            }
        }

        /**
         * The datastore's own client, which it closes; Spring is not to close it as well.
         */
        @Bean(destroyMethod = '')
        MongoClient mongo(MongoDatastore mongoDatastore) {
            mongoDatastore.getMongoClient()
        }
    }

    private static MongoDatastore createDatastore(ApplicationContext applicationContext,
                                                  Function<ConfigurableApplicationContext, MongoDatastore> factory) {
        if (!(applicationContext instanceof ConfigurableApplicationContext)) {
            throw new IllegalArgumentException('MongoDbGormAutoConfiguration requires an instance of ConfigurableApplicationContext')
        }
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) applicationContext
        MongoDatastore datastore = factory.apply(context)

        for (Service service in datastore.getServices()) {
            Class serviceClass = service.getClass()
            grails.gorm.services.Service ann = serviceClass.getAnnotation(grails.gorm.services.Service)
            String serviceName = ann?.name()
            if (serviceName == null) {
                serviceName = Introspector.decapitalize(serviceClass.simpleName)
            }
            if (!context.containsBean(serviceName)) {
                context.beanFactory.registerSingleton(
                        serviceName,
                        service
                )
            }
        }
        return datastore
    }

    private static Package[] packagesOf(ConfigurableApplicationContext context) {
        ConfigurableListableBeanFactory beanFactory = context.beanFactory
        List<Package> packages = []
        for (String name in AutoConfigurationPackages.get(beanFactory)) {
            Package pkg = Package.getPackage(name)
            if (pkg != null) {
                packages.add(pkg)
            }
        }
        packages as Package[]
    }
}
