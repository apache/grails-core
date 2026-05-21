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
package org.grails.orm.hibernate.cfg

import spock.lang.Specification

class CacheConfigSpec extends Specification {

    // ── CacheConfig.Usage ─────────────────────────────────────────────────────

    def "Usage constants have expected string values"() {
        expect:
        CacheConfig.Usage.READ_ONLY.toString()             == 'read-only'
        CacheConfig.Usage.READ_WRITE.toString()            == 'read-write'
        CacheConfig.Usage.NONSTRICT_READ_WRITE.toString()  == 'nonstrict-read-write'
        CacheConfig.Usage.TRANSACTIONAL.toString()         == 'transactional'
    }

    def "Usage.values() returns all four constants"() {
        expect:
        CacheConfig.Usage.values().size() == 4
        CacheConfig.Usage.values().contains(CacheConfig.Usage.READ_ONLY)
        CacheConfig.Usage.values().contains(CacheConfig.Usage.TRANSACTIONAL)
    }

    def "Usage.of with Usage instance returns same instance"() {
        expect:
        CacheConfig.Usage.of(CacheConfig.Usage.READ_ONLY).is(CacheConfig.Usage.READ_ONLY)
    }

    def "Usage.of with string resolves case-insensitively to constant"() {
        expect:
        CacheConfig.Usage.of('READ-ONLY').is(CacheConfig.Usage.READ_ONLY)
        CacheConfig.Usage.of('read-write').is(CacheConfig.Usage.READ_WRITE)
        CacheConfig.Usage.of('TRANSACTIONAL').is(CacheConfig.Usage.TRANSACTIONAL)
    }

    def "Usage.of with unknown string creates new Usage with that value"() {
        when:
        def usage = CacheConfig.Usage.of('custom-usage')

        then:
        usage.toString() == 'custom-usage'
        !CacheConfig.Usage.values().contains(usage)
    }

    def "Usage.of with null or empty returns null"() {
        expect:
        CacheConfig.Usage.of(null)  == null
        CacheConfig.Usage.of('')    == null
    }

    def "Usage equals and hashCode work correctly"() {
        given:
        def a = new CacheConfig.Usage('read-only')
        def b = new CacheConfig.Usage('read-only')
        def c = new CacheConfig.Usage('read-write')

        expect:
        a == b
        a != c
        a.hashCode() == b.hashCode()
        a.hashCode() != c.hashCode()
        a != "not a Usage"
    }

    // ── CacheConfig.Include ───────────────────────────────────────────────────

    def "Include constants have expected string values"() {
        expect:
        CacheConfig.Include.ALL.toString()       == 'all'
        CacheConfig.Include.NON_LAZY.toString()  == 'non-lazy'
    }

    def "Include.values() returns both constants"() {
        expect:
        CacheConfig.Include.values().size() == 2
        CacheConfig.Include.values().contains(CacheConfig.Include.ALL)
        CacheConfig.Include.values().contains(CacheConfig.Include.NON_LAZY)
    }

    def "Include.of with Include instance returns same instance"() {
        expect:
        CacheConfig.Include.of(CacheConfig.Include.ALL).is(CacheConfig.Include.ALL)
    }

    def "Include.of with string resolves case-insensitively to constant"() {
        expect:
        CacheConfig.Include.of('ALL').is(CacheConfig.Include.ALL)
        CacheConfig.Include.of('non-lazy').is(CacheConfig.Include.NON_LAZY)
    }

    def "Include.of with unknown string creates new Include"() {
        when:
        def include = CacheConfig.Include.of('custom')

        then:
        include.toString() == 'custom'
    }

    def "Include.of with null or empty returns null"() {
        expect:
        CacheConfig.Include.of(null) == null
        CacheConfig.Include.of('')   == null
    }

    def "Include equals and hashCode work correctly"() {
        given:
        def a = new CacheConfig.Include('all')
        def b = new CacheConfig.Include('all')
        def c = new CacheConfig.Include('non-lazy')

        expect:
        a == b
        a != c
        a.hashCode() == b.hashCode()
        a != "not an Include"
    }

    // ── CacheConfig ───────────────────────────────────────────────────────────

    def "default CacheConfig has READ_WRITE usage, ALL include, and caching disabled"() {
        given:
        def config = new CacheConfig()

        expect:
        config.usage   == CacheConfig.Usage.READ_WRITE
        config.include == CacheConfig.Include.ALL
        !config.enabled
    }

    def "setUsage with string sets the usage"() {
        given:
        def config = new CacheConfig()

        when:
        config.setUsage('read-only')

        then:
        config.usage == CacheConfig.Usage.READ_ONLY
    }

    def "setUsage with unknown string is ignored when of() returns null"() {
        given:
        def config = new CacheConfig()
        config.setUsage('read-only')

        when:
        config.setUsage(null)

        then:
        config.usage == CacheConfig.Usage.READ_ONLY
    }

    def "setInclude with string sets the include"() {
        given:
        def config = new CacheConfig()

        when:
        config.setInclude('non-lazy')

        then:
        config.include == CacheConfig.Include.NON_LAZY
    }

    def "usage(Object) builder method returns this"() {
        given:
        def config = new CacheConfig()

        when:
        def result = config.usage('read-only')

        then:
        result.is(config)
        config.usage == CacheConfig.Usage.READ_ONLY
    }

    def "include(Object) builder method returns this"() {
        given:
        def config = new CacheConfig()

        when:
        def result = config.include('non-lazy')

        then:
        result.is(config)
        config.include == CacheConfig.Include.NON_LAZY
    }

    def "configureNew with Closure sets properties"() {
        when:
        def config = CacheConfig.configureNew {
            enabled true
            usage   'transactional'
            include 'non-lazy'
        }

        then:
        config.enabled
        config.usage   == CacheConfig.Usage.TRANSACTIONAL
        config.include == CacheConfig.Include.NON_LAZY
    }

    def "configureExisting with Map sets properties"() {
        given:
        def config = new CacheConfig()

        when:
        CacheConfig.configureExisting(config, [enabled: true, usage: 'read-only'])

        then:
        config.enabled
        config.usage == CacheConfig.Usage.READ_ONLY
    }

    def "configureExisting with Closure sets properties"() {
        given:
        def config = new CacheConfig()

        when:
        CacheConfig.configureExisting(config) {
            enabled true
        }

        then:
        config.enabled
    }
}
