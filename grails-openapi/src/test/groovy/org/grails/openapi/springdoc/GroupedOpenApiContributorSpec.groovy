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
package org.grails.openapi.springdoc

import java.lang.reflect.Method

import io.swagger.v3.oas.annotations.Operation as OperationAnnotation
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.GlobalOperationComponentsCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.customizers.SpringDocCustomizers
import org.springdoc.core.filters.GlobalOpenApiMethodFilter
import org.springdoc.core.filters.OpenApiMethodFilter
import org.springdoc.core.models.GroupedOpenApi
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.api.MultipleOpenApiActuatorResource
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.web.method.HandlerMethod

import grails.artefact.Artefact
import grails.openapi.GrailsOpenApiGenerator
import grails.openapi.OpenApiSelection
import grails.openapi.OpenApiFixture
import grails.openapi.WidgetController
import grails.openapi.Widget
import grails.openapi.Crate
import grails.openapi.namespaced.v1.GateController
import grails.rest.RestfulController

import spock.lang.Specification

class GroupedOpenApiContributorSpec extends Specification {

    void 'contributes to each springdoc group what the group selects'() {
        given: 'a group declared as a bean, and one springdoc created from its properties'
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('gates', GroupedOpenApi.builder().group('gates').pathsToMatch('/gate/**').build())
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets')
                .packagesToScan('grails.openapi').pathsToExclude('/gate/**').build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory

        when: 'springdoc prepares its grouped documents'
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')

        then:
        paths(beanFactory.getBean('gates', GroupedOpenApi)) == ['/gate'] as Set
        paths(beanFactory.getBean('widgets', GroupedOpenApi)) == ['/widgets', '/widgets/{id}'] as Set
    }

