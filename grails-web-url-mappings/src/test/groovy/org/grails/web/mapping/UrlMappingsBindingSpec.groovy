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

import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Specification
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import grails.core.GrailsApplication
import grails.core.DefaultGrailsApplication

/**
 * Tests that focus on ensuring the applicationContext, grailsApplication and servletContext objects are available to UrlMappings
 */
class UrlMappingsBindingSpec extends Specification{

    void "Test that common applications variables are available in UrlMappings"() {
        when:"Mappings that use application variables"
            def evaluator = getEvaluator()
            def urlMappings = new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings))

        then:"The url mappings are valid"
            urlMappings != null
    }

    protected DefaultUrlMappingEvaluator getEvaluator() {
        final servletContext = new MockServletContext()
        final ctx = new GenericWebApplicationContext(servletContext)
        ctx.defaultListableBeanFactory.registerSingleton(GrailsApplication.APPLICATION_ID,new DefaultGrailsApplication())
        ctx.refresh()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        return new DefaultUrlMappingEvaluator(ctx)
    }

    Closure getMappings() {
        return {
            "/foo" {
                assert applicationContext != null
                assert grailsApplication != null
                assert servletContext != null
            }
        }
    }
}
