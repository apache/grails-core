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
package org.apache.grails.gsp.taglib

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import spock.lang.Specification

/**
 * Covers tag library definitions being contributed without replacing what is already registered.
 *
 * <p>The GSP plugin used to register them from {@code doWithSpring()}, over whatever was there. An
 * ahead-of-time image has already generated a definition for each one, carrying the injection the
 * generator worked out, and replacing it discarded that.</p>
 */
class TagLibBeanDefinitionsPostProcessorSpec extends Specification {

    BeanDefinitionRegistry registry = new DefaultListableBeanFactory()

    GrailsApplication grailsApplication

    void setup() {
        grailsApplication = new DefaultGrailsApplication(DemoTagLib)
        grailsApplication.initialise()
    }

    private void process() {
        new TagLibBeanDefinitionsPostProcessor(grailsApplication).postProcessBeanDefinitionRegistry(registry)
    }

    void 'a tag library the application knows about is registered'() {
        when:
            process()

        then:
            registry.containsBeanDefinition(DemoTagLib.name)
    }

    void 'the definition is lazy and autowired by name'() {
        when:
            process()
            AbstractBeanDefinition definition =
                    (AbstractBeanDefinition) registry.getBeanDefinition(DemoTagLib.name)

        then: 'a tag library takes some collaborators by name rather than by annotation'
            definition.lazyInit
            definition.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_NAME
    }

    void 'a definition that is already registered is kept'() {
        given: 'the definition an ahead-of-time image generated, carrying its own injection'
            RootBeanDefinition generated = new RootBeanDefinition(DemoTagLib)
            registry.registerBeanDefinition(DemoTagLib.name, generated)

        when:
            process()

        then:
            registry.getBeanDefinition(DemoTagLib.name).is(generated)
    }

    void 'a definition that declares no autowiring is left declaring none'() {
        given: 'an application that deliberately took by-name autowiring off its own tag library'
            RootBeanDefinition declared = new RootBeanDefinition(DemoTagLib)
            declared.autowireMode = AbstractBeanDefinition.AUTOWIRE_NO
            registry.registerBeanDefinition(DemoTagLib.name, declared)

        when:
            process()

        then: 'which cannot be told apart from a generated one, so neither is touched -- what a ' +
                'generated definition needs is carried into it while it is generated'
            declared.autowireMode == AbstractBeanDefinition.AUTOWIRE_NO
    }

    void 'a definition asking for something else keeps it'() {
        given:
            RootBeanDefinition declared = new RootBeanDefinition(DemoTagLib)
            declared.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_TYPE
            registry.registerBeanDefinition(DemoTagLib.name, declared)

        when:
            process()

        then:
            declared.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_TYPE
    }

    /** Recognised as a tag library by its name, which is what the artefact handler reads. */
    static class DemoTagLib {

        static namespace = 'demo'

        Closure hello = { attrs -> }
    }
}
