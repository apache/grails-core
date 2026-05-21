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
package org.grails.gsp.jsp

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.JstlUtils
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleJspTagTests extends Specification {

    @Shared
    GrailsWebRequest webRequest

    void setup() {
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.getCurrentRequest().setAttribute(GroovyPagesServlet.SERVLET_INSTANCE, new GroovyPagesServlet())
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void testSimpleTagUsage() {
        given:
        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication = new DefaultGrailsApplication()
        resolver.tldScanPatterns = ['classpath*:/META-INF/fmt.tld'] as String[]
        resolver.resourceLoader = new DefaultResourceLoader(this.class.classLoader)
        
        when:
        def tagLib = resolver.resolveTagLibrary('jakarta.tags.fmt')

        then:
        tagLib

        when:
        def formatNumberTag = tagLib.getTag('formatNumber')

        then:
        formatNumberTag

        when:
        def writer = new StringWriter()
        JstlUtils.exposeLocalizationContext(webRequest.getRequest(), null)
        formatNumberTag.doTag(writer, [value: '10', pattern: '.00'])

        then:
        writer.toString() == '10.00'
    }
}
