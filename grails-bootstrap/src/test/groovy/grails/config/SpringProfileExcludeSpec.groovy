/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.config

import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import org.grails.config.NavigableMap

@RestoreSystemProperties
class isExcludedBySpringProfileSpec extends Specification {

    def 'on-profile present in config source but no active profile is set => exclude'() {
        given:
            def src = nestedOnProfile('dev')
            def path = 'whatever'

        expect:
            NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    def 'on-profile matches active profile => include'() {
        given:
            System.setProperty('spring.profiles.active', 'dev')

        and:
            def src = nestedOnProfile('dev')
            def path = 'does.not.matter'

        expect:
            !NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    def 'profiles matches active => include'() {
        given:
            System.setProperty('spring.profiles.active', 'prod')

        and:
            def src = nestedProfiles('prod')
            def path = 'does.not.matter'

        expect:
            !NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    def 'profiles present in config but no active profile set => exclude'() {
        given:
            def src = nestedProfiles('prod')
            def path = 'does.not.matter'

        expect:
            NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    def 'empty string active profile is treated as null'() {
        given:
            System.setProperty('spring.profiles.active', '') // should behave like no active profile

        and:
            def src = nestedOnProfile('dev')
            def path = 'does.not.matter'

        expect:
            NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    @Unroll
    def 'on-profile lookup via #desc is honored (active=#active -> exclude=#excludeExpected)'() {
        given:
            if (active) System.setProperty('spring.profiles.active', active)
            else System.clearProperty('spring.profiles.active')

        and:
            def src = srcBuilder('qa')

        expect:
            NavigableMap.isSourceMapExcludedBySpringProfile(src, path) == excludeExpected

        where:
            desc                       | srcBuilder              | path                     | active | excludeExpected
            'nested map'               | this.&nestedOnProfile   | 'any'                    | null   | true   // on-profile present, no active => exclude
            'nested map (match)'       | this.&nestedOnProfile   | 'any'                    | 'qa'   | false  // matches => include
            'relative via path match'  | this.&relativeOnProfile | 'spring.config.activate' | 'qa'   | false
            'relative via path no act' | this.&relativeOnProfile | 'spring.config.activate' | null   | true
            'dotted key'               | this.&dottedOnProfile   | 'any'                    | 'qa'   | false
            'dotted key no active'     | this.&dottedOnProfile   | 'any'                    | null   | true
            'non-match'                | this.&nestedOnProfile   | 'any'                    | 'dev'  | true   // profile set but different from active => exclude
    }

    @Unroll
    def 'legacy spring.profiles lookup via #desc is honored (active=#active -> exclude=#excludeExpected)'() {
        given:
            if (active) System.setProperty('spring.profiles.active', active)
            else System.clearProperty('spring.profiles.active')

        and:
            def src = srcBuilder('prod')

        expect:
            NavigableMap.isSourceMapExcludedBySpringProfile(src, path) == excludeExpected

        where:
            desc                       | srcBuilder             | path     | active | excludeExpected
            'nested map'               | this.&nestedProfiles   | 'any'    | 'prod' | false  // matches => include
            'nested map no active'     | this.&nestedProfiles   | 'any'    | null   | true   // present but no active => exclude
            'relative via path match'  | this.&relativeProfiles | 'spring' | 'prod' | false
            'relative via path no act' | this.&relativeProfiles | 'spring' | null   | true
            'dotted key (match)'       | this.&dottedProfiles   | 'any'    | 'prod' | false
            'non-match'                | this.&nestedProfiles   | 'any'    | 'qa'   | true   // different => exclude
    }

    def 'active profile present but no profile keys in config => include'() {
        given:
            System.setProperty('spring.profiles.active', 'anything')

        and:
            def src = [:]
            def path = 'any'

        expect:
            !NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    def 'no active and no profile keys => include'() {
        given:
            def src = [:]
            def path = 'any'

        expect:
            !NavigableMap.isSourceMapExcludedBySpringProfile(src, path)
    }

    private static Map nestedOnProfile(String value) {
        ['spring': ['config': ['activate': ['on-profile': value]]]]
    }

    private static Map relativeOnProfile(String value) {
        ['on-profile': value]
    }

    private static Map dottedOnProfile(String value) {
        ['spring.config.activate.on-profile': value]
    }

    private static Map nestedProfiles(String value) {
        ['spring': ['profiles': value]]
    }

    private static Map relativeProfiles(String value) {
        ['profiles': value]
    }

    private static Map dottedProfiles(String value) {
        ['spring.profiles': value]
    }
}
