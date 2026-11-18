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

import jakarta.servlet.ServletContext

import org.sitemesh.DecoratorSelector
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.tagrules.TagBasedContentProcessor
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.View
import org.springframework.web.servlet.view.InternalResourceViewResolver
import spock.lang.Specification

/**
 * Covers the resolver being built without a servlet context argument and receiving one from the
 * container instead. A plain bean factory applies no {@code ServletContextAware} callback, so the
 * path only exists in a real web application context.
 */
class GrailsSiteMeshViewResolverServletContextSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()
    GenericWebApplicationContext context = new GenericWebApplicationContext(servletContext)

    void setup() {
        def target = new GenericBeanDefinition()
        target.beanClass = InternalResourceViewResolver
        context.registerBeanDefinition('jspViewResolver', target)

        // definitions rather than singletons: the post-processor stands down unless the SiteMesh
        // collaborators are present as bean definitions
        context.registerBeanDefinition('contentProcessor',
                new GenericBeanDefinition(beanClass: CaptureAwareContentProcessor))
        def selector = new GenericBeanDefinition(beanClass: Sitemesh3LayoutFinder, lazyInit: true)
        selector.constructorArgumentValues.addIndexedArgumentValue(0, null)
        context.registerBeanDefinition('decoratorSelector', selector)
    }

    void cleanup() {
        context.close()
    }

    void 'the rewritten resolver takes its servlet context from the container'() {
        given:
            new Sitemesh3ViewResolverDefinitionPostProcessor().postProcessBeanDefinitionRegistry(context)

        when:
            context.refresh()

        then: 'nothing declares a servletContext bean definition for it to reference'
            !context.containsBeanDefinition('servletContext')

        and:
            def resolver = context.getBean('jspViewResolver', GrailsSiteMeshViewResolver)
            resolver.servletContext.is(servletContext)
    }

    void 'the injected servlet context reaches the view the resolver produces'() {
        given:
            new Sitemesh3ViewResolverDefinitionPostProcessor().postProcessBeanDefinitionRegistry(context)
            context.refresh()

        when: 'a view is resolved, which is where the servlet context is read'
            def resolver = context.getBean('jspViewResolver', GrailsSiteMeshViewResolver)
            View view = resolver.resolveViewName('someView', Locale.ENGLISH)

        then:
            view instanceof GrailsSiteMeshView
            ((GrailsSiteMeshView) view).servletContext.is(servletContext)
    }

    void 'a resolver built with an explicit servlet context still carries it'() {
        given: 'the constructor callers outside the container callback use'
            ContentProcessor processor = new TagBasedContentProcessor(new CoreHtmlTagRuleBundle())
            DecoratorSelector<SiteMeshContext> selector = { content, ctx -> new String[0] }
            ServletContext explicit = new MockServletContext()

        when:
            def resolver = new GrailsSiteMeshViewResolver(
                    new InternalResourceViewResolver(), processor, selector, explicit)

        then:
            resolver.servletContext.is(explicit)
    }
}
