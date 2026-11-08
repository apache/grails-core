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

    void "names a dependency after the version key its map key reduces to, dropping one dash segment at a time"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                [:],
                ['lib-family-extra-module': 'example:module:1.0'],
                ['lib-family.version': '1.0'])

        expect:
        calculator.calculate('example', 'module', '1.0', false).versionPropertyName == 'lib-family.version'
    }

    void "falls back to the longest version key the map key starts with"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                [:],
                ['libclient': 'example:libclient:1.0'],
                ['li.version': '1.0', 'lib.version': '1.0'])

        expect:
        calculator.calculate('example', 'libclient', '1.0', false).versionPropertyName == 'lib.version'
    }

    void "names an imported platform from the platform definitions"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                ['family-bom': 'example:family-bom:2.0'],
                [:],
                ['family.version': '2.0'])

        expect:
        calculator.calculate('example', 'family-bom', '2.0', true).versionPropertyName == 'family.version'
    }

    void "names a platform re-declared as a plain constraint with the same property as its import"() {
        given: "a derived BOM declaring an imported platform as an ordinary constraint"
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                ['family-bom': 'example:family-bom:2.0'],
                [:],
                ['family.version': '2.0'])

        expect:
        calculator.calculate('example', 'family-bom', '2.0', false).versionPropertyName == 'family.version'
    }

    void "a plain dependency definition wins over a platform definition with the same coordinates"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                ['family-bom': 'example:family-bom:2.0'],
                ['family-bom-constraint': 'example:family-bom:2.0'],
                ['family.version': '2.0', 'family-bom-constraint.version': '2.0'])

        expect:
        calculator.calculate('example', 'family-bom', '2.0', false).versionPropertyName == 'family-bom-constraint.version'
    }

    void "returns null for coordinates no definition declares"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator(
                ['family-bom': 'example:family-bom:2.0'],
                ['lib': 'example:lib:1.0'],
                ['family.version': '2.0', 'lib.version': '1.0'])

        expect:
        calculator.calculate('example', 'lib', '1.1', false) == null
        calculator.calculate('example', 'other', '1.0', true) == null
    }

    void "names a project artifact after its base name, with any forced group prefix"() {
        given:
        PropertyNameCalculator calculator = new PropertyNameCalculator([:], [:], [:])
        calculator.addForcedGroupPrefix('example.profiles', 'example-profile')
        calculator.addProject('example', 'example-core', '3.0', 'example-core')
        calculator.addProject('example.profiles', 'web', '3.0', 'web')

        expect:
        calculator.calculate('example', 'example-core', '3.0', false).versionPropertyName == 'example-core.version'
        calculator.calculate('example.profiles', 'web', '3.0', false).versionPropertyName == 'example-profile-web.version'
        calculator.versions['example-profile-web.version'] == '3.0'
    }
}
