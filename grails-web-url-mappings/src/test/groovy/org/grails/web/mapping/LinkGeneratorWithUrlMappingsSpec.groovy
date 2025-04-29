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


import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.CamelCaseUrlConverter
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * More tests for {@link grails.web.mapping.LinkGenerator }. See Also LinkGeneratorSpec.
 *
 * These test focus on testing integration with the URL mappings to ensure they are respected.
 */
class LinkGeneratorWithUrlMappingsSpec extends Specification{

    def baseUrl = "http://myserver.com/foo"
    def context = null
    def path = "welcome"
    def action = [controller:'home', action:'index']

    def mappings = {
        "/${this.path}"(this.action)
    }

    def link = new LinkedHashMap(action)

    protected getGenerator() {
        def generator = new DefaultLinkGenerator(baseUrl, context)
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings ?: {}))
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator
    }

    protected getUri() {
        generator.link(link)
    }

    void "link is prefixed by the deployment context, and uses path specified in the mapping"() {
        when:
            context = "/bar"

        then:
            uri == "$context/$path"
    }

    void "absolute links are prefixed by the base url, don't contain the deployment context, and use path specified in the mapping"() {
        when:
            context = "/bar"

        and:
            link.absolute = true

        then:
            uri == "$baseUrl/$path"
    }

    void "absolute links are generated when a relative link is asked for, but the deployment context is not known or set"() {
        when:
            context = null

        then:
            uri == "$baseUrl/$path"
    }
}
