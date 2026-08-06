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
package org.grails.plugins.sitemesh3

import groovy.transform.CompileStatic

import org.sitemesh.webmvc.SiteMeshViewResolver
import org.sitemesh.webmvc.SiteMeshViewResolverPostProcessor

import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.core.type.MethodMetadata
import org.springframework.util.ClassUtils

import org.grails.plugins.web.GroovyPagesPostProcessor

/**
 * Grails-flavoured {@link SiteMeshViewResolverPostProcessor} — the upstream
 * bean-definition wrap mode ({@code sitemesh.viewResolver.wrapMode=bean-definition})
 * expressed with Grails semantics. It rewrites the {@code jspViewResolver} bean
 * definition into a {@link GrailsSiteMeshViewResolver} definition, so that every
 * instantiation of the bean — however early — yields the decorating resolver.
 *
 * <p>Wrapping at the bean-definition level (rather than post-processing the bean
 * instance) closes an initialization-order race: {@code jspViewResolver} is
 * registered lazy, so it is instantiated by whichever component first asks for
 * it. If that consumer is initialized before the SiteMesh
 * {@code BeanPostProcessor} takes effect — Spring Boot's
 * {@code ContentNegotiatingViewResolver} collecting every {@code ViewResolver}
 * while it initializes is one such consumer — it captures the raw, undecorating
 * resolver and keeps rendering through it, silently disabling layouts. The
 * rewrite itself — embedding the original definition as an anonymous inner
 * bean, invisible to type scans — is inherited from the upstream implementation;
 * this subclass adds only the Grails-specific guards.</p>
 *
 * <p>Runs after {@link GroovyPagesPostProcessor} (which contributes the default
 * GSP resolver definition when no plugin has registered one) so the definition
 * being wrapped is final, whether it came from grails-gsp, the scaffolding
 * plugin, or the application.</p>
 */
@CompileStatic
class Sitemesh3ViewResolverDefinitionPostProcessor extends SiteMeshViewResolverPostProcessor {

    /**
     * After {@link GroovyPagesPostProcessor#ORDER} so the default GSP resolver
     * definition exists whichever module contributed it, making this the last
     * word on the {@code jspViewResolver} definition.
     */
    public static final int ORDER = GroovyPagesPostProcessor.ORDER + 10

    Sitemesh3ViewResolverDefinitionPostProcessor() {
        targetViewResolverBeanName = GrailsSiteMeshViewResolverBeanPostProcessor.TARGET_VIEW_RESOLVER_BEAN_NAME
        siteMeshViewResolverClass = GrailsSiteMeshViewResolver
        order = ORDER
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (Sitemesh3EnvironmentPostProcessor.isSiteMesh2Present()) {
            // Migration tolerance: with grails-layout on the classpath the
            // SiteMesh 2 integration owns decoration and this module stands
            // down (warned about in Sitemesh3EnvironmentPostProcessor).
            return
        }
        if (!registry.containsBeanDefinition(targetViewResolverBeanName) ||
                !registry.containsBeanDefinition(contentProcessorBeanName) ||
                !registry.containsBeanDefinition(decoratorSelectorBeanName)) {
            // Decoration is not possible in this context (no GSP view resolver, or a
            // context without the SiteMesh beans, e.g. the lightweight unit-test
            // contexts built by grails-testing-support) — leave the definition alone.
            // Returning here also keeps the upstream missing-target warning out of
            // those contexts.
            return
        }
        BeanDefinition existing = registry.getBeanDefinition(targetViewResolverBeanName)
        if (isFactoryMethodReturningDecorator(existing, registry)) {
            return
        }
        super.postProcessBeanDefinitionRegistry(registry)

        // Upstream always marks the wrapper lazy; preserve the target's own
        // eagerness so an eagerly-declared resolver keeps initialising at startup.
        BeanDefinition wrapper = registry.getBeanDefinition(targetViewResolverBeanName)
        if (!wrapper.is(existing)) {
            wrapper.lazyInit = existing.lazyInit
        }
    }

    /**
     * The upstream already-decorating guard recognises definitions by bean
     * class name; a {@code @Bean} factory-method definition carries no class
     * name, so a configuration-class method returning a
     * {@link SiteMeshViewResolver} would slip past it and be double-wrapped.
     * The return type is resolved with the bean class loader the container
     * itself will use, so application classes in a child or restart class
     * loader (e.g. devtools) are visible to the check.
     */
    private boolean isFactoryMethodReturningDecorator(BeanDefinition definition, BeanDefinitionRegistry registry) {
        if (definition.beanClassName != null || !(definition instanceof AnnotatedBeanDefinition)) {
            return false
        }
        MethodMetadata factoryMethod = ((AnnotatedBeanDefinition) definition).factoryMethodMetadata
        if (factoryMethod == null) {
            return false
        }
        ClassLoader loader = registry instanceof ConfigurableBeanFactory ?
                ((ConfigurableBeanFactory) registry).beanClassLoader :
                ClassUtils.defaultClassLoader
        try {
            return SiteMeshViewResolver.isAssignableFrom(
                    ClassUtils.forName(factoryMethod.returnTypeName, loader))
        }
        catch (ClassNotFoundException | LinkageError ignored) {
            return false
        }
    }
}
