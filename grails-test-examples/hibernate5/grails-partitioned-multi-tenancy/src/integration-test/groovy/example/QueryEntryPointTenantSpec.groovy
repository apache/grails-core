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

package example

import datasources.Application
import spock.lang.Specification

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration

/**
 * The tenant restriction and an association path together: a query on the association of a club returns the clubs of
 * the current tenant, though the other tenant has a team of the same name.
 */
@Integration(applicationClass = Application)
class QueryEntryPointTenantSpec extends Specification {

    def setup() {
        ['one', 'two'].each { String tenant ->
            Tenants.withId(tenant) {
                QsClub.withTransaction {
                QsClub.executeUpdate('delete from QsClub', [:])
                QsTeam.executeUpdate('delete from QsTeam', [:])
                QsTeam team = new QsTeam(name: 'alpha').save(flush: true, failOnError: true)
                new QsClub(name: "club-${tenant}", team: team).save(flush: true, failOnError: true)
                }
            }
        }
    }

    def cleanup() {
        ['one', 'two'].each { String tenant ->
            Tenants.withId(tenant) {
                QsClub.withTransaction {
                    QsClub.executeUpdate('delete from QsClub', [:])
                    QsTeam.executeUpdate('delete from QsTeam', [:])
                }
            }
        }
    }

    void "an association query returns the clubs of the current tenant"() {
        expect:
        Tenants.withId('one') { QsClub.withTransaction { QsClub.withCriteria { team { eq 'name', 'alpha' } }*.name } } == ['club-one']
        Tenants.withId('two') { QsClub.withTransaction { QsClub.where { team.name == 'alpha' }.list()*.name } } == ['club-two']
    }
}
