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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.JstlUtils

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class IterativeJspTagTests {

    GrailsWebRequest webRequest

    @BeforeEach
    protected void setUp() {
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.getCurrentRequest().setAttribute(GroovyPagesServlet.SERVLET_INSTANCE, new GroovyPagesServlet())
    }

    @AfterEach
    protected void tearDown() {
        RequestContextHolder.resetRequestAttributes()
        GroovySystem.metaClassRegistry.removeMetaClass TagLibraryResolverImpl
    }

    @Test
    void testIterativeTag() {
        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication = new DefaultGrailsApplication()
        resolver.tldScanPatterns = ['classpath*:/META-INF/c.tld'] as String[]
        resolver.resourceLoader = new DefaultResourceLoader(this.class.classLoader)

        JspTagLib tagLib = resolver.resolveTagLibrary("jakarta.tags.core")
        assert tagLib

        JspTag formatNumberTag = tagLib.getTag("forEach")
        assert formatNumberTag

        def writer = new StringWriter()

        JstlUtils.exposeLocalizationContext webRequest.getRequest(), null

        int count = 0
        def pageContext = PageContextFactory.getCurrent()
        def array = []
        formatNumberTag.doTag(writer, [items: [1, 2, 3], var: "num"]) {
            array << pageContext.getAttribute("num")
            count++
        }

        Assertions.assertEquals 3, count
        assertEquals([1, 2, 3], array)
        // forEach is a TryCatchFinally tag and should remove all attributes in the scope at the end of the loop
        assertNull pageContext.getAttribute("num")
    }
}
