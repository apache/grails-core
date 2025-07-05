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
package grails.gorm.specs

import grails.gorm.specs.entities.Club
import grails.gorm.specs.entities.Team
import org.apache.grails.data.hibernate6.core.GrailsDataHibernate6TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.hibernate.QueryException
import spock.lang.Issue

/**
 * Created by graemerocher on 03/11/16.
 */
//TODO : How to create an alias inside a closure
class WhereQueryWithAssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate6TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Club, Team])
    }

    @Issue('https://github.com/grails/grails-core/issues/9860')
    void "Test sort with where query that queries association"() {
        given: "some test data"
        def c = new Club(name: "Manchester United").save()
        def t = new Team(club: c, name: "MU First Team").save()
        def c2 = new Club(name: "Arsenal").save()
        def t2 = new Team(club: c2, name: "Arsenal First Team").save(flush: true)

        when: "a where query uses a sort on an association"

        /**
         * 2025/04/25
         *    select
         t1_0.id,
         t1_0.club_id,
         t1_0.name,
         t1_0.version
         from
         team t1_0
         left join
         club c1_0
         on c1_0.id=t1_0.club_id, team t2_0
         join
         club c2_0
         on c2_0.id=t2_0.club_id
         where
         c1_0.name=?
         order by
         lower(c2_0.name)
         offset
         ? rows
         */
        def results = Team.where {
            club.name == "Manchester United"
        }.list(sort: 'club.name')


        then: "an exception is thrown because no alias is specified"
        thrown QueryException


        when: "a where query uses a sort on an association"
        def where = Team.where {
            def c1 = club
            c1.name ==~ '%e%'
        }
        results = where.list(sort: 'c1.name')


        then: "an exception is thrown because no alias is specified"
        results.size() == 2
        results.first().name == "Arsenal First Team"
    }
}
