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

package org.grails.datastore.gorm.mongodb.boot.autoconfigure

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.services.Service
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.transaction.PlatformTransactionManager

import java.beans.Introspector

/**
 *
 * Auto configurer that configures GORM for MongoDB for use in Spring Boot
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Configuration
@ConditionalOnMissingBean(MongoDatastore)
@AutoConfigureAfter(MongoAutoConfiguration)
class MongoDbGormAutoConfiguration implements ApplicationContextAware{

    @Autowired(required = false)
    private MongoProperties mongoProperties

    @Autowired(required = false)
    MongoClient mongo

    @Autowired(required = false)
    MongoClientSettings mongoOptions

    ConfigurableApplicationContext applicationContext

    @Bean
    MongoDatastore mongoDatastore() {
        ConfigurableApplicationContext context = applicationContext
        if(!(context instanceof ConfigurableApplicationContext)) {
            throw new IllegalArgumentException("MongoDbGormAutoConfiguration requires an instance of ConfigurableApplicationContext")
        }
        ConfigurableListableBeanFactory beanFactory = context.beanFactory
        List<String> packageNames = AutoConfigurationPackages.get(beanFactory)
        List<Package> packages = []
        for(name in packageNames) {
            Package pkg = Package.getPackage(name)
            if(pkg != null) {
                packages.add(pkg)
            }
        }

        MongoDatastore datastore
        ConfigurableEnvironment environment = context.environment
        ConfigurableApplicationContextEventPublisher eventPublisher = new ConfigurableApplicationContextEventPublisher(context)
        if(mongo != null) {
            datastore = new MongoDatastore(mongo, environment,eventPublisher, packages as Package[])
        }
        else if(mongoProperties != null) {
            this.mongo = MongoClients.create(mongoOptions)
            datastore = new MongoDatastore(mongo, environment,eventPublisher, packages as Package[])
        }
        else {
            datastore = new MongoDatastore(environment, eventPublisher, packages as Package[])
        }

        for(Service service in datastore.getServices()) {
            Class serviceClass = service.getClass()
            grails.gorm.services.Service ann = serviceClass.getAnnotation(grails.gorm.services.Service)
            String serviceName = ann?.name()
            if(serviceName == null) {
                serviceName = Introspector.decapitalize(serviceClass.simpleName)
            }
            if(!context.containsBean(serviceName)) {
                context.beanFactory.registerSingleton(
                        serviceName,
                        service
                )
            }
        }
        return datastore
    }

    @Bean
    PlatformTransactionManager mongoTransactionManager() {
        mongoDatastore().getTransactionManager()
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(!(applicationContext instanceof ConfigurableApplicationContext)) {
            throw new IllegalArgumentException("MongoDbGormAutoConfiguration requires an instance of ConfigurableApplicationContext")
        }
        this.applicationContext = (ConfigurableApplicationContext)applicationContext
    }

}
