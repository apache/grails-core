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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TagLibraryDynamicPropertyTests extends AbstractGrailsTagTests {

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass '''
import grails.gsp.*

@TagLib
class FooTagLib {
    Closure showAction = { attrs, body ->
        out << "action: ${actionName}"
    }
    Closure showController = { attrs, body ->
        out << "controller: ${controllerName}"
    }

    Closure showSession = { attrs, body ->
        out << "test: ${session.foo}"
    }
    Closure showParam = { attrs, body ->
        out << "test: ${params.foo}"
    }
}
'''
    }

    @Test
    void testDynamicProperties() {
        webRequest.actionName = "test"
        webRequest.controllerName = "foo"
        request.session.foo = "bar"
        webRequest.params.foo = "bar"
        
        def template = '<g:showAction />, <g:showController />, <g:showSession />, <g:showParam />'
        assertOutputEquals("action: test, controller: foo, test: bar, test: bar", template)
    }
}
