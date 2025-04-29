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
package org.grails.web.taglib

import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class PageScopeSpec extends Specification implements GrailsWebUnitTest {

    void 'test referring to non existent page scope property does not throw MissingPropertyException'() {
        expect:
        applyTemplate("<%= pageScope.nonExistent ?: 'No Property Found' %>") == 'No Property Found'
    }
    
    void 'test page scope'() {

        expect:
        applyTemplate ('''\
<g:set var="one" scope="request" value="two" />\
<g:set var="two" scope="page" value="three" />\
<g:set var="three" scope="session" value="four" />\
one: ${request.one} two: ${two} three: ${session.three}\
''') == 'one: two two: three three: four'
    }
}
