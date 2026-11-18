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
package org.grails.testing

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

import spock.lang.Specification

/**
 * One test using every bean registration hook at once. Each hook registers a bean of its own, and
 * the shared names are declared by progressively fewer hooks, so each shows the next step of the
 * precedence: {@code beanRegistrar()} over {@code doWithSpring()} over the {@code beans} block over a
 * nested {@code @Configuration} class.
 */
class MixedBeanHooksSpec extends Specification implements GrailsUnitTest {

    static class Widget {
        String source
    }

    def beans = {
        bean('fromBeansBlock', StringBuilder) { new StringBuilder('beans block') }

        bean('declaredByAllFour', StringBuilder) { new StringBuilder('beans block') }
        bean('declaredByAllButRegistrar', StringBuilder) { new StringBuilder('beans block') }
        bean('declaredByBlockAndConfiguration', StringBuilder) { new StringBuilder('beans block') }

        bean('blockWidget', Widget).conditionalOnMissingBean() { new Widget(source: 'beans block') }
    }

    // Declared after the beans block, so source order would put it last
    @Configuration
    static class NestedConfiguration {

        @Bean
        StringBuilder fromConfiguration() { new StringBuilder('configuration') }

        @Bean
        StringBuilder declaredByAllFour() { new StringBuilder('configuration') }

        @Bean
        StringBuilder declaredByAllButRegistrar() { new StringBuilder('configuration') }

        @Bean
        StringBuilder declaredByBlockAndConfiguration() { new StringBuilder('configuration') }
    }

    Closure doWithSpring() {
        { ->
            fromDoWithSpring(StringBuilder, 'doWithSpring')

            declaredByAllFour(StringBuilder, 'doWithSpring')
            declaredByAllButRegistrar(StringBuilder, 'doWithSpring')
        }
    }

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('fromRegistrar', StringBuilder) { BeanRegistry.Spec<StringBuilder> spec ->
                spec.supplier { new StringBuilder('registrar') }
            }
            registry.registerBean('declaredByAllFour', StringBuilder) { BeanRegistry.Spec<StringBuilder> spec ->
                spec.supplier { new StringBuilder('registrar') }
            }
            registry.registerBean('registrarWidget', Widget) { BeanRegistry.Spec<Widget> spec ->
                spec.supplier { new Widget(source: 'registrar') }
            }
        } as BeanRegistrar
    }

    void "every hook contributes its beans"() {
        expect:
        applicationContext.getBean('fromBeansBlock').toString() == 'beans block'
        applicationContext.getBean('fromConfiguration').toString() == 'configuration'
        applicationContext.getBean('fromDoWithSpring').toString() == 'doWithSpring'
        applicationContext.getBean('fromRegistrar').toString() == 'registrar'
    }

    void "a shared name resolves beanRegistrar() over doWithSpring() over the beans block over a nested configuration class"() {
        expect:
        applicationContext.getBean('declaredByAllFour').toString() == 'registrar'
        applicationContext.getBean('declaredByAllButRegistrar').toString() == 'doWithSpring'
        applicationContext.getBean('declaredByBlockAndConfiguration').toString() == 'beans block'
    }

    void "the class the beans block compiles to is registered after the test's hand-written nested classes"() {
        expect:
        configurationClasses*.simpleName == ['NestedConfiguration', 'BeansConfiguration']
    }

    void "a condition in the beans block cannot see a bean beanRegistrar() registers, so both are registered"() {
        expect: 'the condition is evaluated while the configuration is read, before beanRegistrar() runs'
        applicationContext.getBeansOfType(Widget).keySet() == ['blockWidget', 'registrarWidget'] as Set
    }
}
