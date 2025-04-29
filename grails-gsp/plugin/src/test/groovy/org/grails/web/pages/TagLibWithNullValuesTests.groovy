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

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TagLibWithNullValuesTests extends Specification implements TagLibUnitTest<NullValueTagLib> {

    void testNullValueHandling() {
        expect:
        applyTemplate('<p>This is tag1: <my:tag1 p1="abc"/></p>') == '<p>This is tag1: org.grails.taglib.encoder.OutputEncodingStack$OutputProxyWriter: [abc] []</p>'
        applyTemplate('<p>This is tag2: <my:tag2/></p>') == '<p>This is tag2: org.grails.taglib.encoder.OutputEncodingStack$OutputProxyWriter: [abc] []</p>'
    }
}

@Artefact('TagLib')
class NullValueTagLib {
    static namespace = 'my'

    Closure tag1 = { attrs ->
        out << out.getClass().name << ": [" << attrs.p1 << "] [" << attrs.p2 << "]"
    }

    Closure tag2 = { attrs ->
        out << my.tag1(p1: "abc")
    }
}
  
