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

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.OpenAPI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springdoc.api.AbstractMultipleOpenApiResource
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.SpringDocCustomizers
import org.springdoc.core.models.GroupedOpenApi
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.ApplicationContext

import grails.openapi.GrailsOpenApiGenerator
import grails.openapi.OpenApiSelection

/**
 * Contributes the Grails description to every springdoc group.
 *
 * <p>springdoc gives a group only the customizers the group itself carries and the global ones,
 * and a global customizer cannot tell which group it is customizing. So each group - one this
 * module registers for {@code grails.openapi.groups}, or one the application declares - is given
 * its own customizer, selecting what the group's criteria select, before springdoc builds the
 * group's document.</p>
 *
 * <p>The Grails description is contributed ahead of every other {@code OpenApiCustomizer}, in each
 * group and in the default document, so one the application declares sees the Grails operations.
 * springdoc runs its {@code OpenApiLocaleCustomizer}s before any of them, so those do not.</p>
 */
@CompileStatic
class GroupedOpenApiContributor implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger LOG = LoggerFactory.getLogger(GroupedOpenApiContributor)

    private ListableBeanFactory beanFactory

    @Override
    void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ListableBeanFactory) beanFactory
    }

    @Override
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AbstractMultipleOpenApiResource) {
            Collection<GroupedOpenApi> groups = beanFactory.getBeansOfType(GroupedOpenApi).values()
            // springdoc serves the same groups through the actuator too, where it is configured to,
            // so a group is given the Grails description by the first of them only.
            List<GroupedOpenApi> described = groups.findAll { GroupedOpenApi group ->
                !group.openApiCustomizers.any { OpenApiCustomizer it -> it instanceof GrailsOpenApiCustomizer }
            }.toList()
            if (described) {
                warnOfRepeatedGroups(groups)
            }
            described.each { GroupedOpenApi group ->
                // Read as the document is built, once springdoc has given the group the global
                // method filters.
                group.addAllOpenApiCustomizer([new GrailsOpenApiCustomizer(
                        { -> beanFactory.getBean(GrailsOpenApiGenerator) },
                        { ->
                            SpringdocSelection.groupSelection(group, SpringdocSelection.customizers(beanFactory),
                                    SpringdocSelection.properties(beanFactory))
                        })])
            }
        }
        bean
    }

    @Override
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AbstractMultipleOpenApiResource) {
            // springdoc puts the global customizers ahead of a group's own as it prepares the group.
            beanFactory.getBeansOfType(GroupedOpenApi).values().each { GroupedOpenApi group ->
                contributeFirst(group.openApiCustomizers)
            }
        }
        else if (bean instanceof SpringDocCustomizers) {
            ((SpringDocCustomizers) bean).openApiCustomizers.ifPresent { Set<OpenApiCustomizer> customizers ->
                contributeFirst(customizers)
            }
        }
        bean
    }

    /**
     * Moves the Grails contribution ahead of the other customizers, in the set springdoc applies.
     */
    private static void contributeFirst(Set<OpenApiCustomizer> customizers) {
        List<OpenApiCustomizer> grails = customizers.findAll { OpenApiCustomizer it -> it instanceof GrailsOpenApiCustomizer }.toList()
        if (!grails) {
            return
        }
        List<OpenApiCustomizer> others = customizers.findAll { OpenApiCustomizer it -> !(it instanceof GrailsOpenApiCustomizer) }.toList()
        try {
            customizers.clear()
            customizers.addAll(grails)
            customizers.addAll(others)
        }
        catch (UnsupportedOperationException ignored) {
            LOG.warn('The springdoc customizers cannot be reordered, so a customizer declared before the Grails ' +
                    'description is contributed does not see the Grails operations')
        }
    }

    /**
     * The groups an application declares to springdoc, so the {@code generate-open-api} command
     * writes a document for each of them too.
     */
    static List<OpenApiSelection> declaredGroups(ApplicationContext applicationContext) {
        Collection<GroupedOpenApi> groups = applicationContext.getBeansOfType(GroupedOpenApi).values()
        warnOfRepeatedGroups(groups)
        groups.collect { GroupedOpenApi group ->
            (OpenApiSelection) SpringdocSelection.groupSelection(group, SpringdocSelection.customizers(applicationContext),
                    SpringdocSelection.properties(applicationContext))
        }
    }

    /**
     * Applies to a document generated without serving it the customizers springdoc applies to the
     * document it serves: the default document's, or a group's own and the global ones. The one
     * contributing the Grails description is left out, since the document already has it. A
     * customizer that cannot customize a document outside a request is skipped, and logged.
     *
     * @param group the group, or {@code null} for the default document
     */
    static void customize(ApplicationContext applicationContext, String group, OpenAPI openApi) {
        SpringDocCustomizers customizers = SpringdocSelection.customizers(applicationContext)
        Set<OpenApiCustomizer> applied = new LinkedHashSet<>()
        if (group == null) {
            Set<OpenApiCustomizer> declared = customizers?.openApiCustomizers?.orElse(null)
            if (declared) {
                applied.addAll(declared)
            }
        }
        else {
            Set<GlobalOpenApiCustomizer> globals = customizers?.globalOpenApiCustomizers?.orElse(null)
            if (globals) {
                applied.addAll(globals)
            }
            GroupedOpenApi declared = applicationContext.getBeansOfType(GroupedOpenApi).values()
                    .find { GroupedOpenApi it -> it.group == group }
            if (declared?.openApiCustomizers) {
                applied.addAll(declared.openApiCustomizers)
            }
        }
        for (OpenApiCustomizer customizer : applied) {
            if (customizer instanceof GrailsOpenApiCustomizer) {
                continue
            }
            try {
                customizer.customise(openApi)
            }
            catch (RuntimeException e) {
                LOG.warn('Skipping the OpenAPI customizer [{}] outside a request: {}', customizer.getClass().name, e.message)
                LOG.debug('Could not apply the OpenAPI customizer [{}]', customizer.getClass().name, e)
            }
        }
    }

    /**
     * The document an application generates without serving it: its default document, with
     * springdoc's method filters applied, where springdoc is configured.
     */
    static OpenApiSelection defaultSelection(ApplicationContext applicationContext, OpenApiSelection criteria) {
        SpringdocSelection.defaultSelection(criteria, SpringdocSelection.customizers(applicationContext))
    }

    /**
     * A group name declared more than once, across {@code GroupedOpenApi} beans,
     * {@code springdoc.group-configs} and {@code grails.openapi.groups}, is served as the first
     * declaration of it, where springdoc may select its Spring MVC endpoints by another.
     */
    private static void warnOfRepeatedGroups(Collection<GroupedOpenApi> groups) {
        groups.countBy { GroupedOpenApi group -> group.group }.each { String name, Integer declarations ->
            if (declarations > 1) {
                LOG.warn('The OpenAPI group [{}] is declared {} times; its document is described by the first', name,
                        declarations)
            }
        }
    }

    /**
     * What a group selects, as springdoc selects it: springdoc's own top-level criteria apply to
     * every group, and a group's criterion only where the top-level one is empty.
     *
     * @param properties springdoc's configuration, where springdoc is configured
     */
    static OpenApiSelection selectionOf(GroupedOpenApi group, SpringDocConfigProperties properties = null) {
        new OpenApiSelection(
                group: group.group,
                displayName: group.displayName,
                pathsToMatch: criterion(properties?.pathsToMatch, group.pathsToMatch),
                pathsToExclude: criterion(properties?.pathsToExclude, group.pathsToExclude),
                packagesToScan: criterion(properties?.packagesToScan, group.packagesToScan),
                packagesToExclude: criterion(properties?.packagesToExclude, group.packagesToExclude),
                producesToMatch: criterion(properties?.producesToMatch, group.producesToMatch),
                consumesToMatch: criterion(properties?.consumesToMatch, group.consumesToMatch),
                headersToMatch: criterion(properties?.headersToMatch, group.headersToMatch))
    }

    private static List<String> criterion(List<String> topLevel, List<String> group) {
        new ArrayList<String>(topLevel ?: group ?: Collections.<String> emptyList())
    }
}
