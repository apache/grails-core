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

import com.opensymphony.module.sitemesh.HTMLPage
import com.opensymphony.sitemesh.Content
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.AbstractGrailsView
import org.grails.web.servlet.view.GrailsViewResolver
import org.grails.web.servlet.view.LayoutViewResolver
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import grails.web.pages.GroovyPagesUriService
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll
import jakarta.servlet.http.HttpServletRequest

class GroovyPageLayoutFinderSpec extends Specification {

    @Issue('https://github.com/apache/grails-core/issues/10371')
    @Unroll('Layout name mapping: #layoutName => #expectedPath')
    void 'testing layout name resolution'() {
        given:
        GroovyPageLayoutFinder layoutFinder = new GroovyPageLayoutFinder()
        HttpServletRequest request = new MockHttpServletRequest()
        layoutFinder.viewResolver = Mock(GrailsViewResolver)
        when:
        layoutFinder.getNamedDecorator(request, layoutName, false)

        then:
        1 * layoutFinder.viewResolver.resolveViewName(expectedPath, Locale.ENGLISH)
        notThrown(Exception)

        where:
        layoutName         | expectedPath
        'foo'              | '/layouts/foo'
        'foo/bar'          | '/layouts/foo/bar'
        '../foo'           | '/foo'
        '../foo/bar'       | '/foo/bar'
        '../../foo'        | '/../foo'
        '../foo/../../bar' | '/../bar'
        'foo/../../bar'    | '/bar'
    }

    private GroovyPageLayoutFinder finder = new GroovyPageLayoutFinder()
    private MockHttpServletRequest request = new MockHttpServletRequest()
    private Map<String, View> views = [:]
    private List<String> resolved = []

