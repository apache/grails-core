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
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager

/**
 * Covers the artefacts an application is made of being written down while they can still be found.
 *
 * <p>They are found by walking the classpath and by reading a list the compile-time transform builds
 * as it goes, and an image has neither -- so it found no controllers, no domain classes and no URL
 * mappings, and the application failed to start on the first bean that wanted one. The only way to
 * start was for an application to name its own artefacts by hand.</p>
 */
class ArtefactClassesBeanFactoryInitializationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    private String generatedSourceFor(Class<?>... artefacts) {
        GrailsApplication application = new DefaultGrailsApplication(artefacts)
        application.applicationContext = context
        application.initialise()
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)

        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles()
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Subject')), generatedFiles)
        new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)
        generationContext.writeGeneratedContent()

        generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)
                .keySet()
                .collect { generatedFiles.getGeneratedFileContent(GeneratedFiles.Kind.SOURCE, it) }
                .join('\n')
    }

    void 'the artefacts are written into the generated code'() {
        when:
            String generated = generatedSourceFor(DemoController, DemoService)

        then: 'so that an image has them without the application naming them itself'
            generated.contains('registerSingleton("grailsArtefactClasses"')
            generated.contains('DemoController.class')
            generated.contains('DemoService.class')
    }

    void 'the registration is run as the bean factory is initialized'() {
        when:
            String generated = generatedSourceFor(DemoController)

        then: 'they are read while the definitions are still being contributed, so they have to be ' +
                'there before any of them is'
            generated.contains('registerArtefactClasses')
    }

    void 'a context that is not a Grails application contributes nothing'() {
        given:
            def processor = new ArtefactClassesBeanFactoryInitializationAotProcessor()

        expect: 'a plain Spring application being generated has no grailsApplication to ask'
            processor.processAheadOfTime(context.beanFactory) == null
    }

    void 'the classes that constitute the application are what is written down'() {
        when: 'a Grails application holds these as the classes it is made of'
            String generated = generatedSourceFor(DemoController, DemoService)

        then: 'which is what classes() answers with, so a run reads what a build found'
            generated.count('.class') >= 2
    }

    void 'an application with no artefacts contributes nothing'() {
        given:
            GrailsApplication application = new DefaultGrailsApplication()
            application.applicationContext = context
            application.initialise()
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)

        expect: 'writing an empty array down would say the application has none, which is different ' +
                'from not having looked'
            new ArtefactClassesBeanFactoryInitializationAotProcessor()
                    .processAheadOfTime(context.beanFactory) == null
    }

    void "what the plugins bring with them is not written down"() {
        given: "a plugin manager whose plugins provide one of the classes the application holds"
            GrailsApplication application = new DefaultGrailsApplication(DemoController, DemoService)
            application.applicationContext = context
            application.initialise()
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
            context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME,
                    Stub(GrailsPluginManager) {
                        getAllPlugins() >> ([Stub(GrailsPlugin) {
                            getProvidedArtefacts() >> ([DemoService] as Class[])
                        }] as GrailsPlugin[])
                    })

        when:
            def contribution = new ArtefactClassesBeanFactoryInitializationAotProcessor()
                    .processAheadOfTime(context.beanFactory)

        then: "they are registered from the plugins on every start, and a codec or tag library " +
                "registered twice is not the same as registered once"
            contribution != null

        and:
            def written = writtenBy(contribution)
            written.contains(DemoController)
            !written.contains(DemoService)
    }

    /** The classes the contribution would write, read back off the code it generates. */
    private List<Class> writtenBy(contribution) {
        InMemoryGeneratedFiles files = new InMemoryGeneratedFiles()
        DefaultGenerationContext generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Written')), files)
        new ApplicationContextAotGenerator().processAheadOfTime(context, generation)
        generation.writeGeneratedContent()
        String source = files.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)
                .keySet()
                .collect { files.getGeneratedFileContent(GeneratedFiles.Kind.SOURCE, it) }
                .join('\n')
        [DemoController, DemoService].findAll { source.contains(it.simpleName + '.class') }
    }

    static class DemoController {
    }

    static class DemoService {
    }
}
