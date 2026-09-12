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
package org.grails.plugins.testing

import spock.lang.Specification

/**
 * grails-xml is a compileOnly dependency of this module, so it is absent from this source set's
 * runtime classpath. That makes these tests the environment an application without the optional
 * module sees.
 */
class GrailsMockHttpServletRequestXmlSpec extends Specification {

    void 'a String body needs no XML converter'() {
        given:
        def request = new GrailsMockHttpServletRequest()

        when: "a String is set directly, which requires no object-to-XML conversion"
        request.setXml('<book><title>Grails</title></book>')

        then:
        request.contentAsString == '<book><title>Grails</title></book>'
        request.contentType == 'text/xml; charset=UTF-8'
    }

    void 'converting an object to XML without the module reports the missing dependency'() {
        given:
        def request = new GrailsMockHttpServletRequest()

        when: "an object needs converting, but grails-xml is not on the classpath"
        request.setXml([title: 'Grails'])

        then: "the cause is named rather than surfacing as NoClassDefFoundError"
        IllegalStateException e = thrown()
        e.message.contains('org.apache.grails:grails-xml')
        e.cause instanceof NoClassDefFoundError
    }

    void 'reading the body as XML without the module reports the missing dependency'() {
        given:
        def request = new GrailsMockHttpServletRequest()
        request.setXml('<book><title>Grails</title></book>')

        when:
        request.getXML()

        then:
        IllegalStateException e = thrown()
        e.message.contains('org.apache.grails:grails-xml')
    }
}
