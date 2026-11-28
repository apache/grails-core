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
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14505')
class EnumMaxSizeColumnSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EnumSizedThing, EnumListedThing, EnumCollectionThing)
    }

    void "an enum property with maxSize still stores and reads its value"() {
        given:
        new EnumSizedThing(stringField: 'x', enumField: EnumSizedKind.TWO).save(flush: true, failOnError: true)
        session.clear()

        expect:
        EnumSizedThing.findByEnumField(EnumSizedKind.TWO).enumField == EnumSizedKind.TWO
        EnumSizedThing.count() == 1
    }

    void "the string property keeps the length from maxSize"() {
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
    }

    void "an enum property with an inList of enum values stores and reads its value"() {
        given:
        new EnumListedThing(kind: EnumSizedKind.ONE).save(flush: true, failOnError: true)
        session.clear()

        expect:
        EnumListedThing.findByKind(EnumSizedKind.ONE).kind == EnumSizedKind.ONE
        EnumListedThing.count() == 1
    }

    void "a maxSize on a collection of enum values limits the number of values and not the length of one"() {
        given: 'a collection with maxSize 2 of values that are longer than two characters'
        new EnumCollectionThing(kinds: [EnumLongKind.FIRST_VALUE, EnumLongKind.SECOND_VALUE]).save(flush: true, failOnError: true)
        session.clear()

        expect: 'both values are stored and read back'
        EnumCollectionThing.list()[0].kinds as Set == [EnumLongKind.FIRST_VALUE, EnumLongKind.SECOND_VALUE] as Set

        and: 'a third value is refused by the constraint of the collection'
        !new EnumCollectionThing(kinds: [EnumLongKind.FIRST_VALUE, EnumLongKind.SECOND_VALUE, EnumLongKind.THIRD_VALUE]).validate()
    }

    void "an enum inList that does not allow a value rejects it"() {
        expect:
        !new EnumListedThing(kind: EnumSizedKind.TWO).validate()
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

enum EnumLongKind { FIRST_VALUE, SECOND_VALUE, THIRD_VALUE }

@Entity
class EnumCollectionThing {
    Set<EnumLongKind> kinds

    static hasMany = [kinds: EnumLongKind]

    static constraints = {
        kinds maxSize: 2
    }
}
