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
package org.grails.web.pages

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class TagLibNamespaceTests extends Specification implements TagLibUnitTest<TestTagLib> {

    void testInvokeNamespacedTag() {
        expect:
        applyTemplate('<t1:foo />') == 'bar'
    }

    void testInvokeNestedNamespacedTag() {
        expect:
        applyTemplate('<t1:nested name="hello"><t1:foo /></t1:nested>') == "<hello>barbar</hello>"
    }

    void testDynamicDispatch() {
        expect:
        applyTemplate('<t1:condition><%println t1."${\'nested\'}"(name:\'hello\')%></t1:condition>') == ''
    }
}

@Artefact('TagLib')
class TestTagLib {
    static namespace = "t1"

    Closure condition = { attrs, body -> }

    Closure foo = { attrs, body ->
        out << "bar"
        out << body?.call()
    }

    Closure nested = { attrs, body ->
       out << "<${attrs.name}>"
        out << foo()
        out << body()
       out << "</${attrs.name}>"
    }
}
