package org.grails.web.servlet.mvc

import grails.testing.web.UrlMappingsUnitTest
import org.grails.web.servlet.mvc.alpha.NamespacedController
import grails.web.mapping.mvc.exceptions.CannotRedirectException
import org.grails.web.util.GrailsApplicationAttributes
import grails.artefact.Artefact
import grails.web.mapping.mvc.RedirectEventListener
import org.springframework.http.HttpStatus
import spock.lang.Specification

/**
 * Tests the behaviour of the redirect method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class RedirectMethodTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {

    void "test redirect with namespaced controllers"() {
        when:
        def primary = new NamespacedController()
        webRequest.controllerName = 'namespaced'
        primary.redirectToSelf()
        then:
        '/noNamespace/demo' ==  response.redirectedUrl

        when:

        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        primary.redirectToSecondary()

        then:
        '/secondaryNamespace/demo' == response.redirectedUrl

        when:
        def secondary = new org.grails.web.servlet.mvc.beta.NamespacedController()
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToPrimary()

        then:
        '/noNamespace/demo' == response.redirectedUrl

        when:
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToSelfWithImplicitNamespace()

        then:
        '/secondaryNamespace/demo' == response.redirectedUrl

        when:
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToSelfWithExplicitNamespace()

        then:
        '/secondaryNamespace/demo' == response.redirectedUrl

        when:
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToAnotherPrimary()

        then:
        '/anotherNoNamespace/demo' == response.redirectedUrl

        when:
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToAnotherSecondaryWithImplicitNamespace()

        then:
        '/anotherSecondaryNamespace/demo' == response.redirectedUrl

        when:
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        secondary.redirectToAnotherSecondaryWithExplicitNamespace()

        then:
        '/anotherSecondaryNamespace/demo' == response.redirectedUrl
    }

    void testRedirectToDefaultActionOfAnotherController() {

        when:
        def c = new NewsSignupController()
        webRequest.controllerName = 'newsSignup'
        c.redirectToDefaultAction()

        then:
        "/redirect" ==  response.redirectedUrl
    }

    void testRedirectEventListeners() {
        when:
        def fired = false
        def callable = { fired = true }

        def c = new RedirectController()
        c.setRedirectListeners([new TestRedirectListener(callable: callable)])
        webRequest.controllerName = 'redirect'

        c.toAction()

        then:"redirect event should have been fired"
        fired
    }

    void testRedirectAlreadyCalledException() {

        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'

        c.redirectTwice()

        then:
        def e = thrown(CannotRedirectException)
        e.message ==
        "Cannot issue a redirect(..) here. A previous call to redirect(..) has already redirected the response."

    }

    void testRedirectWhenResponseCommitted() {

        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'

        c.responseCommitted()

        then:"incorrect error message for response redirect when already written to"
        def e = thrown(CannotRedirectException)
        e.message ==
                "Cannot issue a redirect(..) here. The response has already been committed either by another redirect or by directly writing to the response."

    }

    void testRedirectToRoot() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toRoot()
        then:
        "/" == response.redirectedUrl
    }

    void testRedirectToAbsoluteURL() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toAbsolute()
        then:
        "http://google.com" == response.redirectedUrl

    }

    void testRedirectWithFragment() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toControllerAndActionWithFragment()
        then:
        "/test/foo#frag" == response.redirectedUrl
    }

    void testRedirectInControllerWithOneLetterClassName() {
        when:
        def c = new AController()
        webRequest.controllerName = 'a'
        c.index()

        then:
        "/a/list" == response.redirectedUrl
    }

    void testRedirectInControllerWithAllUpperCaseClassName() {
        when:
        def c = new ABCController()
        webRequest.controllerName = 'ABC'
        c.index()
        then:
        "/ABC/list" == response.redirectedUrl
    }

    void testRedirectToAction() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toAction()
        then:
        "/redirect/foo" == response.redirectedUrl
    }

    void testRedirectToActionWithGstring() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toActionWithGstring()
        then:
        "/redirect/foo" == response.redirectedUrl
    }

    void testRedirectToController() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toController()
        then:
        "/test" == response.redirectedUrl
    }

    void testRedirectToControllerAndAction() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toControllerAndAction()
        then:
        "/test/foo" == response.redirectedUrl
    }

    void testRedirectToControllerWithParams() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toControllerWithParams()
        then:
        "/test/foo?one=two&two=three" == response.redirectedUrl
    }

    void testRedirectToControllerWithDuplicateParams() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toControllerWithDuplicateParams()
        then:
        "/test/foo?one=two&one=three" == response.redirectedUrl
    }

    void testRedirectToControllerWithDuplicateArrayParams() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toControllerWithDuplicateArrayParams()
        then:
        "/test/foo?one=two&one=three" == response.redirectedUrl
    }

    void testRedirectToActionWithMapping() {
        when:
        def c = new NewsSignupController()
        c = new NewsSignupController()
        webRequest.controllerName = 'newsSignup'
        c.testNoController()
        then:
        "/little-brown-bottle/thankyou" ==  response.redirectedUrl
    }

    void "test temporary redirect"() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toActionTemporaryRedirect()
        
        then: "should use HTTP 307 Temporary Redirect"
        HttpStatus.TEMPORARY_REDIRECT.value() == response.status
        "/redirect/foo" == response.redirectedUrl
    }

    void "test permanent redirect"() {
        when:
        def c = new RedirectController() 
        webRequest.controllerName = 'redirect'
        c.toActionPermanentRedirect()
        
        then: "should use HTTP 308 Permanent Redirect"
        HttpStatus.PERMANENT_REDIRECT.value() == response.status
        "/redirect/foo" == response.redirectedUrl
    }

    void "test moved temporary redirect"() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toActionMovedTemporary()

        then: "should use HTTP 302 Moved Temporary"
        HttpStatus.MOVED_TEMPORARILY.value() == response.status
        "/redirect/foo" == response.redirectedUrl
    }

    void "test moved permanent redirect"() {
        when:
        def c = new RedirectController()
        webRequest.controllerName = 'redirect'
        c.toActionMovedPermanent()

        then: "should use HTTP 301 Moved Permanent"
        HttpStatus.MOVED_PERMANENTLY.value() == response.status
        "/redirect/foo" == response.redirectedUrl
    }

    static class UrlMappings {
        static mappings = {
            "/"(controller:'default')
            "/little-brown-bottle/$action?" {
                controller = "newsSignup"
            }

            "/$controller/$action?/$id?"{
                constraints {
                    // apply constraints here
                }
            }
            "/noNamespace/$action?" {
                controller = 'namespaced'
            }
            "/anotherNoNamespace/$action?" {
                controller = 'anotherNamespaced'
            }

            "/secondaryNamespace/$action?" {
                controller = 'namespaced'
                namespace = 'secondary'
            }
            "/anotherSecondaryNamespace/$action?" {
                controller = 'anotherNamespaced'
                namespace = 'secondary'
            }
        }
    }
}

class TestRedirectListener implements RedirectEventListener {

    def callable

    void responseRedirected(String url) {
        callable(url)
    }
}

@Artefact('Controller')
class ABCController {
    def index = { redirect action: 'list' }
}

@Artefact('Controller')
class AController {
    def index = { redirect action: 'list' }
}

@Artefact('Controller')
class NewsSignupController {

    static defaultAction = "thankyou"

    def testNoController = {
        redirect(action: 'thankyou')
    }

    def redirectToDefaultAction = {
        redirect(controller:"redirect")
    }

    def thankyou = {
    }
}
