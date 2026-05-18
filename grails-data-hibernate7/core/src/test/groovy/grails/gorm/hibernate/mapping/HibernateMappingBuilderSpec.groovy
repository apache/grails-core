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
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingBuilder
import org.hibernate.FetchMode
import spock.lang.Specification

/**
 * Covers branches of {@link HibernateMappingBuilder} not exercised by
 */
class HibernateMappingBuilderSpec extends Specification {

    private HibernateMappingBuilder builder(String name = 'Foo') {
        new HibernateMappingBuilder(new Mapping(), name)
    }

    private Mapping evaluate(@DelegatesTo(HibernateMappingBuilder) Closure cl) {
        builder().evaluate(cl)
    }

    // -------------------------------------------------------------------------
    // table / catalog / schema / comment
    // -------------------------------------------------------------------------

    def "table with name only"() {
        when:
        Mapping m = evaluate { table 'myTable' }

        then:
        m.tableName == 'myTable'
    }

    def "table with catalog and schema"() {
        when:
        Mapping m = evaluate { table name: 'table', catalog: 'CRM', schema: 'dbo' }

        then:
        m.table.name == 'table'
        m.table.schema == 'dbo'
        m.table.catalog == 'CRM'
    }

    def "table comment is stored"() {
        when:
        Mapping m = evaluate { comment 'wahoo' }

        then:
        m.comment == 'wahoo'
    }

    // -------------------------------------------------------------------------
    // version / autoTimestamp
    // -------------------------------------------------------------------------

    def "version column can be changed"() {
        when:
        Mapping m = evaluate { version 'v_number' }

        then:
        m.getPropertyConfig("version").column == 'v_number'
    }

    def "versioning can be disabled"() {
        when:
        Mapping m = evaluate { version false }

        then:
        !m.versioned
    }

    def "autoTimestamp can be disabled"() {
        when:
        Mapping m = evaluate { autoTimestamp false }

        then:
        !m.autoTimestamp
    }

    // -------------------------------------------------------------------------
    // discriminator
    // -------------------------------------------------------------------------

    def "discriminator value only"() {
        when:
        Mapping m = evaluate { discriminator 'one' }

        then:
        m.discriminator.value == 'one'
        m.discriminator.column == null
    }

    def "discriminator with column name"() {
        when:
        Mapping m = evaluate { discriminator value: 'one', column: 'type' }

        then:
        m.discriminator.value == 'one'
        m.discriminator.column.name == 'type'
    }

    def "discriminator with column map"() {
        when:
        Mapping m = evaluate { discriminator value: 'one', column: [name: 'type', sqlType: 'integer'] }

        then:
        m.discriminator.value == 'one'
        m.discriminator.column.name == 'type'
        m.discriminator.column.sqlType == 'integer'
    }

    def "discriminator with formula and other settings"() {
        when:
        Mapping m = evaluate {
            discriminator value: '1', formula: "case when CLASS_TYPE in ('a', 'b', 'c') then 0 else 1 end", type: 'integer', insert: false
        }

        then:
        m.discriminator.value == '1'
        m.discriminator.formula == "case when CLASS_TYPE in ('a', 'b', 'c') then 0 else 1 end"
        m.discriminator.type == 'integer'
        !m.discriminator.insertable
    }

    // -------------------------------------------------------------------------
    // inheritance
    // -------------------------------------------------------------------------

    def "tablePerHierarchy false disables it"() {
        when:
        Mapping m = evaluate { tablePerHierarchy false }

        then:
        !m.tablePerHierarchy
    }

    def "tablePerSubclass true disables tablePerHierarchy"() {
        when:
        Mapping m = evaluate { tablePerSubclass true }

        then:
        !m.tablePerHierarchy
    }

    def "tablePerConcreteClass true enables it and disables tablePerHierarchy"() {
        when:
        Mapping m = evaluate { tablePerConcreteClass true }

        then:
        m.tablePerConcreteClass
        !m.tablePerHierarchy
    }

    // -------------------------------------------------------------------------
    // cache settings
    // -------------------------------------------------------------------------

    def "default cache strategy"() {
        when:
        Mapping m = evaluate { cache true }

        then:
        m.cache.usage.toString() == 'read-write'
        m.cache.include.toString() == 'all'
    }

    def "custom cache strategy"() {
        when:
        Mapping m = evaluate { cache usage: 'read-only', include: 'non-lazy' }

        then:
        m.cache.usage.toString() == 'read-only'
        m.cache.include.toString() == 'non-lazy'
    }

    def "custom cache strategy with usage string only"() {
        when:
        Mapping m = evaluate { cache 'read-only' }

        then:
        m.cache.usage.toString() == 'read-only'
        m.cache.include.toString() == 'all'
    }

