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
package grails.gorm.hibernate.mapping

import jakarta.persistence.AccessType
import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingBuilder
import org.hibernate.FetchMode
import spock.lang.Specification

/**
 * Covers branches of {@link HibernateMappingBuilder} not exercised by
 * {@link HibernateMappingBuilderTests}.
 */
class HibernateMappingBuilderSpec extends Specification {

    private HibernateMappingBuilder builder(String name = 'Foo') {
        new HibernateMappingBuilder(new Mapping(), name)
    }

    private Mapping evaluate(@DelegatesTo(HibernateMappingBuilder) Closure cl) {
        builder().evaluate(cl)
    }

    // -------------------------------------------------------------------------
    // autowire / tenantId
    // -------------------------------------------------------------------------

    def "autowire stores the value on the mapping"() {
        expect:
        evaluate { autowire true }.autowire
        !evaluate { autowire false }.autowire
    }

    def "tenantId stores the property name"() {
        expect:
        evaluate { tenantId 'tenantId' }.getPropertyConfig('tenantId') != null
    }

    // -------------------------------------------------------------------------
    // cache(String, Map)
    // -------------------------------------------------------------------------

    def "cache(String, Map) sets usage and include"() {
        when:
        Mapping m = evaluate { cache 'read-write', [include: 'all'] }

        then:
        m.cache.usage.toString() == 'read-write'
        m.cache.include.toString() == 'all'
    }

    def "cache(String) with invalid usage still creates a CacheConfig with the default usage"() {
        when:
        Mapping m = evaluate { cache 'INVALID_USAGE' }

        then:
        m.cache != null
        m.cache.usage.toString() == 'read-write'  // default preserved; INVALID_USAGE rejected
    }

    def "cache(Map) with invalid include still creates a CacheConfig with the default include"() {
        when:
        Mapping m = evaluate { cache usage: 'read-only', include: 'INVALID_INCLUDE' }

        then:
        m.cache != null
        m.cache.usage.toString() == 'read-only'
        m.cache.include.toString() == 'all'  // default preserved; INVALID_INCLUDE rejected
    }

    // -------------------------------------------------------------------------
    // hibernateCustomUserType
    // -------------------------------------------------------------------------

    def "hibernateCustomUserType registers a user type when args are valid"() {
        when:
        Mapping m = evaluate { 'user-type'(type: 'myType', 'class': String) }

        then:
        m.userTypes[String] == 'myType'
    }

    def "hibernateCustomUserType is a no-op when class is not a Class"() {
        when:
        Mapping m = evaluate { 'user-type'(type: 'myType', 'class': 'notAClass') }

        then:
        m.userTypes.isEmpty()
    }

    def "hibernateCustomUserType is a no-op when type is absent"() {
        when:
        Mapping m = evaluate { 'user-type'('class': String) }

        then:
        m.userTypes.isEmpty()
    }

    // -------------------------------------------------------------------------
    // includes() null-safety
    // -------------------------------------------------------------------------