    void 'applies springdoc\'s top-level criteria to a group ahead of the group\'s own, as springdoc does'() {
        given: 'springdoc selecting /gate for every document, and a group selecting /widgets'
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('springDocConfigProperties', new SpringDocConfigProperties(pathsToMatch: ['/gate']))
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/widgets/**')
                .packagesToScan('grails.openapi').build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory

        when:
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')

        then: 'the top-level paths, where springdoc has them, and the group\'s own packages, where it has none'
        paths(beanFactory.getBean('widgets', GroupedOpenApi)) == ['/gate'] as Set
    }

    void 'contributes to a group once, where springdoc serves the groups through the actuator too'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('gates', GroupedOpenApi.builder().group('gates').pathsToMatch('/gate/**').build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory
        def groups = beanFactory.getBeansOfType(GroupedOpenApi).values().toList()

        when: 'springdoc prepares the resource serving the groups, and the one serving them through the actuator'
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')
        contributor.postProcessBeforeInitialization(new MultipleOpenApiActuatorResource(groups, null, null, null, null,
                null, null, null), 'multipleOpenApiActuatorResource')

        then:
        beanFactory.getBean('gates', GroupedOpenApi).openApiCustomizers.count { it instanceof GrailsOpenApiCustomizer } == 1
    }

    void 'warns of a group name declared more than once'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/widgets/**').build())
        beanFactory.registerSingleton('moreWidgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/gate').build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory
        def logged = new ByteArrayOutputStream()
        def err = System.err
        System.err = new PrintStream(logged, true)

        when:
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')

        then:
        logged.toString().contains('The OpenAPI group [widgets] is declared 2 times')

        cleanup:
        System.err = err
    }

    void 'applies the method filters of a group, and the global ones, to the Grails actions'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('springDocCustomizers',
                customizers(globalMethodFilters: [{ Method action -> action.name != 'delete' } as GlobalOpenApiMethodFilter]))
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/widgets/**')
                .addOpenApiMethodFilter { Method action -> action.name != 'save' }.build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory

        when:
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')

        then:
        operations(document(beanFactory.getBean('widgets', GroupedOpenApi))) ==
                ['GET /widgets', 'GET /widgets/{id}', 'PUT /widgets/{id}', 'POST /widgets/{id}', 'PATCH /widgets/{id}'] as Set
    }

    void 'applies the method filters springdoc applies to its default document'() {
        given:
        def customizers = customizers(methodFilters: [{ Method action -> action.name != 'delete' } as OpenApiMethodFilter])

        when:
        def openApi = generator().generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then:
        openApi.paths['/widgets/{id}'].get
        openApi.paths['/widgets/{id}'].delete == null
    }

    void 'gives a method filter the action a controller overrides, as the controller declares it'() {
        given: 'a filter describing only the actions annotated with @Operation'
        def customizers = customizers(methodFilters: [
                { Method action -> action.isAnnotationPresent(OperationAnnotation) } as OpenApiMethodFilter])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'annotatedWidget') },
                OpenApiFixture.application([AnnotatedWidgetController]), OpenApiFixture.context([Widget, Crate]))

        when:
        def openApi = generator.generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then: 'the override, which is annotated, rather than the action it overrides'
        openApi.paths['/widgets'].get.summary == 'Fetch all widgets'

        and: 'an action it does not override, which is not annotated, is left out'
        !openApi.paths['/widgets/{id}']?.get
    }

    void 'gives a method filter an action taking parameters as the controller declares it, not the one Grails adds'() {
        given:
        List<Method> filtered = []
        def customizers = customizers(methodFilters: [{ Method action ->
            filtered << action
            true
        } as OpenApiMethodFilter])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'listedWidget') },
                OpenApiFixture.application([ListedWidgetController]), OpenApiFixture.context([Widget, Crate]))

        when:
        generator.generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then:
        filtered.findAll { it.name == 'index' && it.declaringClass == ListedWidgetController }*.parameterTypes ==
                [[Integer] as Class[]]
    }

    void 'customizes an operation after its annotations, and leaves out one a customizer returns nothing for'() {
        given:
        def customizers = customizers(operationCustomizers: [{ Operation operation, HandlerMethod handlerMethod ->
            Method action = handlerMethod.method
            action.name == 'delete'
                    ? null
                    : operation.summary("${handlerMethod.beanType.simpleName}.${action.name}(${action.parameterCount})".toString())
        } as OperationCustomizer])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'listedWidget') },
                OpenApiFixture.application([ListedWidgetController], [new ListedWidgetController()]),
                OpenApiFixture.context([Widget, Crate]))

        when:
        def openApi = generator.generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then: 'with the method each action is declared as'
        openApi.paths['/widgets'].get.summary == 'ListedWidgetController.index(1)'
        openApi.paths['/widgets/{id}'].get.summary == 'ListedWidgetController.show(0)'

        and:
        openApi.paths['/widgets/{id}'].delete == null
    }

    void 'describes a controller that is not a singleton without creating it'() {
        given: 'a controller in the prototype scope, and a customizer reading the handler of each operation'
        CountedWidgetController.created = 0
        def context = new GenericApplicationContext()
        def definition = new RootBeanDefinition(CountedWidgetController)
        definition.scope = BeanDefinition.SCOPE_PROTOTYPE
        context.registerBeanDefinition(CountedWidgetController.name, definition)
        context.refresh()
        def application = OpenApiFixture.application([CountedWidgetController]).tap { it.mainContext = context }
        def customizers = customizers(operationCustomizers: [{ Operation operation, HandlerMethod handlerMethod ->
            operation.addExtension('x-controller', handlerMethod.beanType.simpleName)
            operation
        } as OperationCustomizer])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'countedWidget') },
                application, OpenApiFixture.context([Widget, Crate]))

        when: 'two documents are described'
        generator.generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))
        def openApi = generator.generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then: 'no controller was created'
        CountedWidgetController.created == 0

        and: 'a customizer is still given the controller handling each operation'
        openApi.paths['/widgets'].get.extensions['x-controller'] == 'CountedWidgetController'

        cleanup:
        context?.close()
    }

    void 'applies the operation customizers of a group, and the global ones, to the Grails operations'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator(true))
        beanFactory.registerSingleton('springDocCustomizers', customizers(globalOperationCustomizers: [
                new GlobalOperationComponentsCustomizer() {
                    Operation customize(Operation operation, Components components, HandlerMethod handlerMethod) {
                        operation.addExtension('x-components', components != null)
                        operation
                    }

                    Operation customize(Operation operation, HandlerMethod handlerMethod) {
                        operation
                    }
                }]))
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/widgets/**')
                .addOperationCustomizer { Operation operation, HandlerMethod handlerMethod ->
                    operation.operationId("${handlerMethod.beanType.simpleName}_${handlerMethod.method.name}".toString())
                }.build())
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory

        when:
        contributor.postProcessBeforeInitialization(resource(beanFactory), 'multipleOpenApiResource')
        def openApi = document(beanFactory.getBean('widgets', GroupedOpenApi))

        then: 'each is given the handler method of the action: the controller, and the method it is declared as'
        openApi.paths['/widgets'].get.operationId == 'WidgetController_index'
        openApi.paths['/widgets/{id}'].delete.operationId == 'WidgetController_delete'

        and: 'a customizer of the components is given them'
        openApi.paths['/widgets'].get.extensions['x-components'] == true
    }

    void 'applies the operation customizers springdoc applies to its default document'() {
        given:
        def customizers = customizers(operationCustomizers: [{ Operation operation, HandlerMethod handlerMethod ->
            operation.summary(handlerMethod.method.name)
        } as OperationCustomizer])

        when:
        def openApi = generator(true).generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then:
        openApi.paths['/widgets/{id}'].get.summary == 'show'
        openApi.paths['/gate'].get.summary == 'index'
    }

    void 'contributes to a group ahead of the global customizers springdoc puts first'() {
        given:
        Set<String> seen = []
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('generator', generator())
        beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets').pathsToMatch('/widgets/**').build())
        def group = beanFactory.getBean('widgets', GroupedOpenApi)
        def resource = resource(beanFactory)
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = beanFactory

        when: 'springdoc prepares the group, adding a global customizer as it does'
        contributor.postProcessBeforeInitialization(resource, 'multipleOpenApiResource')
        group.addAllOpenApiCustomizer([{ OpenAPI openApi -> seen.addAll(openApi.paths?.keySet() ?: []) } as GlobalOpenApiCustomizer])
        contributor.postProcessAfterInitialization(resource, 'multipleOpenApiResource')
        document(group)

        then: 'the global customizer sees the Grails operations'
        seen == ['/widgets', '/widgets/{id}'] as Set
    }

    void 'contributes to the default document ahead of the other customizers'() {
        given:
        Set<String> seen = []
        def grails = new GrailsOpenApiCustomizer({ -> generator() }, { -> new OpenApiSelection() })
        def customizers = customizers(openApiCustomizers: [
                { OpenAPI openApi -> seen.addAll(openApi.paths?.keySet() ?: []) } as OpenApiCustomizer, grails])
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = new DefaultListableBeanFactory()

        when:
        contributor.postProcessAfterInitialization(customizers, 'springDocCustomizers')
        def openApi = new OpenAPI()
        customizers.openApiCustomizers.get().each { it.customise(openApi) }

        then:
        customizers.openApiCustomizers.get().first().is(grails)
        seen == ['/widgets', '/widgets/{id}', '/gate'] as Set
    }

    void 'skips a filter or a customizer that fails, rather than the action it was applied to'() {
        given: 'a filter and a customizer that read the current request, where there is none'
        def customizers = customizers(
                methodFilters: [{ Method action -> throw new IllegalStateException('No current request') } as OpenApiMethodFilter],
                operationCustomizers: [{ Operation operation, HandlerMethod handlerMethod ->
                    throw new IllegalStateException('No current request')
                } as OperationCustomizer])

        when:
        def openApi = generator(true).generate(SpringdocSelection.defaultSelection(new OpenApiSelection(), customizers))

        then:
        openApi.paths['/widgets/{id}'].get
        openApi.paths['/widgets/{id}'].delete
        openApi.paths['/gate'].get
    }

    void 'leaves every other bean alone'() {
        given:
        def contributor = new GroupedOpenApiContributor()
        contributor.beanFactory = new DefaultListableBeanFactory()
        def bean = new Object()

        expect:
        contributor.postProcessBeforeInitialization(bean, 'other').is(bean)
        contributor.postProcessAfterInitialization(bean, 'other').is(bean)
    }

    void 'reads the groups an application declares, so they are generated at build time too'() {
        given:
        def context = new org.springframework.context.support.GenericApplicationContext()
        context.beanFactory.registerSingleton('gates', GroupedOpenApi.builder().group('gates').displayName('Gates')
                .pathsToMatch('/gate/**').packagesToExclude('com.example').producesToMatch('application/json')
                .consumesToMatch('text/xml').headersToMatch('X-Api-Version=1').build())
        context.refresh()

        when:
        def groups = GroupedOpenApiContributor.declaredGroups(context)

        then:
        groups*.group == ['gates']
        groups[0].displayName == 'Gates'
        groups[0].pathsToMatch == ['/gate/**']
        groups[0].packagesToExclude == ['com.example']
        groups[0].producesToMatch == ['application/json']
        groups[0].consumesToMatch == ['text/xml']
        groups[0].headersToMatch == ['X-Api-Version=1']

        cleanup:
        context.close()
    }

    void 'generates a group with the global method filters at build time, where springdoc has not added them'() {
        given:
        def context = new org.springframework.context.support.GenericApplicationContext()
        context.beanFactory.registerSingleton('springDocCustomizers',
                customizers(globalMethodFilters: [{ Method action -> action.name != 'delete' } as GlobalOpenApiMethodFilter]))
        context.beanFactory.registerSingleton('widgets', GroupedOpenApi.builder().group('widgets')
                .pathsToMatch('/widgets/**').build())
        context.refresh()

        when:
        def openApi = generator().generate(GroupedOpenApiContributor.declaredGroups(context).first())

        then:
        openApi.paths['/widgets/{id}'].get
        openApi.paths['/widgets/{id}'].delete == null

        cleanup:
        context.close()
    }

    private static OpenAPI document(GroupedOpenApi group) {
        def openApi = new OpenAPI()
        group.openApiCustomizers.each { it.customise(openApi) }
        openApi
    }

    private static Set<String> operations(OpenAPI openApi) {
        openApi.paths.collectMany { String path, item ->
            item.readOperationsMap().keySet().collect { "${it} ${path}".toString() }
        } as Set<String>
    }

    private static SpringDocCustomizers customizers(Map<String, Collection> declared) {
        new SpringDocCustomizers(
                Optional.of((declared.openApiCustomizers ?: []) as LinkedHashSet),
                Optional.of((declared.operationCustomizers ?: []) as LinkedHashSet),
                Optional.empty(), Optional.empty(),
                Optional.of((declared.methodFilters ?: []) as LinkedHashSet),
                Optional.of((declared.globalOpenApiCustomizers ?: []) as LinkedHashSet),
                Optional.of((declared.globalOperationCustomizers ?: []) as LinkedHashSet),
                Optional.of((declared.globalMethodFilters ?: []) as LinkedHashSet),
                Optional.empty(), Optional.empty())
    }

    private static Set<String> paths(GroupedOpenApi group) {
        def openApi = new OpenAPI()
        group.openApiCustomizers.each { it.customise(openApi) }
        openApi.paths.keySet()
    }

    private static MultipleOpenApiWebMvcResource resource(DefaultListableBeanFactory beanFactory) {
        new MultipleOpenApiWebMvcResource(beanFactory.getBeansOfType(GroupedOpenApi).values().toList(),
                null, null, null, null, null, null, null)
    }

    private static GrailsOpenApiGenerator generator(boolean withControllers = false) {
        def application = withControllers
                ? OpenApiFixture.application([WidgetController, GateController], [new WidgetController(), new GateController()])
                : OpenApiFixture.application([WidgetController, GateController])
        OpenApiFixture.generator(OpenApiFixture.holder {
            '/widgets'(resources: 'widget')
            '/gate'(controller: 'gate', action: 'index', namespace: 'v1')
        }, application, OpenApiFixture.context([Widget, Crate]))
    }
}

@Artefact('Controller')
class AnnotatedWidgetController extends RestfulController<Widget> {

    AnnotatedWidgetController() {
        super(Widget)
    }

    @OperationAnnotation(summary = 'Fetch all widgets')
    @Override
    Object index() {
        super.index(10)
    }
}

@Artefact('Controller')
class ListedWidgetController extends RestfulController<Widget> {

    ListedWidgetController() {
        super(Widget)
    }

    @OperationAnnotation(summary = 'List the widgets')
    @Override
    Object index(Integer max) {
        null
    }
}

@Artefact('Controller')
class CountedWidgetController extends RestfulController<Widget> {

    static int created

    CountedWidgetController() {
        super(Widget)
        created++
    }
}
