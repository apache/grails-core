package grails.plugin.json.view

import grails.plugin.json.view.mvc.JsonViewResolver
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.TemplateResolver
import grails.views.WritableScriptTemplate
import grails.views.api.GrailsView
import grails.views.mvc.GenericGroovyTemplateView
import grails.views.mvc.GenericGroovyTemplateViewResolver
import grails.web.http.HttpHeaders
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Created by graemerocher on 24/08/15.
 */
class JsonViewTemplateResolverSpec extends Specification {

    void "Test resolve paths for locale"() {
        given:"A view resolver"
        def viewResolver = new JsonViewResolver()
        def templateResolver = Mock(TemplateResolver)
        viewResolver.templateResolver = templateResolver

        when:"We resolve a view uri"
        viewResolver.resolveView("/foo/bar", Locale.ENGLISH)

        then:"We get calls to resolve views"
        1 * templateResolver.resolveTemplate('/foo/bar_en.gson')
        1 * templateResolver.resolveTemplate('/foo/bar.gson')

        when:"We resolve a view uri"
        viewResolver.resolveView("/foo/bar", Locale.ENGLISH)

        then:"Calls were cached"
        0 * templateResolver.resolveTemplate('/foo/bar_en.gson')
        0 * templateResolver.resolveTemplate('/foo/bar.gson')

    }


    void "Test resolve paths for local and request version"() {
        given:"A view resolver"
        def viewResolver = new JsonViewResolver()
        def applicationAttributes = Mock(GrailsApplicationAttributes)
        applicationAttributes.getControllerUri(_) >> "/test"

        def webRequest = Mock(GrailsWebRequest)
        webRequest.getAttributes() >> applicationAttributes
        RequestContextHolder.setRequestAttributes(webRequest)
        def request = Mock(HttpServletRequest)
        def response = Mock(HttpServletResponse)
        request.getHeader(HttpHeaders.ACCEPT_VERSION) >> "1.1"
        request.getLocale() >> Locale.ENGLISH
        webRequest.getCurrentRequest() >> request
        webRequest.getRequest() >> request
        webRequest.getResponse() >> response
        def templateResolver = Mock(TemplateResolver)
        viewResolver.templateResolver = templateResolver

        when:"We resolve a view uri"
        viewResolver.resolveView("/foo/bar", request, response)

        then:"We get calls to resolve views"
        1 * templateResolver.resolveTemplate('/foo/bar.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_1.1.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en_1.1.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_1.1_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en_1.1_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_html_1.1.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en_html_1.1.gson')


        cleanup:
        RequestContextHolder.setRequestAttributes(null)

    }


    void "Test that the template resolver works for absolute view URI"() {
        given:"A viewResolver with a mock template resolver"
        def viewResolver = new JsonViewResolver()

        def templateResolver = Mock(TemplateResolver)

        def templateEngine = Mock(ResolvableGroovyTemplateEngine)
        templateResolver.resolveTemplate('/foo/bar.gson') >> new URL("file://foo/bar.gson")
        templateEngine.resolveTemplate(_,_) >> new WritableScriptTemplate(GrailsView.class)

        viewResolver.templateResolver = templateResolver
        viewResolver.templateEngine = templateEngine


        when:"We resolve a template"
        GenericGroovyTemplateView view = (GenericGroovyTemplateView)viewResolver.resolveView("/foo/bar", Locale.ENGLISH)

        then:"The view is not null"
        view != null
        view.url == '/foo/bar.gson'
        view.templateEngine != null
        view.contentType == 'application/json'


    }

    void "Test that the template resolver works for relative URI"() {
        given:"A viewResolver with a mock template resolver"

        def smartResolver = new JsonViewResolver()
        def viewResolver = new GenericGroovyTemplateViewResolver(smartResolver)


        def webRequest = Mock(GrailsWebRequest)

        def applicationAttributes = Mock(GrailsApplicationAttributes)
        applicationAttributes.getControllerUri(_) >> "/test"
        webRequest.getAttributes() >> applicationAttributes
        webRequest.getCurrentRequest() >> new MockHttpServletRequest()
        RequestContextHolder.setRequestAttributes(webRequest)
        def templateResolver = Mock(TemplateResolver)

        smartResolver.templateEngine.templateResolver = templateResolver

        when:"We resolve a template"
        GenericGroovyTemplateView view = (GenericGroovyTemplateView)viewResolver.resolveViewName("bar", Locale.ENGLISH)

        then:"The view is not null"
        1 * templateResolver.resolveTemplateClass('/test/bar.gson')
        1 * templateResolver.resolveTemplateClass('/test/bar_en.gson')
        1 * templateResolver.resolveTemplateClass('/test/bar_html.gson')
        1 * templateResolver.resolveTemplateClass('/test/bar_en_html.gson')
        1 * templateResolver.resolveTemplate('/test/bar.gson')
        1 * templateResolver.resolveTemplate('/test/bar_en.gson')
        1 * templateResolver.resolveTemplate('/test/bar_html.gson')
        1 * templateResolver.resolveTemplate('/test/bar_en_html.gson')

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    @Issue('https://github.com/grails/grails-core/issues/10582')
    void 'Test that the template resolver works for a Request URI'() {
        given: 'a viewResolver with a mock template resolver'
        def smartResolver = new JsonViewResolver()
        def viewResolver = new GenericGroovyTemplateViewResolver(smartResolver)

        def webRequest = Mock(GrailsWebRequest)

        and: 'the default controller URI'
        def applicationAttributes = Mock(GrailsApplicationAttributes)
        applicationAttributes.getControllerUri(_) >> "/test"
        webRequest.getAttributes() >> applicationAttributes

        and: 'the actual URI because of a redirect'
        webRequest.getCurrentRequest() >> new MockHttpServletRequest("", "/foo")
        RequestContextHolder.setRequestAttributes(webRequest)
        def templateResolver = Mock(TemplateResolver)

        smartResolver.templateEngine.templateResolver = templateResolver

        when: 'we resolve a template'
        GenericGroovyTemplateView view = (GenericGroovyTemplateView)viewResolver.resolveViewName("bar", Locale.ENGLISH)

        then: 'the view is not null'
        1 * templateResolver.resolveTemplateClass('/foo/bar.gson')
        1 * templateResolver.resolveTemplateClass('/foo/bar_en.gson')
        1 * templateResolver.resolveTemplateClass('/foo/bar_html.gson')
        1 * templateResolver.resolveTemplateClass('/foo/bar_en_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_html.gson')
        1 * templateResolver.resolveTemplate('/foo/bar_en_html.gson')

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }
}
