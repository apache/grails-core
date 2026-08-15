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
package org.grails.datastore.gorm.jdbc

import org.springframework.beans.PropertyValue
import org.springframework.core.env.PropertySource
import spock.lang.Specification

class OriginCapablePropertyValueSpec extends Specification {

    private static PropertySource<String> namedSource(String name) {
        new PropertySource<String>(name, 'value') {
            @Override
            Object getProperty(String propertyName) { null }
        }
    }

    void "getName and getValue expose the values passed to the constructor"() {
        given:
        def origin = new PropertyOrigin(namedSource('mySource'), 'original.name')

        when:
        def propertyValue = new OriginCapablePropertyValue('bound.name', 'boundValue', origin)

        then:
        propertyValue.name == 'bound.name'
        propertyValue.value == 'boundValue'
    }

    void "toString includes the origin name and the source name"() {
        given:
        def origin = new PropertyOrigin(namedSource('myPropertySource'), 'original.name')
        def propertyValue = new OriginCapablePropertyValue('bound.name', 'boundValue', origin)

        expect:
        propertyValue.toString() == "'original.name' from 'myPropertySource'"
    }

    void "toString reports an unknown source when the origin has no source"() {
        given:
        def origin = new PropertyOrigin(null, 'original.name')
        def propertyValue = new OriginCapablePropertyValue('bound.name', 'boundValue', origin)

        expect:
        propertyValue.toString() == "'original.name' from 'unknown'"
    }

    void "static getOrigin returns the origin of an OriginCapablePropertyValue directly"() {
        given:
        def origin = new PropertyOrigin(namedSource('myPropertySource'), 'original.name')
        def propertyValue = new OriginCapablePropertyValue('bound.name', 'boundValue', origin)

        expect:
        OriginCapablePropertyValue.getOrigin(propertyValue).is(origin)
    }

    void "static getOrigin returns the propertyOrigin attribute for a plain PropertyValue"() {
        given:
        def origin = new PropertyOrigin(namedSource('myPropertySource'), 'original.name')
        def propertyValue = new PropertyValue('bound.name', 'boundValue')
        propertyValue.setAttribute('propertyOrigin', origin)

        expect:
        OriginCapablePropertyValue.getOrigin(propertyValue).is(origin)
    }

    void "static getOrigin returns null for a plain PropertyValue with no propertyOrigin attribute"() {
        given:
        def propertyValue = new PropertyValue('bound.name', 'boundValue')

        expect:
        OriginCapablePropertyValue.getOrigin(propertyValue) == null
    }
}
