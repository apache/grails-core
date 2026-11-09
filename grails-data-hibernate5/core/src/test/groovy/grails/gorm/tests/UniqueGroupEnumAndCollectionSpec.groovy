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

import org.hibernate.mapping.Table
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A {@code unique} group makes a unique key over the property's column and the columns of the listed
 * properties. It has to do so for enum properties as it does for other columns, and it has no meaning on
 * a collection property, whose table does not hold the listed columns.
 */
@Rollback
class UniqueGroupEnumAndCollectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            UgEnumGroup, UgEnumConstraintGroup, UgStringGroup, UgEnumUnique, UgCollectionGroup, UgEnumCollectionGroup
    )
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Unroll
    void 'a unique group on #description makes a unique key over the property and the listed columns'() {
        expect:
        uniqueKeyColumns(datastore.metadata.getEntityBinding(domain.name).table) == [columns as Set]

        where:
        description                                  | domain                | columns
        'an enum property'                           | UgEnumGroup           | ['state', 'other']
        'an enum property in the constraints block'  | UgEnumConstraintGroup | ['state', 'other']
        'a string property'                          | UgStringGroup         | ['name', 'other']
    }

    void 'a duplicate of an enum unique group is rejected by the database'() {
        given:
        new UgEnumGroup(state: UgState.ON, other: 'a').save(flush: true, failOnError: true)
        UgEnumGroup.withSession { it.clear() }

        when:
        new UgEnumGroup(state: UgState.ON, other: 'a').save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    void 'the same enum with another value of the grouped column is accepted'() {
        when:
        new UgEnumGroup(state: UgState.ON, other: 'b').save(flush: true, failOnError: true)
        new UgEnumGroup(state: UgState.OFF, other: 'b').save(flush: true, failOnError: true)
        new UgEnumGroup(state: UgState.ON, other: 'c').save(flush: true, failOnError: true)

        then:
        UgEnumGroup.countByOther('b') == 2
    }

    void 'a plain unique enum property is still unique in the database'() {
        given:
        new UgEnumUnique(state: UgState.ON).save(flush: true, failOnError: true)
        UgEnumUnique.withSession { it.clear() }

        when:
        new UgEnumUnique(state: UgState.ON).save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    @Unroll
    void 'a unique group on #role makes no unique key on the collection table'() {
        given:
        var table = datastore.metadata.getCollectionBinding(role).collectionTable

        expect:
        uniqueKeyColumns(table).empty

        where:
        role << [UgCollectionGroup.name + '.tags', UgEnumCollectionGroup.name + '.states']
    }

    void 'a collection property with a unique group can be saved and reloaded'() {
        given:
        var owner = new UgCollectionGroup(x: 'x')
        owner.addToTags('one')
        owner.addToTags('two')
        owner.save(flush: true, failOnError: true)
        UgCollectionGroup.withSession { it.clear() }

        expect:
        UgCollectionGroup.get(owner.id).tags == ['one', 'two'] as Set
    }

    void 'a collection of enums with a unique group can be saved and reloaded'() {
        given:
        var owner = new UgEnumCollectionGroup(x: 'x')
        owner.addToStates(UgState.ON)
        owner.addToStates(UgState.OFF)
        owner.save(flush: true, failOnError: true)
        UgEnumCollectionGroup.withSession { it.clear() }

        expect:
        UgEnumCollectionGroup.get(owner.id).states == [UgState.ON, UgState.OFF] as Set
    }

    private static List<Set<String>> uniqueKeyColumns(Table table) {
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
        state(unique: 'other')
    }
}

@Entity
class UgEnumConstraintGroup {

    UgState state
    String other

    static constraints = {
        state(unique: 'other')
    }
}

@Entity
class UgStringGroup {

    String name
    String other

    static mapping = {
        name(unique: 'other')
    }
}

@Entity
class UgEnumUnique {

    UgState state

    static mapping = {
        state(unique: true)
    }
}

@Entity
class UgCollectionGroup {

    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags(unique: 'x')
    }
}

@Entity
class UgEnumCollectionGroup {

    String x
    Set<UgState> states

    static hasMany = [states: UgState]

    static mapping = {
        states(unique: 'x')
    }
}
