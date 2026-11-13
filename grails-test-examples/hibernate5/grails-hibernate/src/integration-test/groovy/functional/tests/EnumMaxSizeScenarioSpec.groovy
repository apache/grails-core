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

import javax.sql.DataSource

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * maxSize on an enum property is honoured like it is on a string property (#14505).
 */
@Integration(applicationClass = Application)
class EnumMaxSizeScenarioSpec extends Specification {

    @Autowired
    DataSource dataSource

    void cleanup() {
        ScenarioEnumSized.withNewTransaction {
            ScenarioEnumSized.list()*.delete(flush: true)
        }
    }

    void "an enum property with maxSize stores and reads its value"() {
        given:
        ScenarioEnumSized.withNewTransaction {
            new ScenarioEnumSized(label: 'x', kind: ScenarioEnumKind.TWO).save(failOnError: true)
        }

        expect:
        ScenarioEnumSized.withNewSession { ScenarioEnumSized.findByKind(ScenarioEnumKind.TWO).kind == ScenarioEnumKind.TWO }
    }

    void "the enum column is as long as maxSize says"() {
        when:
        Map<String, Integer> lengths = [:]
        dataSource.connection.withCloseable { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select column_name, character_maximum_length from information_schema.columns " +
                    "where lower(table_name) = 'scenario_enum_sized'")
            while (rs.next()) {
                lengths[rs.getString(1).toLowerCase()] = rs.getInt(2)
            }
        }

        then:
        lengths['label'] == 100
        lengths['kind'] == 100
    }
}
