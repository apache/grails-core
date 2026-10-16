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
package org.apache.grails.web.layout

import com.opensymphony.module.sitemesh.Factory
import com.opensymphony.sitemesh.Content
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import spock.lang.Specification

import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.support.StaticApplicationContext
import org.springframework.core.Ordered
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

import grails.core.GrailsApplication

/**
 * Tests {@link GrailsLayoutViewResolver} initialisation of the SiteMesh factory and creation of
 * {@link GrailsLayoutView} instances.
 */
class GrailsLayoutViewResolverSpec extends Specification {

    static final String FACTORY_ATTRIBUTE = 'grails.layout.factory'

    MockServletContext servletContext = new MockServletContext()
    GroovyPageLayoutFinder finder = Mock(GroovyPageLayoutFinder)
    View inner = Stub(View) {
        render(_, _, _) >> { Map model, def req, def resp ->
            resp.setContentType('text/html;charset=UTF-8')
            resp.writer.write('<html><body>B</body></html>')
            resp.writer.flush()
        }
    }
    ViewResolver innerResolver = Stub(ViewResolver) {
        resolveViewName('page', _) >> inner
    }

    def cleanup() {
        FactoryHolder.setFactory(null)
        RequestContextHolder.resetRequestAttributes()
    }

    private GrailsLayoutViewResolver resolver() {
        def resolver = new GrailsLayoutViewResolver(innerResolver, finder)
        resolver.setServletContext(servletContext)
        resolver
    }

    private static StaticApplicationContext contextWith(ObservationRegistry registry = null) {
        def context = new StaticApplicationContext()
        if (registry != null) {
            context.beanFactory.registerSingleton('observationRegistry', registry)
        }
        context.refresh()
        context
    }

    void 'the default order sits before the embedded resolver and can be changed'() {
        given:
        def resolver = new GrailsLayoutViewResolver()

        expect:
        resolver.order == Ordered.LOWEST_PRECEDENCE - 50

        when:
        resolver.order = 1

        then:
        resolver.order == 1
    }

    void 'the Grails application can be injected'() {
        given:
        def resolver = new GrailsLayoutViewResolver()
        def application = Stub(GrailsApplication)

        when:
        resolver.setGrailsApplication(application)

        then:
        resolver.grailsApplication.is(application)
    }

    void 'init does nothing without a servlet context'() {
        given:
        def resolver = new GrailsLayoutViewResolver(innerResolver, finder)

        when:
        resolver.init()
        resolver.destroy()

        then:
        FactoryHolder.getFactory() == null
    }

    void 'a context refresh loads the layout configuration and publishes the factory'() {
        given:
        def resolver = resolver()

        when:
        resolver.onApplicationEvent(new ContextRefreshedEvent(contextWith()))

        then:
        servletContext.getAttribute(FACTORY_ATTRIBUTE) instanceof Factory
        FactoryHolder.getFactory().is(servletContext.getAttribute(FACTORY_ATTRIBUTE))
        FactoryHolder.getGrailsLayoutFactory() != null
    }

    void 'an already published factory is reused'() {
        given:
        def existing = Stub(Factory)
        servletContext.setAttribute(FACTORY_ATTRIBUTE, existing)
        def resolver = resolver()

        when:
        resolver.init()

        then:
        servletContext.getAttribute(FACTORY_ATTRIBUTE).is(existing)
        FactoryHolder.getFactory() == null
    }

    void 'destroy removes the factory it loaded'() {
        given:
        def resolver = resolver()
        resolver.onApplicationEvent(new ContextRefreshedEvent(contextWith()))

        when:
        resolver.destroy()

        then:
        servletContext.getAttribute(FACTORY_ATTRIBUTE) == null
        FactoryHolder.getFactory() == null
    }

    void 'destroy leaves a factory it did not load alone'() {
        given:
        def existing = Stub(Factory)
        servletContext.setAttribute(FACTORY_ATTRIBUTE, existing)
        def resolver = resolver()
        resolver.init()

        when:
        resolver.destroy()

        then:
        servletContext.getAttribute(FACTORY_ATTRIBUTE).is(existing)
    }

    void 'resolved views are layout views that decorate html with the configured content processor'() {
        given:
        def resolver = resolver()
        resolver.onApplicationEvent(new ContextRefreshedEvent(contextWith()))
        def request = new MockHttpServletRequest(servletContext)
        def response = new MockHttpServletResponse()

        when:
        def view = resolver.resolveViewName('page', Locale.ENGLISH)
        view.render([:], request, response)

        then:
        view instanceof GrailsLayoutView
        1 * finder.findLayout(request, _ as Content) >> null
        response.contentAsString == '<html><body>B</body></html>'
    }

    void 'layout views observe decoration with the observation registry found on refresh'() {
        given:
        List<Observation.Context> recorded = []
        def registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            boolean supportsContext(Observation.Context context) {
                true
            }

            @Override
            void onStop(Observation.Context context) {
                recorded << context
            }
        })
        def resolver = resolver()
        resolver.onApplicationEvent(new ContextRefreshedEvent(contextWith(registry)))
        def decorator = new SpringMVCViewDecorator('main', Stub(View))
        def request = new MockHttpServletRequest(servletContext)
        def response = new MockHttpServletResponse()

        when:
        resolver.resolveViewName('page', Locale.ENGLISH).render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> decorator
        recorded*.name == ['gsp.layout']
    }
}
