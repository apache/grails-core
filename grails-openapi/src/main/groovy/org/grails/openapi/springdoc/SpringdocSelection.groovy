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

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springdoc.core.customizers.GlobalOperationComponentsCustomizer
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.customizers.SpringDocCustomizers
import org.springdoc.core.filters.GlobalOpenApiMethodFilter
import org.springdoc.core.filters.OpenApiMethodFilter
import org.springdoc.core.models.GroupedOpenApi
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springframework.beans.factory.BeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.web.method.HandlerMethod

import grails.core.GrailsControllerClass
import grails.openapi.OpenApiSelection
import org.grails.openapi.ActionHooks

/**
 * What a document springdoc serves selects: its criteria, and the method filters and operation
 * customizers springdoc applies to its handler methods, applied to the Grails actions too.
 *
 * <p>A filter or customizer that fails, such as one that reads the current request where there is
 * none, is skipped and logged, rather than the action it was applied to.</p>
 */
@CompileStatic
class SpringdocSelection extends OpenApiSelection implements ActionHooks {

    private static final Logger LOG = LoggerFactory.getLogger(SpringdocSelection)

    private final List<OpenApiMethodFilter> methodFilters
    private final List<OperationCustomizer> operationCustomizers
    private final Set<Object> reported = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>())

    SpringdocSelection(OpenApiSelection criteria, Collection<? extends OpenApiMethodFilter> methodFilters,
                       Collection<? extends OperationCustomizer> operationCustomizers) {
        super(criteria)
        this.methodFilters = new ArrayList<OpenApiMethodFilter>(methodFilters ?: [])
        this.operationCustomizers = new ArrayList<OperationCustomizer>(operationCustomizers ?: [])
    }

    /**
     * The default document selects what the criteria select, among the actions every springdoc
     * method filter includes, and gives each operation to springdoc's operation customizers.
     */
    static SpringdocSelection defaultSelection(OpenApiSelection criteria, SpringDocCustomizers customizers) {
        new SpringdocSelection(criteria, customizers?.methodFilters?.orElse(null),
                customizers?.operationCustomizers?.orElse(null))
    }

    /**
     * A group selects what its criteria select, among the actions its method filters and the
     * global ones include, and gives each operation to its operation customizers and the global ones.
     */
    static SpringdocSelection groupSelection(GroupedOpenApi group, SpringDocCustomizers customizers,
                                             SpringDocConfigProperties properties = null) {
        // springdoc adds the global filters and customizers to each group as it prepares the group,
        // which it has not where the document is generated without serving it.
        Set<OpenApiMethodFilter> filters = new LinkedHashSet<>()
        Set<GlobalOpenApiMethodFilter> globalFilters = customizers?.globalOpenApiMethodFilters?.orElse(null)
        if (globalFilters) {
            filters.addAll(globalFilters)
        }
        if (group.openApiMethodFilters) {
            filters.addAll(group.openApiMethodFilters)
        }
        Set<OperationCustomizer> operationCustomizers = new LinkedHashSet<>()
        Set<GlobalOperationCustomizer> globalCustomizers = customizers?.globalOperationCustomizers?.orElse(null)
        if (globalCustomizers) {
            operationCustomizers.addAll(globalCustomizers)
        }
        if (group.operationCustomizers) {
            operationCustomizers.addAll(group.operationCustomizers)
        }
        new SpringdocSelection(GroupedOpenApiContributor.selectionOf(group, properties), filters, operationCustomizers)
    }

    /**
     * springdoc's configuration, where springdoc is configured in the context.
     */
    static SpringDocConfigProperties properties(BeanFactory beanFactory) {
        beanFactory?.getBeanProvider(SpringDocConfigProperties)?.getIfAvailable()
    }

    /**
     * springdoc's customizers, where springdoc is configured in the context.
     */
    static SpringDocCustomizers customizers(BeanFactory beanFactory) {
        beanFactory?.getBeanProvider(SpringDocCustomizers)?.getIfAvailable()
    }

    @Override
    boolean selectsAction(Method action) {
        if (action == null) {
            return true
        }
        methodFilters.every { OpenApiMethodFilter filter -> includes(filter, action) }
    }

    /**
     * A customizer is given the handler method springdoc would give it for a Spring MVC endpoint:
     * the controller bean, by name, as Spring MVC registers a handler, and the method the action is
     * declared as. The controller is not created for it.
     */
    @Override
    Operation customize(Operation operation, Components components, GrailsControllerClass controller, Method action) {
        ApplicationContext context = controller?.application?.mainContext
        if (context == null || action == null || !context.containsBean(controller.fullName)) {
            return operation
        }
        HandlerMethod handlerMethod = new HandlerMethod(controller.fullName, context, action)
        Operation customized = operation
        for (OperationCustomizer customizer : operationCustomizers) {
            if (customized == null) {
                break
            }
            customized = customize(customizer, customized, components, handlerMethod)
        }
        customized
    }

    private boolean includes(OpenApiMethodFilter filter, Method action) {
        try {
            return filter.isMethodToInclude(action)
        }
        catch (RuntimeException | LinkageError e) {
            skipped(filter, "${action.declaringClass.name}.${action.name}".toString(), e)
            return true
        }
    }

    private Operation customize(OperationCustomizer customizer, Operation operation, Components components,
                                HandlerMethod handlerMethod) {
        try {
            return customizer instanceof GlobalOperationComponentsCustomizer
                    ? ((GlobalOperationComponentsCustomizer) customizer).customize(operation, components, handlerMethod)
                    : customizer.customize(operation, handlerMethod)
        }
        catch (RuntimeException | LinkageError e) {
            skipped(customizer, "${handlerMethod.beanType.name}.${handlerMethod.method.name}".toString(), e)
            return operation
        }
    }

    /**
     * Reported once for each document, where it would otherwise be for every action.
     */
    private void skipped(Object applied, String action, Throwable failure) {
        if (reported.add(applied)) {
            LOG.warn('Skipping the springdoc [{}], which failed for the action [{}]: {}', applied.getClass().name,
                    action, failure.message)
        }
        LOG.debug('Could not apply [{}] to [{}]', applied.getClass().name, action, failure)
    }
}
