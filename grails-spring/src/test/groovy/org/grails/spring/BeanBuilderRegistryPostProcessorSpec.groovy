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

import java.util.function.Supplier

import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.Ordered

import spock.lang.Specification

class BeanBuilderRegistryPostProcessorSpec extends Specification {

    void 'the definitions the closure declares are registered when the context refreshes, with their aliases'() {
        given:
        def context = new GenericApplicationContext()
        context.registerBeanDefinition('beanBuilderPostProcessor', new RootBeanDefinition(BeanBuilderRegistryPostProcessor, {
            new BeanBuilderRegistryPostProcessor(null, getClass().classLoader, [greeting: 'hello'], { ->
                new BeanBuilderRegistryDefinitions().beans()
            } as Supplier<Closure<?>>)
        } as Supplier<BeanBuilderRegistryPostProcessor>))

        when:
        context.refresh()

        then: 'the definitions are the bean builder\'s'
        context.getBeanDefinition('abstractHolder').abstract
        context.getBeanDefinition('holder').parentName == 'abstractHolder'
        context.getBeanDefinition('holder').lazyInit
        context.getBeanDefinition('holder').propertyValues.get('other') instanceof RuntimeBeanReference

        and: 'the beans are created from them, with the variables of the binding'
        BeanBuilderRegistryHolder holder = context.getBean('holderAlias', BeanBuilderRegistryHolder)
        holder.text == 'hello'
        holder.other.is(context.getBean('otherHolder'))

        cleanup:
        context.close()
    }

    void 'a definition the closure declares replaces one of the same name'() {
        given:
        def context = new GenericApplicationContext()
        context.registerBeanDefinition('holder', new RootBeanDefinition(String))
        def postProcessor = new BeanBuilderRegistryPostProcessor(null, getClass().classLoader, [:],
                { -> { -> holder(BeanBuilderRegistryHolder) } } as Supplier<Closure<?>>)

        when:
        postProcessor.postProcessBeanDefinitionRegistry(context)

        then:
        context.getBeanDefinition('holder').beanClassName == BeanBuilderRegistryHolder.name
    }

    void 'no closure registers nothing'() {
        given:
        def context = new GenericApplicationContext()
        int before = context.beanDefinitionCount

        when:
        new BeanBuilderRegistryPostProcessor(null, getClass().classLoader, [:], { -> null } as Supplier<Closure<?>>)
                .postProcessBeanDefinitionRegistry(context)

        then:
        context.beanDefinitionCount == before
    }

    void 'it runs with the highest precedence, before the configuration classes are read'() {
        expect:
        new BeanBuilderRegistryPostProcessor(null, null, [:], { -> null } as Supplier<Closure<?>>).order == Ordered.HIGHEST_PRECEDENCE
    }
}

/** Declares beans the way a library's own class does, outside the test. */
class BeanBuilderRegistryDefinitions {

    Closure beans() {
        { ->
            abstractHolder {
                text = greeting
            }
            holder(BeanBuilderRegistryHolder) { bean ->
                bean.parent = 'abstractHolder'
                bean.lazyInit = true
                other = ref('otherHolder')
            }
            otherHolder(BeanBuilderRegistryHolder)
            springConfig.addAlias('holderAlias', 'holder')
        }
    }
}

class BeanBuilderRegistryHolder {

    String text
    BeanBuilderRegistryHolder other
}
