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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * A subclass that sets tablePerHierarchy false under an abstract base class is joined, so the association to a
 * deeper subclass loads (#14423).
 */
@Integration(applicationClass = Application)
class JoinedHierarchyHibernate5ScenarioSpec extends Specification {

    void cleanup() {
        ScenarioInheritanceMan.withNewTransaction {
            ScenarioInheritanceMan.list()*.delete(flush: true)
            ScenarioInheritanceTeam.list()*.delete(flush: true)
        }
    }

    void "a subclass with tablePerHierarchy false under an abstract base class loads through a collection"() {
        given:
        Long teamId = ScenarioInheritanceTeam.withNewTransaction {
            ScenarioInheritanceTeam team = new ScenarioInheritanceTeam().save(failOnError: true)
            new ScenarioInheritanceMan(firstName: 'a', weight: 70L, team: team).save(failOnError: true)
            team.id
        }

        expect:
        ScenarioInheritanceTeam.withNewSession { ScenarioInheritanceTeam.get(teamId).men.size() } == 1
    }
}
