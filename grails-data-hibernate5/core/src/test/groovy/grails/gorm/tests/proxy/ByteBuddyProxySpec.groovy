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

package grails.gorm.tests.proxy

import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.proxy.HibernateProxyHandler

import grails.gorm.tests.Club
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Team
import spock.lang.PendingFeature
import spock.lang.PendingFeatureIf

/**
 * Contains misc proxy tests using Hibenrate defaults, which is ByteBuddy.
 * These should all be passing for Gorm to be operating correctly with Groovy.
 */
class ByteBuddyProxySpec extends GormDatastoreSpec {
    static HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    //to show test that fail that should succeed set this to true. or uncomment the
    // testImplementation "org.yakworks:hibernate-groovy-proxy:$yakworksHibernateGroovyProxy" to see pass
    boolean runPending = ClassUtils.isPresent("yakworks.hibernate.proxy.ByteBuddyGroovyInterceptor")

    @Override
    List getDomainClasses() { [Team, Club] }

    Team createATeam(){
        Club c = new Club(name: "DOOM Club").save(failOnError:true)
        Team team = new Team(name: "The A-Team", club: c).save(failOnError:true, flush:true)
        return team
    }

    void "getId and id property checks dont initialize proxy if in a CompileStatic method"() {
        when:
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on getId and id should not initialize proxy when statically compiled"
        StaticTestUtil.team_id_asserts(team)
        !proxyHandler.isInitialized(team)

        StaticTestUtil.club_id_asserts(team)
        !proxyHandler.isInitialized(team.club)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "getId and id dont initialize proxy"() {
        when:"load proxy"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on getId and id should not initialize proxy"
        proxyHandler.isProxy(team)
        team.getId()
        !proxyHandler.isInitialized(team)

        team.id
        !proxyHandler.isInitialized(team)

        and: "the getAt check for id should not initialize"
        team['id']
        !proxyHandler.isInitialized(team)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "truthy check on instance should not initialize proxy"() {
        when:"load proxy"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        team
        !proxyHandler.isInitialized(team)

        and: "truthy check on association should not initialize"
        team.club
        !proxyHandler.isInitialized(team.club)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "id checks on association should not initialize its proxy"() {
        when:"load instance"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        !proxyHandler.isInitialized(team.club)

        team.club.getId()
        !proxyHandler.isInitialized(team.club)

        team.club.id
        !proxyHandler.isInitialized(team.club)

        team.clubId
        !proxyHandler.isInitialized(team.club)

        and: "the getAt check for id should not initialize"
        team.club['id']
        !proxyHandler.isInitialized(team.club)
    }

    void "isDirty should not intialize the association proxy"() {
        when:"load instance"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        !proxyHandler.isInitialized(team)

        //isDirty will init the proxy. should make changes for this.
        !team.isDirty()
        proxyHandler.isInitialized(team)
        //it should not have initialized the association
        !proxyHandler.isInitialized(team.club)

        when: "its made dirty"
        team.name = "B-Team"

        then:
        team.isDirty()
        //still should not have initialized it.
        !proxyHandler.isInitialized(team.club)
    }

}
