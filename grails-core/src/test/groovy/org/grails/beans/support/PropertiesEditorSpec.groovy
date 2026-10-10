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
package org.grails.beans.support

import spock.lang.Specification

class PropertiesEditorSpec extends Specification {

    void 'setValue with a Map coerces every key and value to a String'() {
        given:
        def editor = new PropertiesEditor()

        when:
        editor.setValue([one: 1, two: 2L, (3): 'three'])
        Properties result = (Properties) editor.value

        then:
        result.getProperty('one') == '1'
        result.getProperty('two') == '2'
        result.getProperty('3') == 'three'
    }

    void 'setValue with a Map containing a null value throws, since Properties rejects null values'() {
        given:
        def editor = new PropertiesEditor()

        when:
        editor.setValue([key: null])

        then:
        thrown(NullPointerException)
    }

    void 'setValue with an actual Properties instance is passed through unchanged'() {
        given:
        def editor = new PropertiesEditor()
        Properties props = new Properties()
        props.setProperty('a', 'b')

        when:
        editor.setValue(props)

        then:
        editor.value.is(props)
    }

    void 'setValue with a non-Map, non-Properties value delegates to the superclass unparsed'() {
        // setValue is a raw passthrough (unlike setAsText, which parses Properties-format
        // text) - a plain String value that is neither a Map nor Properties is stored as-is.
        given:
        def editor = new PropertiesEditor()

        when:
        editor.setValue('a=1\nb=2')

        then:
        editor.value == 'a=1\nb=2'
    }

    void 'setAsText parses Properties-format text into a Properties instance'() {
        given:
        def editor = new PropertiesEditor()

        when:
        editor.setAsText('a=1\nb=2')

        then:
        ((Properties) editor.value).getProperty('a') == '1'
        ((Properties) editor.value).getProperty('b') == '2'
    }
}
