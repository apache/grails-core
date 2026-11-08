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

import grails.gorm.DetachedCriteria
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class UndefinedAliasSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    void setup() {
        ['Manchester United', 'Arsenal', 'Chelsea'].each { clubName ->
            final club = new Club(name: clubName).save()
            new Team(club: club, name: "${clubName} First Team").save()
        }
        manager.session.flush()
        manager.session.clear()
    }

    void 'a sort by an alias that the query does not define is rejected, whichever way the query is made'() {
        when:
        query.call()

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Invalid sort property'

        where:
        description                                   | query
        'a where query with a criterion on the club'  | { Team.where { club.name ==~ '%e%' }.list(sort: 'c1.name') }
        'a where query that defines another alias'    | { Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c2.name') }
        'a list without any criterion'                | { Team.list(sort: 'c1.name') }
        'a dynamic finder'                            | { Team.findAllByNameLike('%', [sort: 'c1.name']) }
        'a list that does not ignore case'            | { Team.list(sort: 'c1.name', ignoreCase: false) }
        'an order added to a detached criteria'       | { new DetachedCriteria(Team).build { order('c1.name') }.list() }
    }

    void 'a sort by an alias that the query defines still sorts'() {
        when:
        final results = Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c1.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }
}
