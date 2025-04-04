/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.plugin.support

import groovy.transform.CompileStatic
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.ManagedList
import org.springframework.core.Ordered
import org.springframework.util.ClassUtils

import java.util.regex.Pattern
/**
 * BeanDefinitionRegistryPostProcessor that replaces multiple discovered PersistenceContextInterceptor beans with
 * a single aggregating instance. The previous multiple PersistenceContextInterceptor beans will be removed from
 * the context and re-added as inner beans of the new AggregatePersistenceContextInterceptor bean.
 * 
 * PersistenceContextInterceptor beans are discovered by the bean name. The default pattern used for matching
 * the beans is ^.*[pP]ersistenceInterceptor$
 *
 * @author Burt Beckwith
 * @author Lari Hotari
 */
@CompileStatic
class PersistenceContextInterceptorAggregator implements BeanDefinitionRegistryPostProcessor, Ordered {
    Pattern persistenceInterceptorBeanNamePattern = ~/^.*[pP]ersistenceInterceptor$/
    String aggregatorBeanName = 'persistenceInterceptor'
    Class aggregatorBeanClass = ClassUtils.forName("org.grails.datastore.gorm.support.AggregatePersistenceContextInterceptor", Thread.currentThread().contextClassLoader)
    int order = 500

    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        createAggregatePersistenceContextInterceptorOnDemand(registry)
    }

    protected createAggregatePersistenceContextInterceptorOnDemand(BeanDefinitionRegistry registry) {
        Collection<String> persistenceInterceptorBeanNames = findPersistenceInterceptorBeanNames(registry)
        if(persistenceInterceptorBeanNames.size() > 1) {
            ManagedList interceptorBeans = moveInterceptorBeansToManagedList(registry, persistenceInterceptorBeanNames)
            registry.registerBeanDefinition(aggregatorBeanName, createAggregateBeanDefinition(interceptorBeans))
        }
    }
    
    protected Collection<String> findPersistenceInterceptorBeanNames(BeanDefinitionRegistry registry) {
        // assume that all persistenceInterceptor beans match the defined pattern
        // checking for type (class) would require instantiating classes
        registry.getBeanDefinitionNames().findAll { String beanName -> beanName ==~ persistenceInterceptorBeanNamePattern }
    }

    protected ManagedList moveInterceptorBeansToManagedList(BeanDefinitionRegistry registry, Collection<String> persistenceInterceptorBeanNames) {
        ManagedList list = new ManagedList()
        persistenceInterceptorBeanNames.each { String beanName ->
            list.add registry.getBeanDefinition(beanName)
            registry.removeBeanDefinition(beanName)
        }
        return list
    }

    protected BeanDefinition createAggregateBeanDefinition(ManagedList interceptorBeans) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition()
        beanDefinition.beanClass = aggregatorBeanClass
        beanDefinition.primary = true
        ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues()
        constructorArgumentValues.addIndexedArgumentValue(0, interceptorBeans)
        beanDefinition.constructorArgumentValues = constructorArgumentValues
        beanDefinition
    }

    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        
    }
}
