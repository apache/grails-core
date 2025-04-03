package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils
import spock.lang.Issue
import spock.lang.Specification

class RestfulUrlMappingSpec extends Specification {

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }

    def mappings = {
        delete "/$controller/$id(.$format)?"(action: "delete")
        get "/$controller(.$format)?"(action: "index")
        get "/$controller/$id(.$format)?"(action: "show")
        post "/$controller(.$format)?"(action: "save")
        put "/$controller/$id(.$format)?"(action: "update")
        patch "/$controller/$id(.$format)?"(action: "patch")
    }

    @Issue('https://github.com/apache/grails-core/issues/10995')
    void 'test that the right link is generated for restful mapping'() {
        expect:
        linkGenerator.link(resource: 'user', action: 'show', id: 1, absolute: true) == 'http://localhost/user/1'
        linkGenerator.link(resource: 'user', action: 'show', id: 1, method: 'GET', absolute: true) == 'http://localhost/user/1'
    }

    LinkGenerator getLinkGenerator() {
        def generator = new DefaultLinkGenerator("http://localhost", null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = urlMappingsHolder
        return generator;
    }

    UrlMappingsHolder getUrlMappingsHolder() {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def mappings = evaluator.evaluateMappings mappings
        return new DefaultUrlMappingsHolder(mappings)
    }
}
