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
package org.grails.datastore.gorm.boot.autoconfigure

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.mapping.services.Service
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.hibernate.SessionFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.EnvironmentAware
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.AnnotationMetadata
import org.springframework.transaction.PlatformTransactionManager

import javax.sql.DataSource
import java.beans.Introspector

/**
 * Auto configuration for GORM for Hibernate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Configuration
@ConditionalOnClass(HibernateMappingContextConfiguration)
@ConditionalOnBean(DataSource)
@ConditionalOnMissingBean(type = "grails.orm.bootstrap.HibernateDatastoreSpringInitializer")
@AutoConfigureAfter(DataSourceAutoConfiguration)
@AutoConfigureBefore([HibernateJpaAutoConfiguration])
class HibernateGormAutoConfiguration implements ApplicationContextAware,BeanFactoryAware {

    BeanFactory beanFactory

    @Autowired(required = false)
    DataSource dataSource

    ConfigurableApplicationContext applicationContext

    @Bean
    HibernateDatastore hibernateDatastore() {
        List<String> packageNames = AutoConfigurationPackages.get(this.beanFactory)
        List<Package> packages = []
        for(name in packageNames) {
            Package pkg = Package.getPackage(name)
            if(pkg != null) {
                packages.add(pkg)
            }
        }

        ConfigurableListableBeanFactory beanFactory = applicationContext.beanFactory
        HibernateDatastore datastore
        if(dataSource == null) {
            datastore = new HibernateDatastore(
                    applicationContext.getEnvironment(),
                    new ConfigurableApplicationContextEventPublisher(applicationContext),
                    packages as Package[]
            )
            beanFactory.registerSingleton("dataSource", datastore.getDataSource())
        }
        else {
            datastore = new HibernateDatastore(
                    dataSource,
                    applicationContext.getEnvironment(),
                    new ConfigurableApplicationContextEventPublisher(applicationContext),
                    packages as Package[]
            )
        }

        for(Service service in datastore.getServices()) {
            Class serviceClass = service.getClass()
            grails.gorm.services.Service ann = serviceClass.getAnnotation(grails.gorm.services.Service)
            String serviceName = ann?.name()
            if(serviceName == null) {
                serviceName = Introspector.decapitalize(serviceClass.simpleName)
            }
            if(!applicationContext.containsBean(serviceName)) {
                applicationContext.beanFactory.registerSingleton(
                    serviceName,
                    service
                )
            }
        }
        return datastore
    }

    @Bean
    SessionFactory sessionFactory() {
        hibernateDatastore().getSessionFactory()
    }

    @Bean
    PlatformTransactionManager hibernateTransactionManager() {
        hibernateDatastore().getTransactionManager()
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(!(applicationContext instanceof ConfigurableApplicationContext)) {
            throw new IllegalArgumentException("Neo4jAutoConfiguration requires an instance of ConfigurableApplicationContext")
        }
        this.applicationContext = (ConfigurableApplicationContext)applicationContext
    }
}
