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
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.web.mapping.UrlMapping
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.junit.Test
import org.springframework.mock.web.MockServletContext

import static org.junit.Assert.*

/**
 * @author graemerocher
 */
class RegexUrlMappingTests {

    @Test
    void testComparable() {
        def parser = new DefaultUrlMappingParser()
        def application = new DefaultGrailsApplication()
        def m1 = new RegexUrlMapping(parser.parse("/foo/"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m2 = new RegexUrlMapping(parser.parse("/"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m3 = new RegexUrlMapping(parser.parse("/foo/bar/(*)"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m4 = new RegexUrlMapping(parser.parse("/foo/(*)/bar"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m5 = new RegexUrlMapping(parser.parse("/(*)/foo/bar"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m6 = new RegexUrlMapping(parser.parse("/foo/(*)"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m7 = new RegexUrlMapping(parser.parse("/(*)"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m8 = new RegexUrlMapping(parser.parse("/foo/(*)/(*)"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m9 = new RegexUrlMapping(parser.parse("/(*)/(*)/bar"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m10 = new RegexUrlMapping(parser.parse("/(*)/(*)/(*)"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)
        def m11 = new RegexUrlMapping(parser.parse("/"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,null, application)


        assertTrue m1.compareTo(m1) == 0
        assertTrue m2.compareTo(m2) == 0
        assertTrue m3.compareTo(m3) == 0
        assertTrue m4.compareTo(m4) == 0
        assertTrue m5.compareTo(m5) == 0
        assertTrue m6.compareTo(m6) == 0
        assertTrue m7.compareTo(m7) == 0
        assertTrue m8.compareTo(m8) == 0
        assertTrue m9.compareTo(m9) == 0
        assertTrue m2.compareTo(m11) == 0
        assertTrue m11.compareTo(m2) == 0
        assertTrue m1.compareTo(m2) < 0
        assertTrue m1.compareTo(m3) < 0
        assertTrue m1.compareTo(m4) < 0
        assertTrue m1.compareTo(m5) > 0
        assertTrue m1.compareTo(m6) > 0
        assertTrue m1.compareTo(m7) > 0
        assertTrue m1.compareTo(m8) > 0
        assertTrue m1.compareTo(m9) > 0
        assertTrue m1.compareTo(m10) > 0

        assertTrue m2.compareTo(m1) > 0
        assertTrue m2.compareTo(m3) > 0
        assertTrue m2.compareTo(m4) > 0
        assertTrue m2.compareTo(m5) > 0
        assertTrue m2.compareTo(m6) > 0
        assertTrue m2.compareTo(m7) > 0
        assertTrue m2.compareTo(m8) > 0
        assertTrue m2.compareTo(m9) > 0
        assertTrue m2.compareTo(m10) > 0

        assertTrue m3.compareTo(m1) > 0
        assertTrue m3.compareTo(m2) < 0
        assertTrue m3.compareTo(m4) > 0
        assertTrue m3.compareTo(m5) > 0
        assertTrue m3.compareTo(m6) > 0
        assertTrue m3.compareTo(m7) > 0
        assertTrue m3.compareTo(m8) > 0
        assertTrue m3.compareTo(m9) > 0
        assertTrue m3.compareTo(m10) > 0

        assertTrue m4.compareTo(m1) > 0
        assertTrue m4.compareTo(m2) < 0
        assertTrue m4.compareTo(m3) < 0
        assertTrue m4.compareTo(m5) > 0
        assertTrue m4.compareTo(m6) > 0
        assertTrue m4.compareTo(m7) > 0
        assertTrue m4.compareTo(m8) > 0
        assertTrue m4.compareTo(m9) > 0
        assertTrue m4.compareTo(m10) > 0

        assertTrue m5.compareTo(m1) < 0
        assertTrue m5.compareTo(m2) < 0
        assertTrue m5.compareTo(m3) < 0
        assertTrue m5.compareTo(m4) < 0
        assertTrue m5.compareTo(m6) < 0
        assertTrue m5.compareTo(m7) > 0
        assertTrue m5.compareTo(m8) < 0
        assertTrue m5.compareTo(m9) > 0
        assertTrue m5.compareTo(m10) > 0

        assertTrue m6.compareTo(m1) < 0
        assertTrue m6.compareTo(m2) < 0
        assertTrue m6.compareTo(m3) < 0
        assertTrue m6.compareTo(m4) < 0
        assertTrue m6.compareTo(m5) > 0
        assertTrue m6.compareTo(m7) > 0
        assertTrue m6.compareTo(m8) > 0
        assertTrue m6.compareTo(m9) > 0
        assertTrue m6.compareTo(m10) > 0

        assertTrue m7.compareTo(m1) < 0
        assertTrue m7.compareTo(m2) < 0
        assertTrue m7.compareTo(m3) < 0
        assertTrue m7.compareTo(m4) < 0
        assertTrue m7.compareTo(m5) < 0
        assertTrue m7.compareTo(m6) < 0
        assertTrue m7.compareTo(m8) < 0
        assertTrue m7.compareTo(m9) < 0
        assertTrue m7.compareTo(m10) > 0

        assertTrue m8.compareTo(m1) < 0
        assertTrue m8.compareTo(m2) < 0
        assertTrue m8.compareTo(m3) < 0
        assertTrue m8.compareTo(m4) < 0
        assertTrue m8.compareTo(m5) > 0
        assertTrue m8.compareTo(m6) < 0
        assertTrue m8.compareTo(m7) > 0
        assertTrue m8.compareTo(m9) > 0
        assertTrue m8.compareTo(m10) > 0

        assertTrue m9.compareTo(m1) < 0
        assertTrue m9.compareTo(m2) < 0
        assertTrue m9.compareTo(m3) < 0
        assertTrue m9.compareTo(m4) < 0
        assertTrue m9.compareTo(m5) < 0
        assertTrue m9.compareTo(m6) < 0
        assertTrue m9.compareTo(m7) > 0
        assertTrue m9.compareTo(m8) < 0
        assertTrue m9.compareTo(m10) > 0

        assertTrue m10.compareTo(m1) < 0
        assertTrue m10.compareTo(m2) < 0
        assertTrue m10.compareTo(m3) < 0
        assertTrue m10.compareTo(m4) < 0
        assertTrue m10.compareTo(m5) < 0
        assertTrue m10.compareTo(m6) < 0
        assertTrue m10.compareTo(m7) < 0
        assertTrue m10.compareTo(m8) < 0
        assertTrue m10.compareTo(m9) < 0

        def correctOrder = [m2, m3, m4, m1, m6, m8, m5, m9, m7, m10]

        // urls in completely reverse order
        def urls = [m10, m9, m8, m7, m6, m5, m4, m3, m2, m1]
        Collections.sort(urls)
        Collections.reverse(urls)
        assertEquals(correctOrder, urls)

        // urls in random order
        urls = [m3, m9, m5, m8, m4, m10, m7, m2, m1, m6]
        Collections.sort(urls)
        Collections.reverse(urls)
        assertEquals(correctOrder, urls)
    }

    @Test
    void testDollarSignParamsAllowed() {
        def parser = new DefaultUrlMappingParser()
        def application = new DefaultGrailsApplication()
        def defaultRegistry = new DefaultConstraintRegistry()
        def constraints = [new DefaultConstrainedProperty(UrlMapping.class, "controller", String.class, defaultRegistry), new DefaultConstrainedProperty(UrlMapping.class, "action", String.class, defaultRegistry), new DefaultConstrainedProperty(UrlMapping.class, "id", String.class, defaultRegistry), new DefaultConstrainedProperty(UrlMapping.class, "format", String.class, defaultRegistry)]
        def m1 = new RegexUrlMapping(parser.parse('/(*)/(*)?/(*)?(.(*))?'), null, null, null, null, null, null, UrlMapping.ANY_VERSION, constraints as ConstrainedProperty[], application)
        assert m1.createURL([id: 'AST$RING', action: "save", controller: "someController"], null) == '/someController/save/AST$RING'
    }
}
