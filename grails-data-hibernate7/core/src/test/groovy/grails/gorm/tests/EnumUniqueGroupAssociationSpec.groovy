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
import grails.gorm.hibernate.HibernateEntity
import groovy.transform.EqualsAndHashCode
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/16713')
class EnumUniqueGroupAssociationSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EnumUniqueCompositeParent, EnumUniqueCompositeChild,
                StringUniqueCompositeChild, DeclaredFirstUniqueCompositeChild)
    }

    void "an enum unique group includes every mapped column of a composite parent"() {
        given:
        def table = datastore.metadata.getEntityBinding(EnumUniqueCompositeChild.name).table

        expect:
        table.uniqueKeys.values().collect { it.columns*.name.toSet() } ==
                [['child_state', 'parent_code', 'parent_revision'] as Set]
        table.getForeignKeys().values().any { it.columns*.name.toSet() == ['parent_code', 'parent_revision'] as Set }
    }

    void "the same enum can be saved for parents differing in either identifier column"() {
        when:
        [['a', 1], ['a', 2], ['b', 1]].each { code, revision ->
            def parent = new EnumUniqueCompositeParent(code: code, revision: revision).save(failOnError: true)
            new EnumUniqueCompositeChild(parent: parent, state: EnumUniqueCompositeState.ON).save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()

        then:
        EnumUniqueCompositeChild.count() == 3
        EnumUniqueCompositeChild.list()*.parent.collect { [it.code, it.revision] }.toSet() ==
                [['a', 1], ['a', 2], ['b', 1]] as Set
    }

    void "a duplicate enum for the same composite parent violates the unique key"() {
        given:
        def parent = new EnumUniqueCompositeParent(code: 'duplicate', revision: 1).save(failOnError: true)
        new EnumUniqueCompositeChild(parent: parent, state: EnumUniqueCompositeState.ON).save(flush: true, failOnError: true)

        when:
        new EnumUniqueCompositeChild(parent: parent, state: EnumUniqueCompositeState.ON).save(flush: true, validate: false)

        then:
        thrown(DataIntegrityViolationException)
    }

    void "a string unique group includes every configured column of a composite parent"() {
        given:
        def table = datastore.metadata.getEntityBinding(StringUniqueCompositeChild.name).table

        expect:
        table.uniqueKeys.values().collect { it.columns*.name.toSet() } ==
                [['code', 'parent_code', 'parent_revision'] as Set]
    }

    void "declared-first enum and string groups include every implicit composite-association column"() {
        given:
        def table = datastore.metadata.getEntityBinding(DeclaredFirstUniqueCompositeChild.name).table
        def foreignKeyColumns = ['enum_unique_composite_parent_code', 'enum_unique_composite_parent_revision'] as Set

        expect:
        table.uniqueKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                [(['code'] as Set) + foreignKeyColumns, (['kind'] as Set) + foreignKeyColumns] as Set
        table.getForeignKeys().values().any { it.columns*.name.toSet() == foreignKeyColumns }
    }

    void "declared-first enum and string groups accept parents differing in either identifier column"() {
        when:
        [['a', 1], ['a', 2], ['b', 1]].each { code, revision ->
            def parent = new EnumUniqueCompositeParent(code: code, revision: revision).save(failOnError: true)
            new DeclaredFirstUniqueCompositeChild(parent: parent, code: 'same', kind: EnumUniqueCompositeState.ON)
                    .save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()

        then:
        DeclaredFirstUniqueCompositeChild.count() == 3
    }

    void "a duplicate declared-first #property for the same composite parent violates the unique key"() {
        given:
        def parent = new EnumUniqueCompositeParent(code: 'duplicate', revision: 1).save(failOnError: true)
        new DeclaredFirstUniqueCompositeChild(parent: parent, code: 'same', kind: EnumUniqueCompositeState.ON)
                .save(flush: true, failOnError: true)

        when:
        new DeclaredFirstUniqueCompositeChild(parent: parent, code: code, kind: kind).save(flush: true, validate: false)

        then:
        thrown(DataIntegrityViolationException)

        where:
        property | code    | kind
        'string' | 'same'  | EnumUniqueCompositeState.OFF
        'enum'   | 'other' | EnumUniqueCompositeState.ON
    }
}

enum EnumUniqueCompositeState {
    ON, OFF
}

@Entity
@EqualsAndHashCode(includes = ['code', 'revision'])
class EnumUniqueCompositeParent implements HibernateEntity<EnumUniqueCompositeParent>, Serializable {
    String code
    Integer revision

    static mapping = {
        id composite: ['code', 'revision']
    }
}

@Entity
class EnumUniqueCompositeChild implements HibernateEntity<EnumUniqueCompositeChild> {
    EnumUniqueCompositeParent parent
    EnumUniqueCompositeState state

    static mapping = {
        state column: 'child_state', unique: 'parent'
        parent {
            column name: 'parent_code'
            column name: 'parent_revision'
        }
    }
}

@Entity
class StringUniqueCompositeChild implements HibernateEntity<StringUniqueCompositeChild> {
    String code
    EnumUniqueCompositeParent parent

    static mapping = {
        code unique: 'parent'
        parent {
            column name: 'parent_code'
            column name: 'parent_revision'
        }
    }
}

@Entity
class DeclaredFirstUniqueCompositeChild implements HibernateEntity<DeclaredFirstUniqueCompositeChild> {
    String code
    EnumUniqueCompositeState kind
    EnumUniqueCompositeParent parent

    static mapping = {
        code unique: 'parent'
        kind unique: 'parent'
    }
}
