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
package org.grails.plugins.datasource

import javax.sql.DataSource

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.core.env.PropertyResolver

import org.grails.spring.beans.factory.InstanceFactoryBean

/**
 * Registers the {@code dataSourceConnectionSources} and {@code dataSource} bean definitions used
 * when no GORM datastore plugin supplies a {@code dataSource}, replacing the registration the
 * data-source plugin previously performed through the {@code doWithSpring()} bean DSL. The
 * {@code dataSource} bean is an {@link InstanceFactoryBean} whose produced type — {@link DataSource}
 * — is only known from an explicit constructor argument, which an instance supplier would hide from
 * Spring's factory-bean type prediction and thereby break by-type autowiring of {@code DataSource}.
 * The definitions are therefore contributed here, mirroring the original DSL (including the SpEL
 * expression that pulls the source from the resolved connection sources).
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the definitions
 * are registered before Spring Boot's configuration-class post-processor evaluates auto-configuration
 * conditions — the same visibility the {@code doWithSpring()} registration had.</p>
 *
 * @since 8.0
 */
@CompileStatic
class DataSourceBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final PropertyResolver configuration

    DataSourceBeanDefinitionsPostProcessor(PropertyResolver configuration) {
        this.configuration = configuration
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition('dataSource')) {
            return
        }

        GenericBeanDefinition connectionSources = new GenericBeanDefinition()
        connectionSources.beanClass = DataSourceConnectionSourcesFactoryBean
        connectionSources.constructorArgumentValues.addIndexedArgumentValue(0, configuration)
        registry.registerBeanDefinition('dataSourceConnectionSources', connectionSources)

        GenericBeanDefinition dataSource = new GenericBeanDefinition()
        dataSource.beanClass = InstanceFactoryBean
        dataSource.constructorArgumentValues.addIndexedArgumentValue(0,
                '#{dataSourceConnectionSources.defaultConnectionSource.source}')
        dataSource.constructorArgumentValues.addIndexedArgumentValue(1, DataSource)
        registry.registerBeanDefinition('dataSource', dataSource)
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