    def setup() {
        finder.viewResolver = Stub(ViewResolver) {
            resolveViewName(_ as String, _) >> { String name, Locale locale ->
                resolved << name
                views[name]
            }
        }
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private AbstractGrailsView grailsView() {
        Stub(AbstractGrailsView)
    }

    private HTMLPage pageWithLayout(String layout) {
        Stub(HTMLPage) {
            getProperty('meta.layout') >> layout
        }
    }

    private void bindController(GroovyObject controller, String controllerName = null) {
        def webRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), new MockServletContext())
        webRequest.controllerName = controllerName
        RequestContextHolder.setRequestAttributes(webRequest)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        request.setAttribute(GroovyPagesUriService.RENDERING_VIEW_ATTRIBUTE, true)
    }

    void 'the finder is ordered just before the lowest precedence'() {
        expect:
        finder.order == Ordered.LOWEST_PRECEDENCE - 1
    }

    void 'no layout is found when the request is not rendering a view and names no layout'() {
        expect:
        finder.findLayout(request, pageWithLayout('main')) == null
        resolved.isEmpty()
    }

    void 'a layout request attribute selects the named layout'() {
        given:
        views['/layouts/main'] = grailsView()
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, 'main')

        when:
        def decorator = finder.findLayout(request, pageWithLayout('ignored'))

        then:
        decorator instanceof SpringMVCViewDecorator
        decorator.name == 'main'
        resolved == ['/layouts/main']
    }

    void 'the none layout disables decoration'() {
        given:
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, WebUtils.NONE_LAYOUT)

        expect:
        finder.findLayout(request, pageWithLayout('ignored')) == null
        resolved.isEmpty()
    }

    void 'the meta layout property of the page selects the layout when rendering a view'() {
        given:
        views['/layouts/fromMeta'] = grailsView()
        request.setAttribute(GroovyPagesUriService.RENDERING_VIEW_ATTRIBUTE, true)

        expect:
        finder.findLayout(request, pageWithLayout('fromMeta')).name == 'fromMeta'
    }

    void 'content is converted to a page before the layout is looked up'() {
        given:
        views['/layouts/fromContent'] = grailsView()
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, 'fromContent')
        Content content = new GSPGrailsLayoutPage()

        expect:
        finder.findLayout(request, content).name == 'fromContent'
    }

    void 'without a controller the application default layout is used when it is a Grails view'() {
        given:
        views['/layouts/application'] = grailsView()
        request.setAttribute(GroovyPagesUriService.RENDERING_VIEW_ATTRIBUTE, true)

        expect:
        finder.findLayout(request, pageWithLayout(null)).name == 'application'
    }

    void 'the application default layout must be a Grails view unless non GSP views are enabled with a default name'() {
        given:
        views['/layouts/application'] = Stub(View)
        views['/layouts/custom'] = Stub(View)
        request.setAttribute(GroovyPagesUriService.RENDERING_VIEW_ATTRIBUTE, true)

        expect:
        finder.findLayout(request, pageWithLayout(null)) == null

        when:
        finder.setCacheEnabled(false)
        finder.setEnableNonGspViews(true)

        then: 'without a default name the view must still exist as a Grails view'
        finder.findLayout(request, pageWithLayout(null)) == null

        when:
        finder.setDefaultDecoratorName('custom')

        then:
        finder.findLayout(request, pageWithLayout(null)).name == 'custom'
    }

    void 'a layout resolved by name is cached when caching is enabled'() {
        given:
        views['/layouts/main'] = grailsView()
        finder.setCacheEnabled(true)
        finder.setGspReloadEnabled(true)

        when:
        def first = finder.getNamedDecorator(request, 'main')
        def second = finder.getNamedDecorator(request, 'main')

        then:
        first.is(second)
        resolved == ['/layouts/main']
    }

    void 'a missing layout is cached as missing'() {
        given:
        finder.setCacheEnabled(true)

        expect:
        finder.getNamedDecorator(request, 'missing') == null
        finder.getNamedDecorator(request, 'missing') == null
        resolved == ['/layouts/missing']
    }

    void 'layouts are resolved every time when caching is disabled'() {
        given:
        views['/layouts/main'] = grailsView()
        finder.setCacheEnabled(false)

        when:
        def first = finder.getNamedDecorator(request, 'main')
        def second = finder.getNamedDecorator(request, 'main')

        then:
        !first.is(second)
        resolved == ['/layouts/main', '/layouts/main']
    }

    void 'blank and none layout names never resolve a view'() {
        expect:
        finder.getNamedDecorator(request, null) == null
        finder.getNamedDecorator(request, '  ') == null
        finder.getNamedDecorator(request, WebUtils.NONE_LAYOUT) == null
        resolved.isEmpty()
    }

    void 'non Grails views are only accepted for named layouts when non GSP views are enabled'() {
        given:
        views['/layouts/jsp'] = Stub(View)
        finder.setCacheEnabled(false)

        expect:
        finder.getNamedDecorator(request, 'jsp') == null

        when:
        finder.setEnableNonGspViews(true)

        then:
        finder.getNamedDecorator(request, 'jsp').name == 'jsp'
    }

    void 'a failure to resolve a layout view is reported with the cause'() {
        given:
        def cause = new IllegalArgumentException('bad')
        finder.viewResolver = Stub(ViewResolver) {
            resolveViewName(_ as String, _) >> { throw cause }
        }

        when:
        finder.getNamedDecorator(request, 'main')

        then:
        def e = thrown(RuntimeException)
        e.message == 'Unable to resolve view'
        e.cause.is(cause)
    }

    void 'a layout view resolver is unwrapped to its inner view resolver'() {
        given:
        views['/layouts/main'] = grailsView()
        def inner = Stub(ViewResolver) {
            resolveViewName('/layouts/main', _) >> views['/layouts/main']
        }
        def wrapper = Stub(LayoutViewResolver) {
            getInnerViewResolver() >> inner
        }

        when:
        finder.setViewResolver(wrapper)

        then:
        finder.getNamedDecorator(request, 'main').name == 'main'
    }

    void 'a controller with a static layout property uses that layout'() {
        given:
        views['/layouts/custom'] = grailsView()
        bindController(new StaticLayoutController(actionUri: '/static/index'), 'static')

        expect:
        finder.findLayout(request, pageWithLayout(null)).name == 'custom'
    }

    void 'a controller falls back from the action layout to the controller layout to the application layout'() {
        given:
        bindController(new PlainController(actionUri: '/plain/index'), 'plain')
        finder.setCacheEnabled(false)

        when: 'the action layout exists'
        views['/layouts/plain/index'] = grailsView()

        then:
        finder.findLayout(request, pageWithLayout(null)).name == 'plain/index'

        when: 'only the controller layout exists'
        views.clear()
        views['/layouts/plain'] = grailsView()

        then:
        finder.findLayout(request, pageWithLayout(null)).name == 'plain'

        when: 'only the application layout exists'
        views.clear()
        views['/layouts/application'] = grailsView()

        then:
        finder.findLayout(request, pageWithLayout(null)).name == 'application'

        when: 'no layout exists'
        views.clear()

        then:
        finder.findLayout(request, pageWithLayout(null)) == null
    }

    void 'the controller name is derived from the controller class when the web request has none'() {
        given:
        views['/layouts/derived'] = grailsView()
        bindController(new DerivedController(actionUri: '/derived/list'))

        expect:
        finder.findLayout(request, pageWithLayout(null)).name == 'derived'
    }

    void 'a controller without an action uri finds no layout'() {
        given:
        bindController(new PlainController(), 'plain')
        views['/layouts/application'] = grailsView()

        expect:
        finder.findLayout(request, pageWithLayout(null)) == null
    }

    void 'the layout resolved for a controller action is cached, including a missing layout'() {
        given:
        bindController(new PlainController(actionUri: '/plain/index'), 'plain')
        views['/layouts/plain'] = grailsView()
        finder.setCacheEnabled(true)

        when:
        def first = finder.findLayout(request, pageWithLayout(null))
        int lookups = resolved.size()
        def second = finder.findLayout(request, pageWithLayout(null))

        then:
        first.name == 'plain'
        second.is(first)
        resolved.size() == lookups

        when: 'a different action with no layouts at all is cached as missing'
        views.clear()
        finder.setCacheEnabled(true)
        bindController(new PlainController(actionUri: '/other/index'), 'other')
        def none = finder.findLayout(request, pageWithLayout(null))
        int afterFirst = resolved.size()
        def noneAgain = finder.findLayout(request, pageWithLayout(null))

        then:
        none == null
        noneAgain == null
        resolved.size() == afterFirst
    }

    void 'the controller layout cache honours gsp reload without expiring recent entries'() {
        given:
        bindController(new PlainController(actionUri: '/plain/index'), 'plain')
        views['/layouts/plain'] = grailsView()
        finder.setGspReloadEnabled(true)

        when:
        def first = finder.findLayout(request, pageWithLayout(null))
        def second = finder.findLayout(request, pageWithLayout(null))

        then:
        second.is(first)
    }

    void 'a refreshed application context supplies the Grails view resolver when none is configured'() {
        given:
        def grailsViewResolver = Stub(GrailsViewResolver) {
            resolveViewName('/layouts/main', _) >> grailsView()
        }
        def context = Stub(ApplicationContext) {
            getBean(GrailsViewResolver) >> grailsViewResolver
        }
        finder.viewResolver = null

        when:
        finder.onApplicationEvent(new ContextRefreshedEvent(context))

        then:
        finder.getNamedDecorator(request, 'main').name == 'main'
    }

    void 'a refreshed application context does not replace an existing Grails view resolver'() {
        given:
        def configured = Stub(GrailsViewResolver) {
            resolveViewName('/layouts/main', _) >> grailsView()
        }
        def context = Mock(ApplicationContext)
        finder.setViewResolver(configured)

        when:
        finder.onApplicationEvent(new ContextRefreshedEvent(context))

        then:
        0 * context.getBean(_)
        finder.getNamedDecorator(request, 'main').name == 'main'
    }
}

class PlainController {
    String actionUri
}

class DerivedController {
    String actionUri
}

class StaticLayoutController {
    static layout = 'custom'
    String actionUri
}

