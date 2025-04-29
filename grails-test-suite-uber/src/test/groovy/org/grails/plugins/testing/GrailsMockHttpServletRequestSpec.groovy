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

import grails.converters.XML

import grails.core.DefaultGrailsApplication
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer

import spock.lang.Issue
import spock.lang.Specification

class GrailsMockHttpServletRequestSpec extends Specification {
    @Issue("GRAILS-11493")
    def "should allow setting request.xml with XML instance"() {
        given:
            ConvertersConfigurationInitializer initializer=new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication())
            initializer.initialize()
            GrailsMockHttpServletRequest request=new GrailsMockHttpServletRequest()
        when:
            request.xml = new XML([a:1, b:2, c:3])
        then:
            request.inputStream.getText('UTF-8') == '<?xml version="1.0" encoding="UTF-8"?><map><entry key="a">1</entry><entry key="b">2</entry><entry key="c">3</entry></map>'
    }

    @Issue('GRAILS-11483')
    def 'test that the inputStream may not be read multiple times'() {
        given: 'a mock request which contains some content'
        def req = new GrailsMockHttpServletRequest(content: 'some content')

        when: 'the inputStream is read'
        def reader = new InputStreamReader(req.inputStream)
        def result = reader.readLine()

        then: 'the inputStream contents are returned'
        'some content' == result

        when: 'the inputStream is read again'
        reader = new InputStreamReader(req.inputStream)
        result = reader.readLine()

        then: 'the content is no longer available'
        null == result
    }
}
