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
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.mock.web.MockServletContext
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Scans the JSTL descriptors that declare a JSP 1.2 {@code DOCTYPE}, as shipped in
 * {@code jakarta.servlet.jsp.jstl}. The default {@code grails.gsp.tldScanPattern} ends with
 * {@code c-1_0-rt.tld}, which is one of them, and the resolver scans every pattern in one pass, so
 * a parser that refused the declaration would leave no JSP tag library resolvable in any
 * application that adds JSTL.
 */
class JstlDocTypeTldSpec extends Specification {

    private TagLibraryResolverImpl resolverScanning(String... patterns) {
        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication = new DefaultGrailsApplication()
        resolver.tldScanPatterns = patterns
        resolver.resourceLoader = new DefaultResourceLoader(this.class.classLoader)
        resolver
    }

    void 'a descriptor declaring a doctype does not stop the scan'() {
        given: 'the default scan order: the schema-based c.tld first, then the JSP 1.2 c-1_0-rt.tld'
        def resolver = resolverScanning('classpath*:/META-INF/c.tld', 'classpath*:/META-INF/c-1_0-rt.tld')

        expect: 'the descriptor scanned before the declaration still resolves'
        resolver.resolveTagLibrary('jakarta.tags.core')?.getTag('out')

        and: 'so does the descriptor that declares it'
        resolver.resolveTagLibrary('http://java.sun.com/jstl/core_rt')?.getTag('out')
    }

    @Unroll
    void 'the JSP 1.2 descriptor for #uri resolves'() {
        given:
        def resolver = resolverScanning(
                'classpath*:/META-INF/c-1_0*.tld',
                'classpath*:/META-INF/fmt-1_0*.tld',
                'classpath*:/META-INF/sql-1_0*.tld',
                'classpath*:/META-INF/x-1_0*.tld')

        expect:
        resolver.resolveTagLibrary(uri)

        where:
        uri << ['core', 'core_rt', 'fmt', 'fmt_rt', 'sql', 'sql_rt', 'xml', 'xml_rt']
                .collect { "http://java.sun.com/jstl/$it".toString() }
    }
}
