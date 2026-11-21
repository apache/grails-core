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
        // a sort through an association keeps the instances without it with this setting
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = true
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

    void '#api sorts by a path that starts with the alias of the queried class'() {
        given: 'a team without a club'
        new Team(name: 'Unattached First Team').save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        query.call()*.name == expected

        where:
        api                                                     | query                                                                                       || expected
        'a detached criteria'                                   | { new DetachedCriteria(Team, 't').list(sort: 't.name') }                                    || TEAMS_BY_NAME
        'a detached criteria in descending order'               | { new DetachedCriteria(Team, 't').list(sort: 't.name', order: 'desc') }                     || TEAMS_BY_NAME.reverse()
        'the sort of a detached criteria'                       | { new DetachedCriteria(Team, 't').sort('t.name').list() }                                   || TEAMS_BY_NAME
        'a detached criteria with a criterion'                  | { new DetachedCriteria(Team, 't').build { like('name', '%First Team') }.list(sort: 't.name') } || TEAMS_BY_NAME
        'a where query, through the alias Hibernate gives it'   | { Team.where { name ==~ '%' }.list(sort: 'this.name') }                                     || TEAMS_BY_NAME
        'a dynamic finder, through the alias Hibernate gives it' | { Team.findAllByNameLike('%', [sort: 'this.name']) }                                       || TEAMS_BY_NAME
        'findAll by example, through the alias Hibernate gives it' | { Team.findAll(new Team(), [sort: 'this.name']) }                                        || TEAMS_BY_NAME
        'a criteria query, through the alias Hibernate gives it' | { Team.createCriteria().list(sort: 'this.name') { } }                                       || TEAMS_BY_NAME
    }

    void 'a paged detached criteria sorted through the alias of the queried class counts every team'() {
        when:
        final results = new DetachedCriteria(Team, 't').list(max: 2, sort: 't.name', order: 'desc')

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team']
        results.totalCount == 3
    }

    void 'a root alias named #alias takes precedence over a property with the same name'() {
        given:
        final criteria = new DetachedCriteria(Team, alias)

        expect:
        criteria.list(sort: property, order: 'desc')*.name == expected
        criteria.sort(property, 'desc').list()*.name == expected

        where:
        alias  | property         | expected
        'club' | 'club.club.name' | ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
        'name' | 'name.name'      | ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a sort through an association after the alias of the queried class keeps a team without the association'() {
        given:
        new Team(name: 'Unattached First Team').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = new DetachedCriteria(Team, 't').list(sort: 't.club.name', order: 'desc')

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    private static final List<String> TEAMS_BY_NAME = ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team', 'Unattached First Team']

    void 'a sort by an alias that the query defines still sorts'() {
        when:
        final results = Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c1.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }
}
