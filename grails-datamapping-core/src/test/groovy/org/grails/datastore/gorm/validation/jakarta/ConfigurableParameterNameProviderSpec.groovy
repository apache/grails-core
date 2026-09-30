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
package org.grails.datastore.gorm.validation.jakarta

import spock.lang.Specification

class ConfigurableParameterNameProviderSpec extends Specification {

    ConfigurableParameterNameProvider provider = new ConfigurableParameterNameProvider()

    void "returns registered parameter names for a method"() {
        given:
        def method = Sample.getMethod('greet', String, Integer)
        provider.addParameterNames('greet', [String, Integer] as Class[], ['name', 'times'])

        expect:
        provider.getParameterNames(method) == ['name', 'times']
    }

    void "returns default arg-prefixed names for an unregistered method"() {
        given:
        def method = Sample.getMethod('greet', String, Integer)

        expect:
        provider.getParameterNames(method) == ['arg0', 'arg1']
    }

    void "returns registered parameter names for a constructor"() {
        given:
        def constructor = Sample.getConstructor(String)
        provider.addParameterNames('<init>', [String] as Class[], ['name'])

        expect:
        provider.getParameterNames(constructor) == ['name']
    }

    void "returns default arg-prefixed names for an unregistered constructor"() {
        given:
        def constructor = Sample.getConstructor(String)

        expect:
        provider.getParameterNames(constructor) == ['arg0']
    }

    void "does not register names when any argument is null"() {
        when:
        provider.addParameterNames(null, [String] as Class[], ['name'])
        provider.addParameterNames('greet', null, ['name'])
        provider.addParameterNames('greet', [String, Integer] as Class[], null)

        then:
        provider.getParameterNames(Sample.getMethod('greet', String, Integer)) == ['arg0', 'arg1']
    }
}

class Sample {

    Sample(String name) {
    }

    void greet(String name, Integer times) {
    }
}
