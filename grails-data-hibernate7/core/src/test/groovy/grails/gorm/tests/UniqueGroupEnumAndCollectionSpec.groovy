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
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A {@code unique} group makes a unique key over the property's column and the columns of the listed
 * properties. It has to do so for enum properties as it does for every other column, and it has no meaning on
 * a collection property, whose table does not hold the listed columns.
 */
@Rollback
class UniqueGroupEnumAndCollectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            UgEnumGroup, UgEnumConstraintGroup, UgStringGroup, UgEnumUnique, UgCollectionGroup, UgEnumCollectionGroup, UgSerializableGroup)

    void "a unique group on an enum property makes a unique key over the enum and the listed columns"() {
        expect:
        uniqueKeyColumns(UgEnumGroup) == [['state', 'other'] as Set]
    }

    void "a unique group declared in the constraints block on an enum property makes the same key"() {
        expect:
        uniqueKeyColumns(UgEnumConstraintGroup) == [['state', 'other'] as Set]
    }

    void "a unique group on a string property still makes a unique key, as before"() {
        expect:
        uniqueKeyColumns(UgStringGroup) == [['name', 'other'] as Set]
    }

    void "a duplicate of an enum unique group is rejected by the database"() {
        given:
        new UgEnumGroup(state: UgState.ON, other: 'a').save(flush: true, failOnError: true)
        UgEnumGroup.withSession { it.clear() }

        when:
        new UgEnumGroup(state: UgState.ON, other: 'a').save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    void "the same enum with another value of the grouped column is accepted"() {
        when:
        new UgEnumGroup(state: UgState.ON, other: 'b').save(flush: true, failOnError: true)
        new UgEnumGroup(state: UgState.OFF, other: 'b').save(flush: true, failOnError: true)
        new UgEnumGroup(state: UgState.ON, other: 'c').save(flush: true, failOnError: true)

        then:
        UgEnumGroup.countByOther('b') == 2
    }

    void "a plain unique enum property is unique in the database"() {
        given:
        new UgEnumUnique(state: UgState.ON).save(flush: true, failOnError: true)
        UgEnumUnique.withSession { it.clear() }

        when:
        new UgEnumUnique(state: UgState.ON).save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    void "the unique keys of a collection table name only columns of that table"() {
        given:
        Collection collection = datastore.metadata.getCollectionBinding(UgCollectionGroup.name + '.tags')
        Table table = collection.collectionTable
        Set<String> columns = table.columns*.name.toSet()

        expect:
        table.uniqueKeys.values().every { it.columns*.name.every { String name -> columns.contains(name) } }
    }

    void "a collection property with a unique group can be saved and reloaded"() {
        given:
        UgCollectionGroup owner = new UgCollectionGroup(x: 'x')
        owner.addToTags('one')
        owner.addToTags('two')
        owner.save(flush: true, failOnError: true)
        UgCollectionGroup.withSession { it.clear() }

        expect:
        UgCollectionGroup.get(owner.id).tags == ['one', 'two'] as Set
    }

    void "a collection of enums with a unique group can be saved and reloaded"() {
        given:
        UgEnumCollectionGroup owner = new UgEnumCollectionGroup(x: 'x')
        owner.addToStates(UgState.ON)
        owner.addToStates(UgState.OFF)
        owner.save(flush: true, failOnError: true)
        UgEnumCollectionGroup.withSession { it.clear() }

        expect:
        UgEnumCollectionGroup.get(owner.id).states == [UgState.ON, UgState.OFF] as Set
    }

    void "a serializable collection property keeps its unique group key on the owner table"() {
        expect:
        uniqueKeyColumns(UgSerializableGroup) == [['tags', 'x'] as Set]
    }

    void "a duplicate of a serializable collection unique group is rejected by the database"() {
        given:
        new UgSerializableGroup(x: 'x', tags: ['one'] as Set).save(flush: true, failOnError: true)
        UgSerializableGroup.withSession { it.clear() }

        when:
        new UgSerializableGroup(x: 'x', tags: ['one'] as Set).save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    private List<Set<String>> uniqueKeyColumns(Class<?> domain) {
        Table table = datastore.metadata.getEntityBinding(domain.name).table
        table.uniqueKeys.values().collect { it.columns*.name*.toLowerCase().toSet() }
    }
}

enum UgState {
    ON, OFF
}

@Entity
class UgEnumGroup {
    UgState state
    String other

    static mapping = {
        state unique: 'other'
    }
}

@Entity
class UgEnumConstraintGroup {
    UgState state
    String other

    static constraints = {
        state unique: 'other'
    }
}

@Entity
class UgStringGroup {
    String name
    String other

    static mapping = {
        name unique: 'other'
    }
}

@Entity
class UgEnumUnique {
    UgState state

    static mapping = {
        state unique: true
    }
}

@Entity
class UgCollectionGroup {
    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags unique: 'x'
    }
}

@Entity
class UgEnumCollectionGroup {
    String x
    Set<UgState> states

    static hasMany = [states: UgState]

    static mapping = {
        states unique: 'x'
    }
}

@Entity
class UgSerializableGroup {
    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags type: 'serializable', unique: 'x'
    }
}
