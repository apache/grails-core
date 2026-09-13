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
package org.grails.spring

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.context.annotation.Lazy
import spock.lang.Specification

class DefaultBeanConfigurationSpec extends Specification {

    void 'name, singleton and constructor args are set from the constructors'() {
        expect:
        new DefaultBeanConfiguration('a', String).name == 'a'
        new DefaultBeanConfiguration('a', String).singleton
        !new DefaultBeanConfiguration('a', String, true).singleton
        !new DefaultBeanConfiguration('a', true).singleton
        new DefaultBeanConfiguration(String).beanDefinition.beanClassName == String.name
        new DefaultBeanConfiguration('a', String, ['x']).beanDefinition.constructorArgumentValues.argumentCount == 1
        new DefaultBeanConfiguration(String, ['x']).beanDefinition.constructorArgumentValues.argumentCount == 1
    }

    void 'a bean definition is created lazily and reused on repeated access'() {
        given:
        DefaultBeanConfiguration config = new DefaultBeanConfiguration('a', DbcLazyBean)

        when:
        AbstractBeanDefinition first = config.beanDefinition
        AbstractBeanDefinition second = config.beanDefinition

        then:
        first.is(second)
        first.lazyInit
        first.beanClassName == DbcLazyBean.name
    }

    void 'setBeanDefinition replaces the generated definition'() {
        given:
        DefaultBeanConfiguration config = new DefaultBeanConfiguration('a', String)
        AbstractBeanDefinition replacement = new org.springframework.beans.factory.support.GenericBeanDefinition()

        when:
        config.setBeanDefinition(replacement)

        then:
        config.getBeanDefinition().is(replacement)
    }

    void 'addProperty unwraps a nested BeanConfiguration to its bean definition'() {
        given:
        DefaultBeanConfiguration config = new DefaultBeanConfiguration('a', String)
        DefaultBeanConfiguration nested = new DefaultBeanConfiguration('b', Integer)

        when:
        config.addProperty('nested', nested)
        config.addProperty('plain', 'value')

        then:
        config.beanDefinition.propertyValues.getPropertyValue('nested').value.is(nested.beanDefinition)
        config.getPropertyValue('plain') == 'value'
        config.hasProperty('plain')
        !config.hasProperty('missing')
        config.getPropertyValue('missing') == null
    }

    void 'setDestroyMethod, setFactoryBean, setFactoryMethod, setAbstract and setDependsOn all chain and mutate the definition'() {
        given:
        DefaultBeanConfiguration config = new DefaultBeanConfiguration('a', String)

        when:
        config.setDestroyMethod('close').setFactoryBean('factory').setFactoryMethod('create').setAbstract(true).setDependsOn(['x', 'y'] as String[])

        then:
        config.beanDefinition.destroyMethodName == 'close'
        config.beanDefinition.factoryBeanName == 'factory'
        config.beanDefinition.factoryMethodName == 'create'
        config.beanDefinition.abstract
        config.beanDefinition.dependsOn == ['x', 'y']
    }

    void 'setAutowire accepts byName and byType and ignores anything else'() {
        given:
        DefaultBeanConfiguration byName = new DefaultBeanConfiguration('a', String)
        DefaultBeanConfiguration byType = new DefaultBeanConfiguration('b', String)
        DefaultBeanConfiguration other = new DefaultBeanConfiguration('c', String)

        when:
        byName.setAutowire('byName')
        byType.setAutowire('byType')
        other.setAutowire('nonsense')

        then:
        byName.beanDefinition.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_NAME
        byType.beanDefinition.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_TYPE
        other.beanDefinition.autowireMode == AbstractBeanDefinition.AUTOWIRE_NO
    }

    void 'setParent accepts a String, a RuntimeBeanReference or another BeanConfiguration, and clears abstract'() {
        given:
        DefaultBeanConfiguration byString = new DefaultBeanConfiguration('a', String)
        DefaultBeanConfiguration byRef = new DefaultBeanConfiguration('b', String)
        DefaultBeanConfiguration byConfig = new DefaultBeanConfiguration('c', String)
        DefaultBeanConfiguration parentConfig = new DefaultBeanConfiguration('parent', String)
        byString.setAbstract(true)

        when:
        byString.setParent('parentName')
        byRef.setParent(new RuntimeBeanReference('refName'))
        byConfig.setParent(parentConfig)

        then:
        byString.beanDefinition.parentName == 'parentName'
        !byString.beanDefinition.abstract
        byRef.beanDefinition.parentName == 'refName'
        byConfig.beanDefinition.parentName == 'parent'

        when:
        byString.setParent(null)

        then:
        thrown(IllegalArgumentException)
    }

    void 'dynamic getProperty/setProperty route autowire, singleton and the other builder-DSL keys through the bean definition'() {
        given:
        DefaultBeanConfiguration config = new DefaultBeanConfiguration('a', String)

        when:
        config.autowire = 'byName'
        config.singleton = false
        config.destroyMethod = 'shutdown'
        config.factoryBean = 'aFactory'
        config.factoryMethod = 'aMethod'
        config.initMethod = 'init'
        config.constructorArgs = ['one']

        then:
        config.beanDefinition.autowireMode == AutowireCapableBeanFactory.AUTOWIRE_BY_NAME
        config.beanDefinition.scope == 'prototype'
        config.beanDefinition.destroyMethodName == 'shutdown'
        config.beanDefinition.factoryBeanName == 'aFactory'
        config.beanDefinition.factoryMethodName == 'aMethod'
        config.beanDefinition.initMethodName == 'init'
        config.beanDefinition.constructorArgumentValues.argumentCount == 1
        config.autowire == null
        config.constructorArgs == null

        when:
        config.parent = 'someParent'

        then:
        config.beanDefinition.parentName == 'someParent'
    }

}

@Lazy
class DbcLazyBean {
}
