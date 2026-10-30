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
package grails.encoders

import org.springframework.util.ClassUtils

import spock.lang.Specification

/**
 * Without grails-xml, the XML encoder escapes an object other than text as its string.
 */
class XMLEncoderSpec extends Specification {

    void 'without grails-xml, an object other than text is escaped as its string'() {
        given:
        assert !ClassUtils.isPresent('grails.converters.XML', getClass().classLoader)

        expect:
        new XMLEncoder().encode([title: '<Grails>']).toString() == '{title=&lt;Grails&gt;}'
    }
}
