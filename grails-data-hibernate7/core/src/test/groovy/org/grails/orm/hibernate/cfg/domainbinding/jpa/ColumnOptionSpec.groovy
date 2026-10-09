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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.mapping.Column

/**
 * Column options of a simple property and of an enum property that reach the bound column and the rows: the write flags
 * ({@code insertable}, {@code updatable}) and the comment, default and read and write expressions of an enum column, which
 * {@link GrailsDomainGenerator} states as the mapping asks for.
 */
class ColumnOptionSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DefectWritable, DefectReadOnlyColumns, DefectEnumGroup, DefectCollectionGroup, DefectEnumColumn)
    }

    void "a column with no write restriction is inserted and updated"() {
        given:
        DefectWritable row = new DefectWritable(inserted: 'a', updated: 'b').save(flush: true, failOnError: true)
        session.clear()
        DefectWritable loaded = DefectWritable.get(row.id)
        loaded.updated = 'c'
        loaded.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectWritable.get(row.id).inserted == 'a'
        DefectWritable.get(row.id).updated == 'c'
    }

    void "insertable false keeps the value out of the insert"() {
        given:
        DefectReadOnlyColumns row = new DefectReadOnlyColumns(inserted: 'a', updated: 'b').save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectReadOnlyColumns.get(row.id).inserted == null
    }

    void "updatable false keeps the value out of the update"() {
        given:
        DefectReadOnlyColumns row = new DefectReadOnlyColumns(updated: 'b').save(flush: true, failOnError: true)
        session.clear()
        DefectReadOnlyColumns loaded = DefectReadOnlyColumns.get(row.id)
        loaded.updated = 'c'
        loaded.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectReadOnlyColumns.get(row.id).updated == 'b'
    }

    void "a unique group on an enum property is unique in the database (classic binding never created the key)"() {
        given:
        new DefectEnumGroup(state: DefectState.ON, other: 'a').save(flush: true, failOnError: true)
        session.clear()

        when:
        new DefectEnumGroup(state: DefectState.ON, other: 'a').save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    void "the comment, default and read and write expressions of an enum column reach the column"() {
        given:
        Column column = getPersistentEntity(DefectEnumColumn).persistentClass.getProperty('state').columns[0] as Column

        expect:
        column.comment == 'the state'
        column.defaultValue == "'ON'"
        column.customRead == 'lower(state)'
        column.customWrite == 'upper(?)'
    }

    void "a unique group on a collection property creates no key, since the collection table does not hold the columns of the group"() {
        given:
        new DefectCollectionGroup(x: 'a', tags: ['one', 'two'] as Set).save(flush: true, failOnError: true)
        session.clear()

        when: 'another owner repeats the value the group names'
        new DefectCollectionGroup(x: 'a', tags: ['one'] as Set).save(flush: true, failOnError: true)
        session.clear()

        then:
        DefectCollectionGroup.count() == 2
    }
}

@Entity
class DefectWritable {

    String inserted
    String updated
}

@Entity
class DefectReadOnlyColumns {

    String inserted
    String updated

    static mapping = {
        inserted insertable: false
        updated updatable: false
    }
}

@Entity
class DefectEnumGroup {

    DefectState state
    String other

    static mapping = {
        state unique: 'other'
    }
}

@Entity
class DefectCollectionGroup {

    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags unique: 'x'
    }
}

@Entity
class DefectEnumColumn {

    DefectState state

    static mapping = {
        state comment: 'the state', defaultValue: "'ON'", read: 'lower(state)', write: 'upper(?)'
    }
}

enum DefectState {
    ON, OFF
}