    def "includes() with null closure does not throw"() {
        when:
        evaluate { includes(null) }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // sort / order null guards
    // -------------------------------------------------------------------------

    def "sort(null) is a no-op"() {
        when:
        Mapping m = evaluate { sort((String) null) }

        then:
        m.sort.name == null
    }

    def "order with invalid direction is a no-op"() {
        when:
        Mapping m = evaluate { order 'invalid' }

        then:
        m.sort.direction == null
    }

    def "batchSize(null) is a no-op and leaves batchSize as null"() {
        when:
        Mapping m = evaluate { batchSize null }

        then:
        m.batchSize == null
    }

    // -------------------------------------------------------------------------
    // evaluate with context argument
    // -------------------------------------------------------------------------

    def "evaluate passes context to the closure"() {
        given:
        def b = builder()
        Object captured = null

        when:
        b.evaluate({ Object ctx -> captured = ctx }, 'myContext')

        then:
        captured == 'myContext'
    }

    // -------------------------------------------------------------------------
    // property(Map, String) — the 2-arg typed method
    // -------------------------------------------------------------------------

    def "property(Map, String) registers the property config"() {
        when:
        Mapping m = evaluate { property([nullable: true, column: 'my_col'], 'myProp') }

        then:
        m.getPropertyConfig('myProp') != null
        m.getPropertyConfig('myProp').nullable
        m.getPropertyConfig('myProp').column == 'my_col'
    }

    // -------------------------------------------------------------------------
    // handlePropertyInternal — uncovered branches
    // -------------------------------------------------------------------------

    def "property with accessType stores it"() {
        when:
        Mapping m = evaluate { myProp accessType: AccessType.FIELD }

        then:
        m.getPropertyConfig('myProp').accessType == AccessType.FIELD
    }

    def "property updatable is honoured"() {
        when:
        Mapping m = evaluate { myProp updatable: false }

        then:
        !m.getPropertyConfig('myProp').updatable
    }

    def "property params map is converted to Properties"() {
        when:
        Mapping m = evaluate { myProp params: [scale: '4', precision: '10'] }

        then:
        m.getPropertyConfig('myProp').typeParams instanceof Properties
        m.getPropertyConfig('myProp').typeParams['scale'] == '4'
    }

    def "property unique as String creates a named unique constraint"() {
        when:
        Mapping m = evaluate { myProp unique: 'myGroup' }

        then:
        m.getPropertyConfig('myProp').isUniqueWithinGroup()
    }

    def "property unique as List creates a composite unique constraint"() {
        when:
        Mapping m = evaluate { myProp unique: ['a', 'b'] }

        then:
        m.getPropertyConfig('myProp').isUniqueWithinGroup()
    }

    def "property size as IntRange stores minSize and maxSize"() {
        when:
        Mapping m = evaluate { myProp size: (1..10) }

        then:
        m.getPropertyConfig('myProp').minSize == 1
        m.getPropertyConfig('myProp').maxSize == 10
    }

    def "property range as ObjectRange stores min and max"() {
        when:
        // ObjectRange is used for non-primitive ranges; 'a'..'e' produces one
        Mapping m = evaluate { myProp range: ('a'..'e') }

        then:
        m.getPropertyConfig('myProp').min == 'a'
        m.getPropertyConfig('myProp').max == 'e'
    }

    def "property inList stores the list"() {
        when:
        Mapping m = evaluate { myProp inList: ['A', 'B', 'C'] }

        then:
        m.getPropertyConfig('myProp').inList == ['A', 'B', 'C']
    }

    def "property fetch with join string sets JOIN fetch mode"() {
        when:
        Mapping m = evaluate { myProp fetch: 'join' }

        then:
        m.getPropertyConfig('myProp').getFetchMode() == FetchMode.JOIN
    }

    def "property fetch with unknown string falls back to SELECT"() {
        when:
        Mapping m = evaluate { myProp fetch: 'eager' }

        then:
        m.getPropertyConfig('myProp').getFetchMode() == FetchMode.SELECT
    }

    def "property with sub-closure delegates to PropertyDefinitionDelegate"() {
        when:
        Mapping m = evaluate {
            myProp {
                column name: 'col_one'
            }
        }

        then:
        m.getPropertyConfig('myProp').columns[0].name == 'col_one'
    }

    def "property indexColumn map is applied"() {
        when:
        Mapping m = evaluate {
            myProp indexColumn: [name: 'idx', type: 'integer', length: 10]
        }

        then:
        PropertyConfig ic = m.getPropertyConfig('myProp').indexColumn
        ic != null
        ic.columns[0].name == 'idx'
        ic.columns[0].length == 10
    }

    def "property cache as boolean true enables caching"() {
        when:
        Mapping m = evaluate { myProp cache: true }

        then:
        m.getPropertyConfig('myProp').cache instanceof CacheConfig
    }

    def "property cache as boolean false is a no-op"() {
        when:
        Mapping m = evaluate { myProp cache: false }

        then:
        m.getPropertyConfig('myProp').cache == null
    }

    def "property cache as Map sets usage and include"() {
        when:
        Mapping m = evaluate { myProp cache: [usage: 'read-only', include: 'all'] }

        then:
        m.getPropertyConfig('myProp').cache.usage.toString() == 'read-only'
        m.getPropertyConfig('myProp').cache.include.toString() == 'all'
    }

    def "property column sqlType is set"() {
        when:
        Mapping m = evaluate { myProp sqlType: 'text' }

        then:
        m.getPropertyConfig('myProp').sqlType == 'text'
    }

    def "property column read/write formulas are set"() {
        when:
        Mapping m = evaluate { myProp read: 'lower(col)', write: 'upper(?)' }

        then:
        m.getPropertyConfig('myProp').columns[0].read == 'lower(col)'
        m.getPropertyConfig('myProp').columns[0].write == 'upper(?)'
    }

    def "property column defaultValue and comment are set"() {
        when:
        Mapping m = evaluate { myProp defaultValue: 'N/A', comment: 'a test column' }

        then:
        m.getPropertyConfig('myProp').columns[0].defaultValue == 'N/A'
        m.getPropertyConfig('myProp').columns[0].comment == 'a test column'
    }

    // -------------------------------------------------------------------------
    // methodMissing — filtering branches
    // -------------------------------------------------------------------------

    def "methodMissing skips properties in methodMissingExcludes via importFrom"() {
        given: "a class whose constraints closure maps 'foos' and 'bars'"
        def cl = new GroovyClassLoader().parseClass('''
            class ImportSource {
                static constraints = {
                    foos(lazy: false)
                    bars(lazy: true)
                }
            }
        ''')

        when: "importFrom with exclude:[bars]"
        Mapping m = evaluate { importFrom(cl, [exclude: ['bars']]) }

        then: "foos is mapped, bars is not"
        m.getPropertyConfig('foos') != null
        m.getPropertyConfig('bars') == null
    }

    def "methodMissing skips properties not in methodMissingIncludes via importFrom"() {
        given:
        def cl = new GroovyClassLoader().parseClass('''
            class ImportSource2 {
                static constraints = {
                    foos(lazy: false)
                    bars(lazy: true)
                }
            }
        ''')

        when: "importFrom with include:[bars]"
        Mapping m = evaluate { importFrom(cl, [include: ['bars']]) }

        then: "bars is mapped, foos is not"
        m.getPropertyConfig('bars') != null
        m.getPropertyConfig('foos') == null
    }

    def "methodMissing with no matching args signature is silently ignored"() {
        when: "call with a plain String arg (no Map, no Closure)"
        Mapping m = evaluate { myProp 'justAString' }

        then:
        noExceptionThrown()
        m.getPropertyConfig('myProp') == null
    }
}
