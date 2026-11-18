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
package org.apache.grails.openapi.aot

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.javapoet.ClassName
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.tools.GroovyClass
import org.springframework.web.multipart.MultipartFile

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
import grails.rest.RestfulController
import grails.validation.Validateable
import org.grails.plugins.openapi.OpenApiGrailsPlugin

import spock.lang.Specification

class OpenApiBeanFactoryInitializationAotProcessorSpec extends Specification {

    void 'keeps the controllers, with the members the description reads'() {
        when:
        RuntimeHints hints = process(beanFactory())

        then:
        [ShelfController, NoticeController].every { Class<?> controller ->
            RuntimeHintsPredicates.reflection().onType(controller).withMemberCategories(MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS).test(hints)
        }

        and: 'the classes a controller extends, whose actions it inherits, with their annotations and parameters'
        RuntimeHintsPredicates.reflection().onMethodInvocation(RestfulController.getDeclaredMethod('index', Integer)).test(hints)
        RuntimeHintsPredicates.reflection().onType(RestfulController).withMemberCategories(
                MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS).test(hints)
    }

    void 'keeps every type the description reaches from what the controllers serve and bind'() {
        when:
        RuntimeHints hints = process(beanFactory())

        then: 'the resource a controller serves, and a type its properties reach'
        kept(hints, Shelf)
        kept(hints, ShelfLabel)

        and: 'the command object an action binds'
        kept(hints, NoticeCommand)

        and: 'a type an OpenAPI annotation names'
        kept(hints, NoticeReceipt)

        and: 'with the constraints and bound properties Grails declares on it'
        RuntimeHintsPredicates.reflection().onType(NoticeCommand)
                .withMemberCategories(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_PUBLIC_FIELDS)
                .test(hints)

        and: 'with the private fields Groovy puts the annotations of its properties on'
        RuntimeHintsPredicates.reflection().onFieldAccess(NoticeCommand.getDeclaredField('message')).test(hints)
    }

    void 'keeps the classes a kept type extends, whose members Jackson reads too'() {
        when:
        RuntimeHints hints = process(beanFactory())

        then:
        kept(hints, NoticeBase)
    }

    void 'reads nothing Grails declares of a type while the build runs'() {
        given:
        CountedCommand.constraintsReads = 0

        when: 'the application is processed ahead of time, when it is not running'
        RuntimeHints hints = process(beanFactory())

        then: 'the command object is kept, without its constraints being evaluated'
        kept(hints, CountedCommand)
        CountedCommand.constraintsReads == 0
    }

    void 'keeps what the other controllers are described from where one names a class the application does not have'() {
        given: 'a controller whose OpenAPI annotation names a class that is only compiled against'
        Class<?> lost = compiledWithout('MissingReceipt', '''
            package grails.openapi.aot.fixture

            class MissingReceipt {
                String reference
            }
            ''', '''
            package grails.openapi.aot.fixture

            import grails.artefact.Artefact
            import io.swagger.v3.oas.annotations.media.Content
            import io.swagger.v3.oas.annotations.media.Schema
            import io.swagger.v3.oas.annotations.responses.ApiResponse

            @Artefact('Controller')
            @ApiResponse(responseCode = '200', content = @Content(schema = @Schema(implementation = MissingReceipt)))
            class LostController {
            }
            ''')

        when:
        RuntimeHints hints = process(beanFactory([:], [lost, ShelfController, NoticeController, Shelf]))

        then:
        kept(hints, lost)
        kept(hints, Shelf)
        kept(hints, NoticeCommand)
    }

    void 'keeps only what swagger-core resolves, not every type reachable from a property'() {
        when:
        RuntimeHints hints = process(beanFactory())

        then: 'the metaClass every Groovy object has and the errors a validateable one has are not described'
        !kept(hints, MetaClass)
        !kept(hints, org.springframework.validation.Errors)

        and: 'nor are the types of the JDK'
        !kept(hints, String)
    }

    void 'keeps nothing where the description is not generated'() {
        expect:
        new OpenApiBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(beanFactory('grails.openapi.enabled': false)) == null
    }

    void 'is registered for ahead-of-time processing'() {
        given:
        def factories = SpringFactoriesLoader.forResourceLocation('META-INF/spring/aot.factories')

        expect:
        factories.load(BeanFactoryInitializationAotProcessor).any { it instanceof OpenApiBeanFactoryInitializationAotProcessor }
        factories.load(RuntimeHintsRegistrar).any { it instanceof OpenApiRuntimeHints }
    }

    void 'keeps the optional types the description looks up by name'() {
        given:
        def hints = new RuntimeHints()

        when:
        new OpenApiRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onType(MultipartFile).test(hints)
    }

    /**
     * A class compiled against another, and loaded where that other cannot be, as a class compiled
     * against a dependency the application does not ship is.
     */
    private static Class<?> compiledWithout(String missing, String missingSource, String source) {
        def unit = new CompilationUnit(new CompilerConfiguration(), null,
                new GroovyClassLoader(OpenApiBeanFactoryInitializationAotProcessorSpec.classLoader))
        unit.addSource("${missing}.groovy", missingSource)
        unit.addSource('Compiled.groovy', source)
        unit.compile(Phases.CLASS_GENERATION)
        GroovyClass compiled = unit.classes.find { GroovyClass it -> !it.name.endsWith(missing) }
        new GroovyClassLoader(OpenApiBeanFactoryInitializationAotProcessorSpec.classLoader).defineClass(compiled.name, compiled.bytes)
    }

    private static boolean kept(RuntimeHints hints, Class<?> type) {
        RuntimeHintsPredicates.reflection().onType(type).test(hints)
    }

    private RuntimeHints process(DefaultListableBeanFactory beanFactory) {
        def contribution = new OpenApiBeanFactoryInitializationAotProcessor().processAheadOfTime(beanFactory)
        def generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Application')), new InMemoryGeneratedFiles())
        contribution.applyTo(generationContext, Stub(BeanFactoryInitializationCode))
        generationContext.runtimeHints
    }

    /**
     * The bean factory an application's context is processed with: its Grails application, and
     * the beans the plugin registers from its configuration.
     */
    private static DefaultListableBeanFactory beanFactory(Map<String, Object> config = [:],
                                                          List<Class<?>> artefacts = [ShelfController, NoticeController, Shelf]) {
        def application = new DefaultGrailsApplication(artefacts as Class[]).tap { it.initialise() }
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', config))
        def registrar = new OpenApiGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.class).register(registrar)
        beanFactory
    }
}

class ShelfLabel {
    String text
}

@Entity
class Shelf {
    String name
    ShelfLabel label
}

@Artefact('Controller')
class ShelfController extends RestfulController<Shelf> {
    ShelfController() { super(Shelf) }
}

class NoticeBase {
    String origin
}

class NoticeCommand extends NoticeBase implements Validateable {
    String message

    static constraints = {
        message nullable: false
    }
}

class NoticeReceipt {
    String reference
}

class CountedCommand implements Validateable {
    static int constraintsReads
    String note

    static Map getConstraintsMap() {
        constraintsReads++
        [:]
    }
}

@Artefact('Controller')
class NoticeController {

    @ApiResponse(responseCode = '200', content = @Content(schema = @Schema(implementation = NoticeReceipt)))
    def post(NoticeCommand notice) { }

    def count(CountedCommand command) { }
}
