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

import org.springframework.beans.factory.BeanFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.InternalResourceViewResolver

import spock.lang.Specification

class GrailsSiteMeshViewResolverBeanPostProcessorSpec extends Specification {

    BeanFactory beanFactory = Mock(BeanFactory)

    static class ListenerViewResolver extends InternalResourceViewResolver implements ApplicationListener<ApplicationEvent> {
        @Override
        void onApplicationEvent(ApplicationEvent event) { }
    }

    void "target bean name defaults to jspViewResolver and wrapper class is GrailsSiteMeshViewResolver"() {
        expect:
        new GrailsSiteMeshViewResolverBeanPostProcessor().targetViewResolverBeanName == 'jspViewResolver'
        new GrailsSiteMeshViewResolverBeanPostProcessor().siteMeshViewResolverClass == GrailsSiteMeshViewResolver
    }

    void "wraps the jspViewResolver bean with GrailsSiteMeshViewResolver"() {
        given:
        ContentProcessor cp = Mock(ContentProcessor)
        DecoratorSelector<SiteMeshContext> ds = Mock(DecoratorSelector)
        ServletContext sc = Mock(ServletContext)
        beanFactory.containsBean('contentProcessor') >> true
        beanFactory.containsBean('decoratorSelector') >> true
        beanFactory.getBean('contentProcessor', ContentProcessor) >> cp
        beanFactory.getBean('decoratorSelector', DecoratorSelector) >> ds
        beanFactory.getBean('servletContext', ServletContext) >> sc

        GrailsSiteMeshViewResolverBeanPostProcessor pp = new GrailsSiteMeshViewResolverBeanPostProcessor()
        pp.setBeanFactory(beanFactory)

        when:
        Object result = pp.postProcessAfterInitialization(new InternalResourceViewResolver(), 'jspViewResolver')

        then:
        result instanceof GrailsSiteMeshViewResolver
    }

    void "beans with a non-matching name are returned untouched"() {
        given:
        beanFactory.containsBean(_ as String) >> true
        GrailsSiteMeshViewResolverBeanPostProcessor pp = new GrailsSiteMeshViewResolverBeanPostProcessor()
        pp.setBeanFactory(beanFactory)
        ViewResolver other = new InternalResourceViewResolver()

        expect:
        pp.postProcessAfterInitialization(other, 'someOther').is(other)
    }

    void "a resolver that is an ApplicationListener, like SiteMesh 2's layout resolver, is left unwrapped"() {
        given:
        beanFactory.containsBean(_ as String) >> true
        GrailsSiteMeshViewResolverBeanPostProcessor pp = new GrailsSiteMeshViewResolverBeanPostProcessor()
        pp.setBeanFactory(beanFactory)
        ViewResolver listenerResolver = new ListenerViewResolver()

        expect:
        pp.postProcessAfterInitialization(listenerResolver, 'jspViewResolver').is(listenerResolver)
    }

    void "the view resolver is left unwrapped when the SiteMesh beans are not in the context"() {
        given: "a context without the plugin's contentProcessor/decoratorSelector beans, like a unit-test context"
        beanFactory.containsBean('contentProcessor') >> hasContentProcessor
        beanFactory.containsBean('decoratorSelector') >> hasDecoratorSelector
        GrailsSiteMeshViewResolverBeanPostProcessor pp = new GrailsSiteMeshViewResolverBeanPostProcessor()
        pp.setBeanFactory(beanFactory)
        ViewResolver resolver = new InternalResourceViewResolver()

        expect:
        pp.postProcessAfterInitialization(resolver, 'jspViewResolver').is(resolver)

        where:
        hasContentProcessor | hasDecoratorSelector
        false               | false
        true                | false
        false               | true
    }
}
