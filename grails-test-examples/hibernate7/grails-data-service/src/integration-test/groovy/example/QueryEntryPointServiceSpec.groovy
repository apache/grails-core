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

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * A where query compiled into a data service method goes through the compile-time path of a service, not the one of
 * a where query in a class. The aggregate on the right of such a comparison is in WhereServiceAggregateScenarioSpec.
 *
 * Data: the club c1 has the team alpha, c2 has beta, c3 has no team.
 */
@Rollback
@Integration
class QueryEntryPointServiceSpec extends Specification {

    QsClubService qsClubService

    def setup() {
        QsClub.executeUpdate('delete from QsClub', [:])
        QsTeam.executeUpdate('delete from QsTeam', [:])
        QsTeam alpha = new QsTeam(name: 'alpha').save(flush: true, failOnError: true)
        QsTeam beta = new QsTeam(name: 'beta').save(flush: true, failOnError: true)
        new QsClub(name: 'c1', team: alpha).save(flush: true, failOnError: true)
        new QsClub(name: 'c2', team: beta).save(flush: true, failOnError: true)
        new QsClub(name: 'c3').save(flush: true, failOnError: true)
    }

    void "a service where query on an association path returns the clubs of the team"() {
        expect:
        qsClubService.inTeam('alpha')*.name == ['c1']
    }

    void "a service where query with or on an association path and a property drops a club without a team unless it is named"() {
        expect: 'c3 has no team, so the join of the team drops it, even though it is named'
        qsClubService.inTeamOrNamed('beta', 'c3')*.name == ['c2']
    }
}
