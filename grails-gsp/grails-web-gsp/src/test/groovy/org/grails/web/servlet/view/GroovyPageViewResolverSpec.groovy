package org.grails.web.servlet.view

import grails.util.GrailsWebMockUtil
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View
import org.springframework.web.servlet.view.InternalResourceView
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest

class GroovyPageViewResolverSpec extends Specification {
    def "should use namespace as part of cache key"() {
        given:
        GroovyPageViewResolver resolver = new GroovyPageViewResolver() {
            protected View createGrailsView(String viewName) throws Exception {
                return new InternalResourceView(viewName);
            }
        }
        resolver.templateEngine = new GroovyPagesTemplateEngine()
        def pageLocator = Mock(GrailsConventionGroovyPageLocator)
        pageLocator.resolveViewFormat(_) >> { String viewName -> viewName }
        resolver.groovyPageLocator = pageLocator
        resolver.allowGrailsViewCaching = true
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'a')
        when:
        def view = resolver.resolveViewName('/hello/hello',null)
        def view2 = resolver.resolveViewName('/hello/hello',null)
        then:
        view.is(view2)
        when:
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'b')
        def view3 = resolver.resolveViewName('/hello/hello',null)
        then:
        !view2.is(view3)
        when:
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'a')
        def view4 = resolver.resolveViewName('/hello/hello',null)
        then:
        view2.is(view4)
    }
    
    def "should use pluginContextPath as part of cache key"() {
        given:
        GroovyPageViewResolver resolver = new GroovyPageViewResolver() {
            protected View createGrailsView(String viewName) throws Exception {
                return new InternalResourceView(viewName);
            }
        }
        resolver.templateEngine = new GroovyPagesTemplateEngine()
        def pageLocator = Mock(GrailsConventionGroovyPageLocator)
        pageLocator.resolveViewFormat(_) >> { String viewName -> viewName }
        resolver.groovyPageLocator = pageLocator
        resolver.allowGrailsViewCaching = true
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def grailsAppAttributes = Mock(GrailsApplicationAttributes)
        webRequest.attributes = grailsAppAttributes
        grailsAppAttributes.getPluginContextPath(_ as HttpServletRequest) >> { HttpServletRequest request -> webRequest.currentRequest.getAttribute('currentPlugin') }
        
        webRequest.currentRequest.setAttribute('currentPlugin', 'a')
        when:
        def view = resolver.resolveViewName('/hello/hello',null)
        def view2 = resolver.resolveViewName('/hello/hello',null)
        then:
        view.is(view2)
        when:
        webRequest.currentRequest.setAttribute('currentPlugin', 'b')
        def view3 = resolver.resolveViewName('/hello/hello',null)
        then:
        !view2.is(view3)
        when:
        webRequest.currentRequest.setAttribute('currentPlugin', 'a')
        def view4 = resolver.resolveViewName('/hello/hello',null)
        then:
        view2.is(view4)
    }
    
    def "should use pluginContextPath and namespace as part of cache key"() {
        given:
        GroovyPageViewResolver resolver = new GroovyPageViewResolver() {
            protected View createGrailsView(String viewName) throws Exception {
                return new InternalResourceView(viewName);
            }
        }
        resolver.templateEngine = new GroovyPagesTemplateEngine()
        def pageLocator = Mock(GrailsConventionGroovyPageLocator)
        pageLocator.resolveViewFormat(_) >> { String viewName -> viewName }
        resolver.groovyPageLocator = pageLocator
        resolver.allowGrailsViewCaching = true
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def grailsAppAttributes = Mock(GrailsApplicationAttributes)
        webRequest.attributes = grailsAppAttributes
        grailsAppAttributes.getPluginContextPath(_ as HttpServletRequest) >> { HttpServletRequest request -> webRequest.currentRequest.getAttribute('currentPlugin') }
        
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'a')
        webRequest.currentRequest.setAttribute('currentPlugin', 'a')
        when:
        def view = resolver.resolveViewName('/hello/hello',null)
        def view2 = resolver.resolveViewName('/hello/hello',null)
        then:
        view.is(view2)
        when:
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'b')
        def view3a = resolver.resolveViewName('/hello/hello',null)
        then:
        !view2.is(view3a)
        when:
        webRequest.currentRequest.setAttribute('currentPlugin', 'b')
        def view3b = resolver.resolveViewName('/hello/hello',null)
        then:
        !view2.is(view3b)
        when:
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, 'a')
        webRequest.currentRequest.setAttribute('currentPlugin', 'a')
        def view4 = resolver.resolveViewName('/hello/hello',null)
        then:
        view2.is(view4)
    }

    
    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
}

