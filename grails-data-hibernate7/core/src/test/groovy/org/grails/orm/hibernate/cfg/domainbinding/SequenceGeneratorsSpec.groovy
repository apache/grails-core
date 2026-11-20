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

package org.grails.orm.hibernate.cfg.domainbinding

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

import org.hibernate.Session

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Unroll

class SequenceGeneratorsSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EntityWithIdentity,
                                     EntityWithNative,
                                     EntityWithSequence,
                                     EntityWithImplicitSequence,
                                     EntityWithTable,
                                     EntityWithUUID,
                                     EntityWithAssigned)
    }


    @Rollback
    void "test identity generator"() {
        when:
        def entity = new EntityWithIdentity(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test native generator"() {
        when:
        def entity = new EntityWithNative(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test sequence generator"() {
        when:
        def entity = new EntityWithSequence(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test sequence generator with no explicit sequence name"() {
        when: "the mapping names no sequence, so Hibernate must derive an implicit one from the table"
        def entity = new EntityWithImplicitSequence(name: "test").save(flush: true)

        then:
        entity.id != null

        and: "the derived name is the table with Hibernate's suffix, which is what the upgrade notes promise"
        sequenceNames().contains('ENTITY_WITH_IMPLICIT_SEQUENCE_SEQ')
    }

    private static Set<String> sequenceNames() {
        Set<String> names = [] as Set
        EntityWithImplicitSequence.withSession { Session session ->
            session.doWork { Connection connection ->
                connection.createStatement().withCloseable { Statement statement ->
                    statement.executeQuery('select sequence_name from information_schema.sequences')
                            .withCloseable { ResultSet rows ->
                                while (rows.next()) {
                                    names << rows.getString(1).toUpperCase(Locale.ROOT)
                                }
                            }
                }
            }
        }
        names
    }

    @Rollback
    void "test table generator"() {
        when:
        def entity = new EntityWithTable(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test uuid generator"() {
        when:
        def entity = new EntityWithUUID(name: "test").save(flush: true)

        then:
        entity.id != null
        entity.id instanceof String
    }

    @Rollback
    void "test assigned generator"() {
        when:
        def entity = new EntityWithAssigned(id: 123, name: "test").save(flush: true)

        then:
        entity.id == 123
    }
}

@Entity
class EntityWithIdentity {
    Long id
    String name
    static mapping = {
        id generator: 'identity'
    }
}

@Entity
class EntityWithNative {
    Long id
    String name
    static mapping = {
        id generator: 'native'
    }
}

@Entity
class EntityWithSequence {
    Long id
    String name
    static mapping = {
        id generator: 'sequence', params: [sequence_name: 'seq_test']
    }
}

@Entity
class EntityWithImplicitSequence {
    Long id
    String name
    static mapping = {
        id generator: 'sequence'
    }
}

@Entity
class EntityWithTable {
    Long id
    String name
    static mapping = {
        id generator: 'table'
    }
}

@Entity
class EntityWithUUID {
    String id
    String name
    static mapping = {
        id generator: 'uuid'
    }
}

@Entity
class EntityWithAssigned {
    Long id
    String name
    static mapping = {
        id generator: 'assigned'
    }
}
