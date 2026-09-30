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
package org.apache.grails.gradle.tasks.bom

import spock.lang.Specification

class PropertyNameCalculatorSpec extends Specification {

    void "geb-playwright shares the geb-spock version property"() {
        given:
        PropertyNameCalculator calculator = calculator(
            'geb-spock': 'org.apache.groovy.geb:geb-spock:8.0.2-SNAPSHOT',
            'geb-spock-playwright': 'org.apache.groovy.geb:geb-playwright:8.0.2-SNAPSHOT'
        )

        expect:
        calculator.calculate('org.apache.groovy.geb', 'geb-playwright', '8.0.2-SNAPSHOT', false)
            .versionPropertyName == 'geb-spock.version'
    }

    void "a key that does not prefix a known version property fails"() {
        given:
        PropertyNameCalculator calculator = calculator(
            'geb-playwright': 'org.apache.groovy.geb:geb-playwright:8.0.2-SNAPSHOT'
        )

        when:
        calculator.calculate('org.apache.groovy.geb', 'geb-playwright', '8.0.2-SNAPSHOT', false)

        then:
        Exception failure = thrown(Exception)
        failure.message.contains('Could not determine artifact property key')
    }

    private static PropertyNameCalculator calculator(Map<String, String> dependencies) {
        new PropertyNameCalculator([:], dependencies, ['geb-spock.version': '8.0.2-SNAPSHOT'])
    }
}
