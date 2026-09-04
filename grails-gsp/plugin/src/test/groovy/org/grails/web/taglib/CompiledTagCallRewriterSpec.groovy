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

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

/**
 * A call to a tag the build already knows about is compiled into a direct invocation instead of being
 * dispatched dynamically, and has to behave identically.
 *
 * <p>The rewrite is verified through what the tag library produces rather than by reading bytecode:
 * a rewritten call that produced different output would be the failure that matters.
 */
class CompiledTagCallRewriterSpec extends Specification implements TagLibUnitTest<RewrittenCallsTagLib> {

    void 'a namespaced call with attributes produces what the tag produces'() {
        expect:
        applyTemplate('<rewrite:viaNamespace/>') == applyTemplate('<g:link controller="book"/>')
    }

    void 'a namespaced call with a body passes the body through'() {
        expect:
        applyTemplate('<rewrite:withBody/>').contains('inside')
    }

    void 'a call to a tag the index does not hold still resolves'() {
        expect: 'left dynamic, so a tag library registered at runtime keeps working'
        applyTemplate('<rewrite:viaUnknownNamespace/>') == 'fallback'
    }

    void 'a call whose arguments are not a recognisable tag call is left alone'() {
        expect: 'the attributes are built at runtime, so the shape is not evident when compiling'
        applyTemplate('<rewrite:viaComputedAttributes/>') == applyTemplate('<g:link controller="book"/>')
    }
}
