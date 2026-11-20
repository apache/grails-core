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
package org.grails.plugins.web.interceptors

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.web.servlet.handler.MappedInterceptor

import grails.core.GrailsApplication
import grails.core.GrailsClass

/**
 * Registers the interceptor bean definitions the interceptors plugin previously contributed through
 * the {@code doWithSpring()} bean DSL: the {@code grailsInterceptorMappedInterceptor} (wrapping the
 * handler adapter as an <em>inner</em> bean) and one bean per interceptor artefact. Interceptor
 * beans autowire by name and the adapter must remain an inner bean — a top-level
 * {@link org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter} would be
 * collected a second time by {@code WebUtils.lookupHandlerInterceptors} and run every interceptor
 * twice — and neither can be expressed through the
 * {@link org.springframework.beans.factory.BeanRegistry} API, so the definitions are contributed
 * here instead.
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the interceptor
 * definitions are registered before Spring Boot's configuration-class post-processor evaluates
 * auto-configuration conditions — the same visibility the {@code doWithSpring()} registration had.
 * An existing definition for an interceptor name wins, preserving the ability of the application
 * (or another plugin) to override an interceptor bean.</p>
 *
 * @since 8.0
 */
@CompileStatic
class InterceptorBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final GrailsApplication grailsApplication
    private final boolean useJsessionId

    InterceptorBeanDefinitionsPostProcessor(GrailsApplication grailsApplication, boolean useJsessionId) {
        this.grailsApplication = grailsApplication
        this.useJsessionId = useJsessionId
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        GrailsClass[] interceptors = grailsApplication.getArtefacts(InterceptorArtefactHandler.TYPE)
        if (interceptors.length == 0) {
            return
        }

        if (!registry.containsBeanDefinition('grailsInterceptorMappedInterceptor')) {
            // The handler adapter is an inner bean of the mapped interceptor (as in the original
            // DSL): inner beans are still fully autowired — the adapter's @Autowired Interceptor[]
            // is injected — but are not returned by getBeansOfType, so the adapter is not collected
            // a second time as a standalone HandlerInterceptor.
            GenericBeanDefinition adapter = new GenericBeanDefinition()
            adapter.beanClass = GrailsInterceptorHandlerInterceptorAdapter
            GenericBeanDefinition mappedInterceptor = new GenericBeanDefinition()
            mappedInterceptor.beanClass = MappedInterceptor
            mappedInterceptor.constructorArgumentValues.addIndexedArgumentValue(0, ['/**'] as String[])
            mappedInterceptor.constructorArgumentValues.addIndexedArgumentValue(1, adapter)
            registry.registerBeanDefinition('grailsInterceptorMappedInterceptor', mappedInterceptor)
        }

        for (GrailsClass interceptorClass in interceptors) {
            if (registry.containsBeanDefinition(interceptorClass.propertyName)) {
                continue
            }
            GenericBeanDefinition definition = new GenericBeanDefinition()
            definition.beanClass = interceptorClass.clazz
            definition.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
            if (useJsessionId) {
                definition.propertyValues.addPropertyValue('useJessionId', useJsessionId)
            }
            registry.registerBeanDefinition(interceptorClass.propertyName, definition)
        }
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
