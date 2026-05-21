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

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class ReturnValueTagLibTests extends Specification implements TagLibUnitTest<ReturnValueTagLib> {

    void testReturnValue() {
        expect:
        applyTemplate('${g.numberretval()}') == '123'
        applyTemplate('<g:numberretval />') == 'this output should be discarded in function call.123'
        applyTemplate('${(g.numberretval()==123 && g.numberretval() instanceof Integer)}<g:numberretval />') == 'truethis output should be discarded in function call.123'
        applyTemplate('${(numberretval()==123 && numberretval() instanceof Integer)}<g:numberretval />') == 'truethis output should be discarded in function call.123'
    }

    void testOutputNotUsed() {
        expect:
        applyTemplate('${g.outputnotused()}<g:outputnotused />') == ''
        applyTemplate('${outputnotused()}<g:outputnotused />') == ''
    }

    void testDiscardReturnValue() {
        expect:
        applyTemplate('${g.discardreturnvalue()}<g:discardreturnvalue />') == 'hellohello'
        applyTemplate('${discardreturnvalue()}<g:discardreturnvalue />') == 'hellohello'
    }
}

@Artefact('TagLib')
class ReturnValueTagLib {
    static returnObjectForTags = ['numberretval']

    Closure numberretval = { attrs ->
        // out shouldn't be used in returnObjectForTags tags, but we don't prevent it
        out << 'this output should be discarded in function call.'
        return 123
    }

    Closure outputnotused = { attrs -> return 'dontshowup' }

    Closure discardreturnvalue = { attrs ->
        out << 'hello'
        return 'dontshowup'
    }
}

