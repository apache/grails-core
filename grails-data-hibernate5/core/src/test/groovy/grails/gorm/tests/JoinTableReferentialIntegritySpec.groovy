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

import java.sql.SQLException

import grails.gorm.annotation.Entity
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14430')
class JoinTableReferentialIntegritySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(JoinTableIntegrityMessage)
    }

    void "a collection mapped with joinTable gets a foreign key to its owner"() {
        when: 'the schema is read back from the database'
        List<String> foreignKeys = []
        manager.sessionFactory.currentSession.doWork { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select table_name from information_schema.table_constraints " +
                    "where table_schema = 'PUBLIC' and constraint_type = 'FOREIGN KEY'")
            while (rs.next()) {
                foreignKeys << rs.getString(1).toLowerCase()
            }
        }

        then: 'both join tables are constrained'
        foreignKeys.contains('join_table_integrity_to')
        foreignKeys.contains('join_table_integrity_cc')
    }

    void "a join table row for an owner that does not exist is rejected"() {
        when: 'a row is inserted straight into the join table'
        Throwable failure = null
        manager.sessionFactory.currentSession.doWork { conn ->
            try {
                conn.createStatement().executeUpdate("insert into join_table_integrity_to (mess_id, addr) values (999999, 'a@example.com')")
            } catch (SQLException e) {
                failure = e
            }
        }

        then:
        failure != null
    }

    void "elements saved through the owner are stored and removed with it"() {
        given:
        JoinTableIntegrityMessage message = new JoinTableIntegrityMessage(subject: 'hello', tos: ['a@example.com', 'b@example.com'], ccs: ['c@example.com'])
        message.save(flush: true)
        session.clear()

        when:
        JoinTableIntegrityMessage reloaded = JoinTableIntegrityMessage.get(message.id)

        then:
        reloaded.tos as Set == ['a@example.com', 'b@example.com'] as Set
        reloaded.ccs as Set == ['c@example.com'] as Set

        when:
        reloaded.delete(flush: true)
        session.clear()

        then:
        JoinTableIntegrityMessage.count() == 0
    }
}

@Entity
class JoinTableIntegrityMessage {

    String subject

    static hasMany = [tos: String, ccs: String]

    static mapping = {
        tos joinTable: [name: 'join_table_integrity_to', key: 'mess_id', column: 'addr', type: 'text']
        ccs joinTable: [name: 'join_table_integrity_cc', key: 'mess_id', column: 'addr', type: 'text']
    }
}