    def "invalid cache values are ignored and defaults used"() {
        when:
        Mapping m = evaluate { cache usage: 'rubbish', include: 'more-rubbish' }

        then:
        m.cache.usage.toString() == 'read-write'
        m.cache.include.toString() == 'all'
    }

    // -------------------------------------------------------------------------
    // identity / id
    // -------------------------------------------------------------------------

    def "identity column mapping"() {
        when:
        Mapping m = evaluate { id column: 'foo_id', type: Integer }

        then:
        m.identity.type == Long // Default remains Long? No, wait.
        // In HibernateMappingBuilderTests:
        // assertEquals Long, mapping.identity.type
        // assertEquals 'foo_id', mapping.getPropertyConfig("id").column
        // assertEquals Integer, mapping.getPropertyConfig("id").type
        m.getPropertyConfig("id").column == 'foo_id'
        m.getPropertyConfig("id").type == Integer
        m.identity.generator == 'native'
    }

    def "default id strategy"() {
        when:
        Mapping m = evaluate { }

        then:
        m.identity.type == Long
        m.identity.column == 'id'
        m.identity.generator == 'native'
    }

    def "hilo id strategy"() {
        when:
        Mapping m = evaluate { id generator: 'hilo', params: [table: 'hi_value', column: 'next_value', max_lo: 100] }

        then:
        m.identity.column == 'id'
        m.identity.generator == 'hilo'
        m.identity.params.table == 'hi_value'
    }

    def "composite id strategy"() {
        when:
        Mapping m = evaluate { id composite: ['one', 'two'], compositeClass: HibernateMappingBuilder }

        then:
        m.identity instanceof HibernateCompositeIdentity
        m.identity.propertyNames == ['one', 'two']
        m.identity.compositeClass == HibernateMappingBuilder
    }

    def "natural id mapping"() {
        expect:
        evaluate { id natural: 'one' }.identity.natural.propertyNames == ['one']
        evaluate { id natural: ['one', 'two'] }.identity.natural.propertyNames == ['one', 'two']
        evaluate { id natural: [properties: ['one', 'two'], mutable: true] }.identity.natural.mutable
    }

    // -------------------------------------------------------------------------
    // other root settings
    // -------------------------------------------------------------------------

    def "autoImport defaults to true and can be disabled"() {
        expect:
        evaluate { }.autoImport
        !evaluate { autoImport false }.autoImport
    }

    def "dynamicUpdate and dynamicInsert"() {
        when:
        Mapping m = evaluate {
            dynamicUpdate true
            dynamicInsert true
        }

        then:
        m.dynamicUpdate
        m.dynamicInsert

        when:
        m = evaluate { }

        then:
        !m.dynamicUpdate
        !m.dynamicInsert
    }

    def "batchSize config"() {
        when:
        Mapping m = evaluate {
            batchSize 10
            things batchSize: 15
        }

        then:
        m.batchSize == 10
        m.getPropertyConfig('things').batchSize == 15
    }

    def "class sort order"() {
        when:
        Mapping m = evaluate {
            sort "name"
            order "desc"
        }

        then:
        m.sort.name == "name"
        m.sort.direction == "desc"
    }

    def "class sort order via map"() {
        when:
        Mapping m = evaluate {
            sort name: 'desc'
        }

        then:
        m.sort.namesAndDirections == [name: 'desc']
    }

    def "property ignoreNotFound is stored"() {
        expect:
        evaluate { foos ignoreNotFound: true }.getPropertyConfig("foos").ignoreNotFound
        !evaluate { foos ignoreNotFound: false }.getPropertyConfig("foos").ignoreNotFound
    }

    def "property association sort order"() {
        when:
        Mapping m = evaluate {
            columns {
                things sort: 'name'
            }
        }

        then:
        m.getPropertyConfig('things').sort == 'name'
    }

    def "property lazy settings"() {
        expect:
        evaluate { things column: 'foo' }.getPropertyConfig('things').getLazy() == null
        !evaluate { things lazy: false }.getPropertyConfig('things').lazy
    }

    def "property cascades"() {
        expect:
        evaluate { things cascade: 'persist,merge' }.getPropertyConfig('things').cascade == 'persist,merge'
        evaluate { columns { things cascade: 'all' } }.getPropertyConfig('things').cascade == 'all'
    }

    def "property fetch modes"() {
        expect:
        evaluate { things fetch: 'join' }.getPropertyConfig('things').fetchMode == FetchMode.JOIN
        evaluate { things fetch: 'select' }.getPropertyConfig('things').fetchMode == FetchMode.SELECT
        evaluate { things column: 'foo' }.getPropertyConfig('things').fetchMode == FetchMode.DEFAULT
    }

    def "property enumType"() {
        expect:
        evaluate { things column: 'foo' }.getPropertyConfig('things').enumType == 'default'
        evaluate { things enumType: 'ordinal' }.getPropertyConfig('things').enumType == 'ordinal'
    }

