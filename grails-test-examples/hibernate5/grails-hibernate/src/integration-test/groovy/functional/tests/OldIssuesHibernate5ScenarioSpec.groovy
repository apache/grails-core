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

import org.hibernate.criterion.Restrictions
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * Behaviour of Hibernate 5 that reporters asked for years ago and that is now pinned (#14354, #14370, #14423).
 */
@Integration(applicationClass = Application)
class OldIssuesHibernate5ScenarioSpec extends Specification {

    void cleanup() {
        ScenarioInheritanceMan.withNewTransaction {
            ScenarioInheritanceMan.list()*.delete(flush: true)
            ScenarioInheritanceTeam.list()*.delete(flush: true)
            ScenarioOldIssueItem.list()*.delete(flush: true)
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

    void "merge writes the changed fields of a detached instance"() {
        given:
        ScenarioOldIssueItem item = ScenarioOldIssueItem.withNewTransaction {
            new ScenarioOldIssueItem(name: 'a').save(failOnError: true)
        }

        when:
        item.name = 'changed'
        ScenarioOldIssueItem.withNewTransaction {
            item.merge(flush: true)
        }

        then:
        ScenarioOldIssueItem.withNewSession { ScenarioOldIssueItem.get(item.id).name } == 'changed'
    }

    void "a Hibernate criterion inside or is part of the disjunction"() {
        given:
        ScenarioOldIssueItem.withNewTransaction {
            ['one', 'two', 'three'].each { new ScenarioOldIssueItem(name: it).save(failOnError: true) }
        }

        expect:
        ScenarioOldIssueItem.withNewSession {
            ScenarioOldIssueItem.createCriteria().list {
                or {
                    add Restrictions.eq('name', 'two')
                    eq 'name', 'one'
                }
                order 'name'
            }*.name
        } == ['one', 'two']
    }
}
