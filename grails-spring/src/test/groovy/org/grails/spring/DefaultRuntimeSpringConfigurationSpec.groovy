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

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class DefaultRuntimeSpringConfigurationSpec extends Specification {

    DefaultRuntimeSpringConfiguration config = new DefaultRuntimeSpringConfiguration()

    void 'singleton and prototype beans are added and resolve their definitions and names'() {
        when:
        config.addSingletonBean('a', String)
        config.addPrototypeBean('b', Integer)

        then:
        config.containsBean('a')
        config.containsBean('b')
        !config.containsBean('missing')
        config.getBeanConfig('a').name == 'a'
        !config.getBeanConfig('a').singleton == false
        !config.getBeanConfig('b').singleton
        config.getBeanNames().containsAll(['a', 'b'])
        config.createBeanDefinition('a').beanClassName == String.name
        config.createBeanDefinition('missing') == null
    }

    void 'createSingletonBean and createPrototypeBean produce standalone configurations not registered by name'() {
        when:
        BeanConfiguration created = config.createSingletonBean(String)
        BeanConfiguration namedPrototype = config.createPrototypeBean('c')

        then:
        created.beanDefinition.beanClassName == String.name
        !config.containsBean('c')
        namedPrototype.name == 'c'
        !namedPrototype.singleton
    }

    void 'addBeanDefinition registers a raw bean definition and displaces a same-named bean configuration'() {
        given:
        config.addSingletonBean('a', String)
        AbstractBeanDefinition definition = new org.springframework.beans.factory.support.GenericBeanDefinition()
        definition.setBeanClassName(Integer.name)

        when:
        config.addBeanDefinition('a', definition)

        then:
        config.containsBean('a')
        config.getBeanConfig('a') == null
        config.getBeanDefinition('a').is(definition)
        config.createBeanDefinition('a').is(definition)
    }

    void 'addAbstractBean registers an abstract singleton configuration'() {
        when:
        BeanConfiguration bc = config.addAbstractBean('a')

        then:
        bc.beanDefinition.abstract
        config.containsBean('a')
    }

    void 'aliases registered for a bean are applied when beans are registered with a registry'() {
        given:
        config.addSingletonBean('a', String)
        config.addAlias('alias1', 'a')
        config.addAlias('alias2', 'a')

        when:
        ApplicationContext ctx = config.applicationContext

        then:
        ctx.getBean('alias1') instanceof String
        ctx.getBean('alias2') instanceof String

        cleanup:
        ((org.springframework.context.ConfigurableApplicationContext) ctx)?.close()
    }

    void 'registerBeansWithConfig copies this configuration\'s bean configs into the target'() {
        given:
        config.addSingletonBean('a', String)
        DefaultRuntimeSpringConfiguration target = new DefaultRuntimeSpringConfiguration()

        when:
        config.registerBeansWithConfig(target)

        then:
        target.containsBean('a')

        when:
        config.registerBeansWithConfig(null)

        then:
        noExceptionThrown()
    }

    void 'the application context is created once and reused for the unrefreshed and refreshed accessors'() {
        when:
        ApplicationContext unrefreshed = config.unrefreshedApplicationContext
        ApplicationContext refreshed = config.applicationContext

        then:
        unrefreshed.is(refreshed)
        refreshed.active

        cleanup:
        ((org.springframework.context.ConfigurableApplicationContext) refreshed)?.close()
    }

}
