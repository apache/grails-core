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

import grails.testing.mixin.integration.Integration
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification

/**
 * A bulk write has no LIMIT or OFFSET, so a criteria with max or offset is refused and writes nothing.
 * Hibernate 5 ignores them and writes every matching row, so this scenario is not shared with it.
 */
@Integration(applicationClass = Application)
class BulkWriteGuardScenarioSpec extends Specification {

    void setup() {
        ScenarioItem.withNewTransaction {
            new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE).save(flush: true, failOnError: true)
        }
    }

    void cleanup() {
        ScenarioItem.withNewTransaction {
            ScenarioItem.list()*.delete(flush: true)
        }
    }

    private static List<String> left() {
        ScenarioItem.withNewSession { ScenarioItem.list()*.name.sort() }
    }

    void "deleteAll with max is refused and deletes nothing"() {
        when:
        ScenarioItem.withNewTransaction { ScenarioItem.where { qty > 0 }.max(1).deleteAll() }

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message.contains('max(1)')
        left() == ['a', 'b', 'c']
    }

    void "deleteAll with offset is refused and deletes nothing"() {
        when:
        ScenarioItem.withNewTransaction { ScenarioItem.where { qty > 0 }.offset(1).deleteAll() }

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message.contains('offset(1)')
        left() == ['a', 'b', 'c']
    }

    void "updateAll with max is refused and updates nothing"() {
        when:
        ScenarioItem.withNewTransaction { ScenarioItem.where { qty > 0 }.max(1).updateAll(status: ScenarioStatus.DONE) }

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        e.message.contains('max(1)')
        ScenarioItem.withNewSession { ScenarioItem.countByStatus(ScenarioStatus.DONE) } == 1
    }
}
