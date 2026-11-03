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
package functional.tests

import java.sql.SQLException
import javax.sql.DataSource

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * A collection mapped with joinTable is constrained to its owner like any other association (#14430).
 */
@Integration(applicationClass = Application)
class JoinTableReferentialIntegrityScenarioSpec extends Specification {

    @Autowired
    DataSource dataSource

    void cleanup() {
        ScenarioMailMessage.withNewTransaction {
            ScenarioMailMessage.list()*.delete(flush: true)
        }
    }

    void "each join table has a foreign key to the message"() {
        when:
        List<String> constrained = []
        dataSource.connection.withCloseable { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select table_name from information_schema.table_constraints where constraint_type = 'FOREIGN KEY'")
            while (rs.next()) {
                constrained << rs.getString(1).toLowerCase()
            }
        }

        then:
        constrained.contains('scenario_mail_to')
        constrained.contains('scenario_mail_cc')
    }

    void "a recipient row for a message that does not exist is rejected by the database"() {
        when:
        dataSource.connection.withCloseable { conn ->
            conn.createStatement().executeUpdate("insert into scenario_mail_to (message_id, addr) values (999999, 'a@example.com')")
        }

        then:
        thrown(SQLException)
    }

    void "recipients are stored with the message and removed with it"() {
        given:
        Long id = ScenarioMailMessage.withNewTransaction {
            new ScenarioMailMessage(subject: 'hi', tos: ['a@example.com', 'b@example.com'], ccs: ['c@example.com'])
                    .save(flush: true, failOnError: true).id
        }

        expect:
        ScenarioMailMessage.withNewSession {
            ScenarioMailMessage m = ScenarioMailMessage.get(id)
            m.tos as Set == ['a@example.com', 'b@example.com'] as Set && m.ccs as Set == ['c@example.com'] as Set
        }

        when:
        ScenarioMailMessage.withNewTransaction { ScenarioMailMessage.get(id).delete(flush: true) }

        then:
        ScenarioMailMessage.withNewSession { ScenarioMailMessage.count() } == 0
    }
}
