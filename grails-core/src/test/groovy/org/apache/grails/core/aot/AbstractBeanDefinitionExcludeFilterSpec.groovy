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
package org.apache.grails.core.aot

import spock.lang.Specification

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.RegisteredBean

import org.grails.spring.beans.AbstractResourceLocatorPostProcessor

class AbstractBeanDefinitionExcludeFilterSpec extends Specification {

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()
    AbstractBeanDefinitionExcludeFilter filter = new AbstractBeanDefinitionExcludeFilter()

    void 'an abstract definition is excluded from AOT processing'() {
        given: 'a classless template definition carrying only inherited property values'
        def definition = new GenericBeanDefinition()
        definition.abstract = true
        definition.propertyValues.add('searchLocations', ['/some/location'])
        beanFactory.registerBeanDefinition('abstractParent', definition)

        expect:
        filter.isExcludedFromAotProcessing(RegisteredBean.of(beanFactory, 'abstractParent'))
    }

    void 'a concrete definition is left to AOT processing'() {
        given:
        def definition = new GenericBeanDefinition()
        definition.beanClass = String
        beanFactory.registerBeanDefinition('concrete', definition)

        expect:
        !filter.isExcludedFromAotProcessing(RegisteredBean.of(beanFactory, 'concrete'))
    }

    void 'a child inheriting from an abstract parent is left to AOT processing'() {
        given: 'the parent template and a child naming it'
        def parent = new GenericBeanDefinition()
        parent.abstract = true
        parent.propertyValues.add('searchLocations', ['/some/location'])
        beanFactory.registerBeanDefinition('abstractParent', parent)

        def child = new GenericBeanDefinition()
        child.beanClass = StringBuilder
        child.parentName = 'abstractParent'
        beanFactory.registerBeanDefinition('child', child)

        expect: 'the child is generated from its merged definition, so it needs no parent at runtime'
        !filter.isExcludedFromAotProcessing(RegisteredBean.of(beanFactory, 'child'))
    }

    void 'the resource locator template the core plugin contributes is excluded'() {
        given:
        new AbstractResourceLocatorPostProcessor(['/base']).postProcessBeanDefinitionRegistry(beanFactory)

        expect:
        filter.isExcludedFromAotProcessing(
                RegisteredBean.of(beanFactory, AbstractResourceLocatorPostProcessor.BEAN_NAME))
    }
}
