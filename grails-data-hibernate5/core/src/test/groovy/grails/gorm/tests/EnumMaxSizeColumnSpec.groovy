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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/apache/grails-core/issues/14505')
class EnumMaxSizeColumnSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([EnumSizedThing, EnumListedThing, EnumCollectionThing, EnumMatrixThing, EnumMatrixCollectionThing, EnumShortThing])
    }

    void "maxSize constrains the column of an enum property like it does a string property"() {
        when:
        Map<String, Integer> lengths = [:]
        manager.sessionFactory.currentSession.doWork { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select column_name, character_maximum_length from information_schema.columns " +
                    "where lower(table_name) = 'enum_sized_thing'")
            while (rs.next()) {
                lengths[rs.getString(1).toLowerCase()] = rs.getInt(2)
            }
        }

        then:
        lengths['string_field'] == 100
        lengths['enum_field'] == 100
    }

    void "maxSize of a collection of enums limits how many values it holds, not the length of the column of each"() {
        given:
        new EnumCollectionThing(kinds: [EnumSizedKind.ONE]).save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        Map<String, Integer> lengths = [:]
        manager.sessionFactory.currentSession.doWork { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select column_name, character_maximum_length from information_schema.columns " +
                    "where lower(table_name) like '%enum_collection_thing%' and character_maximum_length is not null")
            while (rs.next()) {
                lengths[rs.getString(1).toLowerCase()] = rs.getInt(2)
            }
        }

        then: 'the name of the constant ONE is stored, in a column that is not one character wide'
        EnumCollectionThing.list().first().kinds == [EnumSizedKind.ONE] as Set
        lengths.values().every { it > 1 }
        !new EnumCollectionThing(kinds: [EnumSizedKind.ONE, EnumSizedKind.TWO]).validate()
    }

    void "an enum property with an inList of enum values stores and reads its value"() {
        given:
        new EnumListedThing(kind: EnumSizedKind.ONE).save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        EnumListedThing.findByKind(EnumSizedKind.ONE).kind == EnumSizedKind.ONE
        EnumListedThing.count() == 1
    }

    void "an enum inList that does not allow a value rejects it"() {
        expect:
        !new EnumListedThing(kind: EnumSizedKind.TWO).validate()
    }
    private Map<String, Integer> lengthsOf(String table) {
        Map<String, Integer> lengths = [:]
        manager.sessionFactory.currentSession.doWork { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select column_name, character_maximum_length from information_schema.columns " +
                    "where lower(table_name) = '${table}'")
            while (rs.next()) {
                lengths[rs.getString(1).toLowerCase()] = rs.getObject(2) as Integer
            }
        }
        lengths
    }

    @Unroll
    void "an enum property with #constraint has the column length that a string property with it has"() {
        when:
        Map<String, Integer> lengths = lengthsOf('enum_matrix_thing')

        then: 'the enum column and the string column are the same length'
        lengths["${prefix}_string"] == expected
        lengths["${prefix}_enum"] == expected

        where:
        constraint                                       | prefix  | expected
        'maxSize 40'                                     | 'max'   | 40
        'size 3..20'                                     | 'range' | 20
        'inList of ONE and THREE'                        | 'listed' | 5
        'inList of ONE and THREE and maxSize 40'         | 'both'  | 40
        'no constraint'                                  | 'plain' | 255
    }

    void "a maxSize shorter than the longest constant name does not make the column too short for a valid value"() {
        given: 'a value whose name is longer than the maxSize, which maxSize does not validate for an enum'
        new EnumShortThing(state: EnumMatrixKind.THREE, label: 'ab').save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        Map<String, Integer> lengths = lengthsOf('enum_short_thing')

        then: 'the enum column holds the longest name, THREE, and the string column keeps maxSize 2'
        lengths['state'] == 5
        lengths['label'] == 2
        EnumShortThing.list().first().state == EnumMatrixKind.THREE
    }

    void "an enum property stored as its ordinal has no character column to size"() {
        expect:
        lengthsOf('enum_matrix_thing')['ordinal_enum'] == null
    }

    @Unroll
    void "a collection of enums with #constraint keeps a column for the name of the constants"() {
        given:
        new EnumMatrixCollectionThing(kinds: [EnumMatrixKind.ONE], sizedKinds: [EnumMatrixKind.ONE], listedKinds: [EnumMatrixKind.ONE]).save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        Map<String, Integer> lengths = lengthsOf(table)

        then:
        lengths.values().any { it != null }
        lengths.values().findAll { it != null }.every { it > 1 }
        EnumMatrixCollectionThing.list().first()."$property" == [EnumMatrixKind.ONE] as Set

        where:
        constraint                | table                                  | property
        'maxSize 1'               | 'enum_matrix_collection_thing_kinds'   | 'kinds'
        'size 1..2'               | 'enum_matrix_collection_thing_sized_kinds' | 'sizedKinds'
        'inList of one value'     | 'enum_matrix_collection_thing_listed_kinds' | 'listedKinds'
    }
}

enum EnumSizedKind { ONE, TWO }

@Entity
class EnumSizedThing {
    String stringField
    EnumSizedKind enumField

    static constraints = {
        stringField nullable: true, maxSize: 100
        enumField nullable: true, maxSize: 100
    }
}

@Entity
class EnumListedThing {
    EnumSizedKind kind

    static constraints = {
        kind inList: [EnumSizedKind.ONE]
    }
}

@Entity
class EnumCollectionThing {
    Set<EnumSizedKind> kinds

    static hasMany = [kinds: EnumSizedKind]

    static constraints = {
        kinds maxSize: 1
    }
}

enum EnumMatrixKind { ONE, TWO, THREE }

@Entity
class EnumMatrixThing {
    String maxString
    EnumMatrixKind maxEnum
    String rangeString
    EnumMatrixKind rangeEnum
    String listedString
    EnumMatrixKind listedEnum
    String bothString
    EnumMatrixKind bothEnum
    String plainString
    EnumMatrixKind plainEnum
    EnumMatrixKind ordinalEnum

    static constraints = {
        maxString nullable: true, maxSize: 40
        maxEnum nullable: true, maxSize: 40
        rangeString nullable: true, size: 3..20
        rangeEnum nullable: true, size: 3..20
        listedString nullable: true, inList: ['ONE', 'THREE']
        listedEnum nullable: true, inList: [EnumMatrixKind.ONE, EnumMatrixKind.THREE]
        bothString nullable: true, inList: ['ONE', 'THREE'], maxSize: 40
        bothEnum nullable: true, inList: [EnumMatrixKind.ONE, EnumMatrixKind.THREE], maxSize: 40
        plainString nullable: true
        plainEnum nullable: true
        ordinalEnum nullable: true, maxSize: 40
    }

    static mapping = {
        ordinalEnum enumType: 'ordinal'
    }
}

@Entity
class EnumMatrixCollectionThing {
    Set<EnumMatrixKind> kinds
    Set<EnumMatrixKind> sizedKinds
    Set<EnumMatrixKind> listedKinds

    static hasMany = [kinds: EnumMatrixKind, sizedKinds: EnumMatrixKind, listedKinds: EnumMatrixKind]

    static constraints = {
        kinds maxSize: 1
        sizedKinds size: 1..2
        listedKinds inList: [[EnumMatrixKind.ONE] as Set]
    }
}

@Entity
class EnumShortThing {
    EnumMatrixKind state
    String label

    static constraints = {
        state maxSize: 2
        label maxSize: 2
    }
}
