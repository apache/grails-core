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
package org.apache.grails.openapi.cli

import io.swagger.v3.core.util.Json31
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.SpringDocCustomizers
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.openapi.GrailsOpenApiGenerator
import grails.openapi.OpenApiSettings
import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLineParser
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.TempDir

class GenerateOpenApiCommandSpec extends Specification {

    @TempDir
    File directory

    @AutoCleanup
    GenericApplicationContext applicationContext = new GenericApplicationContext()

    void 'writes the default document and each group'() {
        given:
        def command = command(['grails.openapi.groups.reports.paths-to-match': '/reports/**'])
        applicationContext.beanFactory.registerSingleton('ledgers',
                GroupedOpenApi.builder().group('ledgers').pathsToMatch('/ledgers/**').build())

        when:
        boolean handled = command.handle(context("--output-directory=${directory.absolutePath}"))

        then:
        handled
        directory.list() as Set == ['openapi.yaml', 'openapi-reports.yaml', 'openapi-ledgers.yaml'] as Set

        and: 'each document describes what it selects'
        read('openapi.yaml').paths.keySet() == ['/reports', '/ledgers'] as Set
        read('openapi-reports.yaml').paths.keySet() == ['/reports'] as Set
        read('openapi-ledgers.yaml').paths.keySet() == ['/ledgers'] as Set
    }

    void 'writes a group whose name a file name cannot hold under one it can'() {
        given:
        def command = command([:])
        applicationContext.beanFactory.registerSingleton('adminLedgers',
                GroupedOpenApi.builder().group('admin/v1').pathsToMatch('/ledgers/**').build())

        when:
        boolean handled = command.handle(context("--output-directory=${directory.absolutePath}"))

        then:
        handled
        directory.list() as Set == ['openapi.yaml', 'openapi-admin-v1.yaml'] as Set
        read('openapi-admin-v1.yaml').paths.keySet() == ['/ledgers'] as Set
    }

    void 'writes nothing, and says why, where two groups would be written to one file'() {
        given:
        def command = command([:])
        applicationContext.beanFactory.registerSingleton('adminLedgers',
                GroupedOpenApi.builder().group('admin/v1').pathsToMatch('/ledgers/**').build())
        applicationContext.beanFactory.registerSingleton('adminReports',
                GroupedOpenApi.builder().group('admin-v1').pathsToMatch('/reports/**').build())
        def logged = new ByteArrayOutputStream()
        def err = System.err
        System.err = new PrintStream(logged, true)

        when:
        boolean handled = command.handle(context("--output-directory=${directory.absolutePath}"))

        then:
        !handled
        !directory.list()
        logged.toString().contains('would both be written to openapi-admin-v1.yaml')

        cleanup:
        System.err = err
    }

    void 'applies the customizers springdoc applies to each document it serves'() {
        given:
        applicationContext.beanFactory.registerSingleton('springDocCustomizers', new SpringDocCustomizers(
                Optional.of([{ OpenAPI openApi -> openApi.addExtension('x-default', true) } as OpenApiCustomizer,
                             { OpenAPI openApi -> throw new IllegalStateException('No current request') } as OpenApiCustomizer]
                        as LinkedHashSet<OpenApiCustomizer>),
                Optional.of([] as LinkedHashSet), Optional.empty(), Optional.empty(), Optional.of([] as LinkedHashSet),
                Optional.of([{ OpenAPI openApi -> openApi.addExtension('x-global', true) } as GlobalOpenApiCustomizer]
                        as LinkedHashSet<GlobalOpenApiCustomizer>),
                Optional.of([] as LinkedHashSet), Optional.of([] as LinkedHashSet), Optional.empty(), Optional.empty()))
        applicationContext.beanFactory.registerSingleton('ledgers', GroupedOpenApi.builder().group('ledgers')
                .pathsToMatch('/ledgers/**').addOpenApiCustomizer { OpenAPI openApi -> openApi.addExtension('x-ledgers', true) }
                .build())
        def command = command([:])

        when:
        boolean handled = command.handle(context("--output-directory=${directory.absolutePath}"))

        then: 'the default document with the customizers of the default document'
        handled
        read('openapi.yaml')['x-default'] == true

        and: 'a group with its own and the global ones'
        read('openapi-ledgers.yaml')['x-ledgers'] == true
        read('openapi-ledgers.yaml')['x-global'] == true
        !read('openapi-ledgers.yaml').containsKey('x-default')

        and: 'a customizer that needs a request is skipped rather than failing the document'
        read('openapi.yaml').paths.containsKey('/ledgers')
    }

    void 'writes JSON when asked to'() {
        given:
        def command = command([:])

        when:
        command.handle(context("--output-directory=${directory.absolutePath}", '--format=json'))

        then:
        Json31.mapper().readValue(new File(directory, 'openapi.json'), Map).paths.containsKey('/reports')
    }

    void 'writes where and how the configuration says without options'() {
        given:
        File configured = new File(directory, 'configured')
        def command = command(['grails.openapi.output-directory': configured.absolutePath,
                               'grails.openapi.output-format'   : 'json'])

        when:
        command.handle(context())

        then:
        new File(configured, 'openapi.json').file
    }

    void 'writes nothing, and says why, where the description is disabled'() {
        given: 'the plugin registers no generator where grails.openapi.enabled is false'
        applicationContext.refresh()
        def command = new GenerateOpenApiCommand().tap { it.applicationContext = this.applicationContext }

        expect:
        !command.handle(context("--output-directory=${directory.absolutePath}"))
        !directory.list()
    }

    void 'says where springdoc is disabled in the environment it runs in'() {
        given: 'springdoc disabled, as an application generated with the openapi feature has it in production'
        applicationContext.environment.propertySources.addFirst(
                new MapPropertySource('springdoc', ['springdoc.api-docs.enabled': false]))
        def command = command([:])
        def logged = new ByteArrayOutputStream()
        def err = System.err
        System.err = new PrintStream(logged, true)

        when:
        boolean written = command.handle(context("--output-directory=${directory.absolutePath}"))

        then: 'the documents are written, and it says what is not applied to them'
        written
        new File(directory, 'openapi.yaml').file
        logged.toString().contains('springdoc is disabled where the command runs')

        cleanup:
        System.err = err
    }

    void 'refuses a format it cannot write'() {
        given:
        def command = command([:])

        expect:
        !command.handle(context("--output-directory=${directory.absolutePath}", '--format=xml'))
        !directory.list()
    }

    private GenerateOpenApiCommand command(Map<String, Object> config) {
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', config))
        def application = new DefaultGrailsApplication(ReportController, LedgerController).tap { it.initialise() }
        def mappingContext = new MockApplicationContext()
        mappingContext.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(mappingContext).evaluateMappings {
            '/reports'(controller: 'report', action: 'index')
            '/ledgers'(controller: 'ledger', action: 'index')
        })
        applicationContext.beanFactory.registerSingleton('grailsOpenApiGenerator',
                new GrailsOpenApiGenerator(application, holder, [], OpenApiSettings.from(environment)))
        applicationContext.refresh()
        new GenerateOpenApiCommand().tap { it.applicationContext = this.applicationContext }
    }

    private static ExecutionContext context(String... options) {
        new ExecutionContext(new CommandLineParser().parse((['generate-open-api'] + options.toList()) as String[]))
    }

    private Map read(String name) {
        Yaml31.mapper().readValue(new File(directory, name), Map)
    }
}

@Artefact('Controller')
class ReportController {
    def index() { }
}

@Artefact('Controller')
class LedgerController {
    def index() { }
}
