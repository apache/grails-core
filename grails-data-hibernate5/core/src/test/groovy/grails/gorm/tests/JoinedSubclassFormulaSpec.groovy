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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A property with a formula holds no value of its own, in a joined subclass as everywhere: its table has no column for
 * it, and its value is the value of the formula. That holds for an enum property as for any other.
 */
class JoinedSubclassFormulaSpec extends Specification {

    @Shared
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:joinedSubclassFormula;LOCK_TIMEOUT=10000'),
            FormulaRoot, FormulaJoined)

    void cleanupSpec() {
        datastore?.close()
    }

    @Unroll
    void 'the joined subclass table has no column for the #kind property that has a formula'() {
        when:
        List<String> columns = []
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { conn ->
                def rs = conn.createStatement().executeQuery(
                        "select column_name from information_schema.columns where table_name = 'FORMULA_JOINED'")
                while (rs.next()) {
                    columns << rs.getString(1).toLowerCase()
                }
            }
        }

        then:
        !columns.contains(column)
        columns.contains('own')

        where:
        kind     | column
        'text'   | 'label'
        'enum'   | 'state'
    }

    void 'the value of the properties with a formula is the value of the formula'() {
        given:
        FormulaJoined.withNewTransaction { new FormulaJoined(own: 'x').save(flush: true, failOnError: true) }

        expect:
        FormulaJoined.withNewSession { FormulaJoined.list()[0] }.with {
            assert label == 'fixed'
            assert state == FormulaState.ACTIVE
            true
        }
    }
}

enum FormulaState { ACTIVE, DONE }

@Entity
class FormulaRoot {
    String name

    static constraints = {
        name nullable: true
    }

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class FormulaJoined extends FormulaRoot {
    String own
    String label
    FormulaState state

    static constraints = {
        label nullable: true
        state nullable: true
    }

    static mapping = {
        label formula: "'fixed'"
        state formula: "'ACTIVE'"
    }
}
