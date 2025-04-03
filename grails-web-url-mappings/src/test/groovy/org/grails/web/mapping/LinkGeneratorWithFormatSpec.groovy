package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.CamelCaseUrlConverter
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 05/06/15.
 */
class LinkGeneratorWithFormatSpec extends Specification {

    def baseUrl = "http://myserver.com/foo"
    def context = null
    def path = "welcome"

    def mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

    }

    void setupSpec() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Issue('https://github.com/apache/grails-core/issues/589')
    void "Test that a link containing the format parameter generates correctly"() {
        when:
            def theLink =
                    generator.link(controller:"one", action:"two", params:[format:'json'])
        then:
            theLink  == 'http://myserver.com/foo/one/two.json'
    }


    protected getGenerator() {
        def generator = new DefaultLinkGenerator(baseUrl, context)
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings ?: {}))
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator
    }

}
