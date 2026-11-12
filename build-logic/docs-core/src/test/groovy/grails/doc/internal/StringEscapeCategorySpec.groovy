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
package grails.doc.internal

import spock.lang.Specification

class StringEscapeCategorySpec extends Specification {

    void 'empty and reserved characters are encoded consistently'() {
        expect:
        StringEscapeCategory.encodeAsUrlPath('') == ''
        StringEscapeCategory.encodeAsUrlPath('a/b?c#d') == 'a/b%3Fc%23d'
        StringEscapeCategory.encodeAsUrlFragment('') == ''
        StringEscapeCategory.encodeAsUrlFragment('a/b?c#d') == 'a/b?c%23d'
        StringEscapeCategory.encodeAsHtml('') == ''
        StringEscapeCategory.encodeAsHtml('"quoted" & \'single\'') == '&quot;quoted&quot; &amp; \'single\''
        StringEscapeCategory.encodeAsHtml(null) == null
    }

    void 'the category is a static utility'() {
        expect:
        StringEscapeCategory.declaredConstructors.every { java.lang.reflect.Modifier.isPrivate(it.modifiers) }
    }

}
