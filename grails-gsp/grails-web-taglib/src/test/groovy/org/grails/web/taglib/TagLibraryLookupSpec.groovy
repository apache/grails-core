package org.grails.web.taglib

import grails.core.DefaultGrailsApplication
import grails.gsp.TagLib
import org.grails.taglib.NamespacedTagDispatcher
import org.grails.taglib.TagLibraryLookup
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Issue
import spock.lang.Specification
import spock.util.mop.ConfineMetaClassChanges

/**
 * Created by graemerocher on 13/05/14.
 */
class TagLibraryLookupSpec extends Specification {


    @Issue('GRAILS-11396')
    @ConfineMetaClassChanges([NamespacedTagDispatcher, OneTagLib, TwoTagLib])
    void "Test that TagLibraryLookup correctly registers namespace dispatchers"() {
        given:"A lookup instance"
            def lookup = new TagLibraryLookup()

            def application = new DefaultGrailsApplication([OneTagLib, TwoTagLib] as Class[], TagLibraryLookup.class.classLoader)
            application.initialise()
            def applicationContext = new GenericWebApplicationContext()

            applicationContext.defaultListableBeanFactory.registerSingleton(OneTagLib.name, new OneTagLib(tagLibraryLookup: lookup))
            applicationContext.defaultListableBeanFactory.registerSingleton(TwoTagLib.name, new TwoTagLib(tagLibraryLookup: lookup))
            applicationContext.defaultListableBeanFactory.registerSingleton("gspTagLibraryLookup", lookup)
            // instanceTagLibraryApi(TagLibraryApi, pluginManager)
            applicationContext.refresh()

            lookup.grailsApplication = application
            lookup.applicationContext = applicationContext
            lookup.afterPropertiesSet()

            def context = new MockServletContext()
            context.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext)
            RequestContextHolder.setRequestAttributes(new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), context, applicationContext))

        when:"We lookup a namespace"
            def result = lookup.lookupNamespaceDispatcher("g").methodMissing("foo", [test:"me"])

        then:"the result is correct"
            result.toString() == "good"


        cleanup:"cleanup request context"
            RequestContextHolder.resetRequestAttributes()

    }
}
@TagLib
class OneTagLib {
    def foo = { attrs ->
        out << two.foo(attrs)
    }
}
@TagLib
class TwoTagLib {
    static namespace = "two"
    def foo = { attrs ->
        out << "good"
    }
}
