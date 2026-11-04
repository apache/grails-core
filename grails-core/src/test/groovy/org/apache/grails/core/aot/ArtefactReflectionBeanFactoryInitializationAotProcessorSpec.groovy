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

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

import grails.core.ArtefactHandlerAdapter
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.grails.core.DefaultGrailsServiceClass
import org.grails.core.DefaultGrailsUrlMappingsClass

/**
 * Covers what the framework creates reflectively as it registers artefacts being creatable in an image:
 * the class each kind of artefact is represented by, and what the plugins provide.
 *
 * <p>An image built without these stopped while the plugins registered their artefacts, unable to find
 * the constructor of the first codec's {@code GrailsClass}.</p>
 */
class ArtefactReflectionBeanFactoryInitializationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    private GrailsApplication application(Class<?>... classes) {
        GrailsApplication application = new DefaultGrailsApplication(classes)
        application.applicationContext = context
        application.initialise()
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        application
    }

    private RuntimeHints hints() {
        def contribution = new ArtefactReflectionBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.beanFactory)
        def generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Hints')), new InMemoryGeneratedFiles())
        contribution.applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation.runtimeHints
    }

    private static boolean constructible(RuntimeHints hints, Class<?> type) {
        RuntimeHintsPredicates.reflection().onType(type)
                .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'the class a kind of artefact is represented by can be created'() {
        given: 'an application with the artefact handlers the framework declares'
            application()

        when:
            def hints = hints()

        then: 'a handler creates it through a constructor taking the artefact class'
            constructible(hints, DefaultGrailsServiceClass)
            constructible(hints, DefaultGrailsUrlMappingsClass)
    }

    void 'every handler the application has is covered'() {
        given:
            def application = application()
            def representations = application.artefactHandlers
                    .findAll { it instanceof ArtefactHandlerAdapter }
                    .collect { ((ArtefactHandlerAdapter) it).grailsClassImpl }

        when:
            def hints = hints()

        then: 'whichever module declared it'
            !representations.isEmpty()
            representations.every { constructible(hints, it) }
    }

    void 'what a plugin provides can be created and given its collaborators'() {
        given: 'a plugin providing an artefact, as the codecs plugin provides its codecs'
            application()
            context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME,
                    Stub(GrailsPluginManager) {
                        getAllPlugins() >> ([Stub(GrailsPlugin) {
                            getProvidedArtefacts() >> ([ProvidedCodecFixture] as Class[])
                        }] as GrailsPlugin[])
                    })

        when:
            def hints = hints()

        then: 'it is created through its constructor, and its collaborators are set through its setters'
            RuntimeHintsPredicates.reflection().onType(ProvidedCodecFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
                    .test(hints)
    }

    void 'an artefact the application holds can be read the way its kind needs'() {
        given: 'an application holding a URL mappings class it did not write, as it holds the default mappings'
            application(DefaultUrlMappingsFixture)

        when:
            def hints = hints()

        then: 'its mappings closure is read from the field it declares'
            RuntimeHintsPredicates.reflection().onType(DefaultUrlMappingsFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS)
                    .test(hints)
    }

    void 'a context that is not a Grails application contributes nothing'() {
        expect: 'a plain Spring application being generated has no grailsApplication and no plugins to ask'
            new ArtefactReflectionBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    static class ProvidedCodecFixture {

        GrailsApplication grailsApplication
    }

    static class DefaultUrlMappingsFixture {

        static mappings = {
        }
    }
}
