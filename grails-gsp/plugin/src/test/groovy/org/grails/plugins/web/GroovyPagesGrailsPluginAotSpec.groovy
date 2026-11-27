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
package org.grails.plugins.web

import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.generate.NameGenerator
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.apache.grails.core.aot.BeanReflectionBeanFactoryInitializationAotProcessor
import org.apache.grails.gsp.taglib.TagLibBeanDefinitionsPostProcessor
import spock.lang.Specification

/**
 * Covers the post-processors the plugin's registrar declares being creatable in a native image.
 *
 * <p>The registrar is dynamically compiled Groovy, so the {@code new} in each supplier asks Groovy to
 * choose a constructor, which it does reflectively. An image refuses a constructor nothing registered,
 * and the application then stops before any of its beans are defined.</p>
 */
class GroovyPagesGrailsPluginAotSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    private RuntimeHints hints() {
        def application = new DefaultGrailsApplication()
        application.initialise()
        def plugin = new GroovyPagesGrailsPlugin(grailsApplication: application)
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(context, context.beanFactory, context.environment, registrar.getClass()).register(registrar)
        def generation = new DefaultGenerationContext(
                new NameGenerator(ClassName.get('org.grails.plugins.web', 'GroovyPagesAot')), new InMemoryGeneratedFiles())
        new BeanReflectionBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)
                .applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation.runtimeHints
    }

    void 'the constructor each supplier calls can be invoked in an image'() {
        when:
            def hints = hints()

        then:
            RuntimeHintsPredicates.reflection().onConstructorInvocation(
                    GroovyPagesBeanDefinitionsPostProcessor.getConstructor(GrailsApplication, boolean)).test(hints)
            RuntimeHintsPredicates.reflection().onConstructorInvocation(
                    TagLibBeanDefinitionsPostProcessor.getConstructor(GrailsApplication)).test(hints)
    }
}
