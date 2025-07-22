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
package org.grails.spring.context.support

import grails.core.support.GrailsApplicationAware
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import grails.core.GrailsApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition

import org.springframework.transaction.interceptor.TransactionProxyFactoryBean
import org.springframework.beans.factory.FactoryBean

/**
 * Applies property configuration from a Map with bean names as keys and bean properties as name/value Maps
 * (i.e. Map&lt;String,Map&lt;String,Object&gt;&gt;), trying to handle FactoryBeans specially by applying
 * the properties to the <em>actual</em> bean.
 *
 * @author Luke Daley
 * @author Graeme Rocher
 */
@CompileStatic
class MapBasedSmartPropertyOverrideConfigurer implements BeanFactoryPostProcessor, GrailsApplicationAware {

    @Autowired
    GrailsApplication grailsApplication

    void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        def beans = getBeansConfig()
        if (!beans) {
            return
        }
        for(beanName in beans.keySet()) {
            def beanProperties = beans.get(beanName)
            if (!(beanProperties instanceof Map)) {
                throw new IllegalArgumentException("Entry in bean config for bean '" + beanName + "' must be a Map")
            }
            else {
                final beanPropertiesMap = (Map) beanProperties
                for(beanPropertyName in beanPropertiesMap.keySet()) {
                    final beanPropertyValue = beanPropertiesMap.get(beanPropertyName)
                    applyPropertyValue(factory, beanName.toString(), beanPropertyName.toString(), beanPropertyValue)
                }
            }
        }
    }

    protected void applyPropertyValue(ConfigurableListableBeanFactory factory, String beanName, String property, Object value) {
        def bd = getTargetBeanDefinition(factory, beanName)
        if (bd != null) {
            bd.propertyValues.addPropertyValue(property, value)
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected Map<String, Object> getBeansConfig() {
        grailsApplication?.config?.getProperty("beans", Map)
    }

    protected ClassLoader getClassLoader() {
        grailsApplication.classLoader
    }

    protected BeanDefinition getTargetBeanDefinition(ConfigurableListableBeanFactory factory, String beanName) {
        if (factory.containsBeanDefinition(beanName)) {
            getTargetBeanDefinition(factory, beanName, factory.getBeanDefinition(beanName))
        }
        else {
            null
        }
    }

    protected BeanDefinition getTargetBeanDefinition(ConfigurableListableBeanFactory factory,
            String beanName, BeanDefinition beanDefinition) {

        if (beanDefinition.factoryBeanName) {
            beanDefinition
        }
        else {
            getTargetBeanDefinition(factory, beanName, beanDefinition,
                    classLoader.loadClass(beanDefinition.beanClassName))
        }
    }

    protected BeanDefinition getTargetBeanDefinition(ConfigurableListableBeanFactory factory, String beanName,
            BeanDefinition beanDefinition, Class beanClass) {

        if (FactoryBean.isAssignableFrom(beanClass)) {
            getTargetBeanDefinitionForFactoryBean(factory, beanName, beanDefinition, beanClass)
        }
        else {
            beanDefinition
        }
    }

    protected BeanDefinition getTargetBeanDefinitionForFactoryBean(ConfigurableListableBeanFactory factory,
            String beanName, BeanDefinition beanDefinition, Class<? extends FactoryBean> beanClass) {

        if (TransactionProxyFactoryBean.isAssignableFrom(beanClass)) {
            getTargetBeanDefinition(factory, beanName,
                    (BeanDefinition)beanDefinition.propertyValues.getPropertyValue("target").value)
        }
        else {
            beanDefinition
        }
    }
}