    def "property joinTable mapping"() {
        when:
        Mapping m1 = evaluate { things joinTable: true }
        Mapping m2 = evaluate { things joinTable: 'foo' }
        Mapping m3 = evaluate { things joinTable: [name: 'foo', key: 'foo_id', column: 'bar_id'] }

        then:
        m1.getPropertyConfig('things').joinTable != null
        m2.getPropertyConfig('things').joinTable.name == 'foo'
        m3.getPropertyConfig('things').joinTable.name == 'foo'
        m3.getPropertyConfig('things').joinTable.keys[0].name == 'foo_id'
        m3.getPropertyConfig('things').joinTable.column.name == 'bar_id'
    }

    def "property custom association caching"() {
        when:
        Mapping m1 = evaluate { firstName cache: [usage: 'read-only', include: 'non-lazy'] }
        Mapping m2 = evaluate { firstName cache: 'read-only' }
        Mapping m3 = evaluate { firstName cache: true }

        then:
        m1.getPropertyConfig('firstName').cache.usage.toString() == 'read-only'
        m1.getPropertyConfig('firstName').cache.include.toString() == 'non-lazy'
        m2.getPropertyConfig('firstName').cache.usage.toString() == 'read-only'
        m3.getPropertyConfig('firstName').cache.usage.toString() == 'read-write'
        m3.getPropertyConfig('firstName').cache.include.toString() == 'all'
    }

    def "simple column mappings"() {
        when:
        Mapping m = evaluate {
            firstName column: 'First_Name'
            lastName column: 'Last_Name'
        }

        then:
        m.getPropertyConfig('firstName').column == 'First_Name'
        m.getPropertyConfig('lastName').column == 'Last_Name'
    }

    def "complex column mappings"() {
        when:
        Mapping m = evaluate {
            firstName column: 'First_Name',
                    lazy: true,
                    unique: true,
                    type: java.sql.Clob,
                    length: 255,
                    index: 'foo',
                    sqlType: 'text'
        }

        then:
        m.columns.firstName.column == 'First_Name'
        m.columns.firstName.lazy
        m.columns.firstName.isUnique()
        m.columns.firstName.type == java.sql.Clob
        m.columns.firstName.length == 255
        m.columns.firstName.getIndexName() == 'foo'
        m.columns.firstName.sqlType == 'text'
    }

    def "property with multiple columns"() {
        when:
        Mapping m = evaluate {
            amount type: MyUserType, {
                column name: "value"
                column name: "currency", sqlType: "char", length: 3
            }
        }

        then:
        m.columns.amount.columns.size() == 2
        m.columns.amount.columns[0].name == "value"
        m.columns.amount.columns[1].name == "currency"
        m.columns.amount.columns[1].sqlType == "char"
        m.columns.amount.columns[1].length == 3
    }

    def "disallowed multi-column property access"() {
        given:
        def b = builder()
        b.evaluate {
            amount type: MyUserType, {
                column name: "value"
                column name: "currency"
            }
        }

        when:
        b.evaluate { amount scale: 2 }

        then:
        thrown(Throwable)
    }

    def "property with user type and params"() {
        when:
        Mapping m = evaluate {
            amount type: MyUserType, params: [param1: "amountParam1", param2: 65]
        }

        then:
        m.getPropertyConfig('amount').type == MyUserType
        m.getPropertyConfig('amount').typeParams.param1 == "amountParam1"
        m.getPropertyConfig('amount').typeParams.param2 == 65
    }

    def "property insertable and updatable"() {
        when:
        Mapping m = evaluate {
            firstName insertable: true, updatable: true
            lastName insertable: false, updatable: false
        }

        then:
        m.getPropertyConfig('firstName').insertable
        m.getPropertyConfig('firstName').updatable
        !m.getPropertyConfig('lastName').insertable
        !m.getPropertyConfig('lastName').updatable
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

    void "handlePropertyInternal handles shared constraints"() {
        given:
        def m = new Mapping()
        m.columns['common'] = new PropertyConfig(batchSize: 100)
        def builder = new HibernateMappingBuilder(m, "Foo", { common shared: true })

        when:
        builder.evaluate {
            myProp shared: 'common', ignoreNotFound: true
        }

        then:
        m.columns['myProp'].batchSize == 100
        m.columns['myProp'].ignoreNotFound == true
    }

    void "handlePropertyInternal handles updateable deprecated property"() {
        when:
        Mapping m = evaluate { myProp updateable: false }

        then:
        !m.getPropertyConfig('myProp').updatable
    }

    void "id(Map) handles params conversion"() {
        when:
        Mapping m = evaluate { id generator: 'seq', params: [a: 1, b: '2'] }

        then:
        m.identity.params == [a: '1', b: '2']
    }

    static class MyUserType {}
}
