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
package org.grails.web.mapping

import grails.build.logging.GrailsConsole
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
import org.springframework.mock.web.MockServletContext
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class UrlMappingsWithHttpMethodSpec extends Specification{

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }
    
    def mappings = {
         "/foo"( controller:"bar", action:"save", method:"POST" )
         "/foo2"( controller:"bar", action:"save", method:"PUT" )
         "/foo"( controller:"bar", action:"update", method:"PUT" )
         "/foo"( controller:"bar", action:"patch", method:"PATCH" )
         "/bar"( controller:"bar", action:"list", method:"*" )
         "/bar2"( controller:"bar", action:"list" )

    }

    void "Test that the http method can be used as a prefix to URL mappings"() {
        given:"A URL mapping evaluator"
            def ctx = new MockApplicationContext()
            ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
            def evaluator = new DefaultUrlMappingEvaluator(ctx)

        when:"The mappings are evaluated"
            def mappings = evaluator.evaluateMappings mappings

        then:"The mapping only accepts POST requests"
            mappings.size() == 6
            mappings[0].httpMethod == 'POST'
            mappings[1].httpMethod == 'PUT'

    }

    void "Test that URL mappings can be applied only to a certain HTTP method"() {
        when:"A URL is matched for a valid HTTP method"
            def results = urlMappingsHolder.matchAll('/foo', 'POST')
        then:"A match is found"
            results.size() == 1
            results[0].controllerName == 'bar'
            results[0].actionName == 'save'

        when:"A URL is matched with an invalid HTTP method"
             results = urlMappingsHolder.matchAll('/foo', 'GET')
        then:"No results are found"
            results.size() == 0

        expect:"A URL mapping that matches any HTTP method returns results"
            urlMappingsHolder.matchAll('/bar', 'GET').size() == 1
            urlMappingsHolder.matchAll('/bar', 'POST').size() == 1
            urlMappingsHolder.matchAll('/bar2', 'GET').size() == 1



    }

    void "Test that the HTTP method is taken into account when generating links"() {
        expect:"A link is generated with a specified HTTP method the results are correct"
            linkGenerator.link( controller:"bar", action:"list", method:'GET') == 'http://localhost/bar'
            linkGenerator.link( controller:"bar", action:"save", method:"POST" ) == 'http://localhost/foo'
            linkGenerator.link( controller:"bar", action:"save", method:"PUT" ) == 'http://localhost/foo2'
            linkGenerator.link( controller:"bar", action:"patch", method:"PATCH" ) == 'http://localhost/foo'
            linkGenerator.link( controller:"bar", action:"list") == 'http://localhost/bar'

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
