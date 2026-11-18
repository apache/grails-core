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

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.springframework.transaction.TransactionStatus

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * Created by graemerocher on 16/01/2017.
 */
@RestoreSystemProperties
class CurrentTenantTransformSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DATABASE,
                    (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
            ),
            [ConnectionSource.DEFAULT, 'one', 'two'],
            Team,
            Player
    )

    void "Test parsing of @WithoutTenant"() {
        Class testServiceClass = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional

@Transactional
@WithoutTenant
class TestService {

    Integer count() {
        return 10;
    }
   
}

return TestService
''')
        PlayService playService = new PlayService()

        expect:
        testServiceClass.getDeclaredMethod('$mt__count')
        playService.countPlays() == 10
    }

    void "Test parsing of @CurrentTenant"() {
        given:
        Class testServiceClass = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.transactions.Transactional

@CurrentTenant
class TestService {
    @Tenant({ "two" }) 
    List<String> listTwoTeams() {
        return ["Manchester United"]
    }   
    
    List<String> listTeams() {
        return ["Arsenal"]
    }

    @Transactional
    void addTeam(String name) {
        println "good"
    }
    

}

return TestService
''')
        TeamService teamService = new TeamService()
        teamService.setTargetDatastore(Mock(Datastore))

        expect:
        testServiceClass.getDeclaredMethod('$tt__addTeam', String, TransactionStatus)
        testServiceClass.getDeclaredMethod('$mt__addTeam', String, Serializable)
        testServiceClass.getDeclaredMethod('$mt__listTwoTeams', Serializable)

    }

    void "Test @CurrentTenant with service"() {
        given: "A service with @CurrentTenant at the class level is used"
        TeamService teamService = new TeamService()

        when: "a method is invoked"
        def results = teamService.listTeams()

        then: "An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when: "An @Transactional method is invoked"
        teamService.addTeam("test")

        then: "An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when: "An @Transactional method is invoked which has no GORM logic in it"
        teamService.addTeamCheckTransaction("test")

        then: "An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when: "A method that uses @Tenant is used"
        results = teamService.allTwoTeams()

        then: "A tenant id was resolved for that method"
        results.isEmpty()

        when: "A tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "two")
        results = teamService.listTeams()

        then: "The result is correct"
        results.size() == 0

        when: "A @Transactional method is invoked"
        teamService.addTeam("test")
        teamService.addTeamCheckTransaction("test")
        results = teamService.listTeams()

        then: "The results are correct"
        results.size() == 1

        when: "we try with no tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        teamService.addTeam("test 2")

        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "A method that uses @Tenant is used"
        results = teamService.allTwoTeams()

        then: "A tenant id was resolved for that method"
        results.size() == 1

        when: "A method that used @WithoutTenant is used"
        int playerCount = teamService.countPlayers()

        then: "It executed corretly without throwing an exception"
        playerCount == 0
    }

    void 'inside Tenants.withId the current tenant is the one withId binds, not the one the resolver resolves'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'two')

        expect:
        Tenants.withId('one') { Tenants.currentId() } == 'one'
    }

    void 'a @CurrentTenant method inside Tenants.withId runs for the tenant withId binds'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'two')
        final teamService = new TeamService()

        when:
        Tenants.withId('one') {
            teamService.addTeam('Brentford')
        }

        then:
        Tenants.withId('one') { Team.countByName('Brentford') } == 1

        and: 'not for the tenant the resolver resolves'
        Tenants.withId('two') { Team.countByName('Brentford') } == 0
    }

    void 'a @Transactional method runs for the tenant of its datastore while another datastore has a different tenant'() {
        given:
        final teamService = new TeamService()
        final otherDatastore = Stub(Datastore)

        when:
        Tenants.withId('one') {
            CurrentTenantHolder.withTenant(otherDatastore, 'two') {
                teamService.addTeam('Fulham')
            }
        }

        then:
        Tenants.withId('one') { Team.countByName('Fulham') } == 1
    }

    void 'Tenants.withId with a domain class runs for the given tenant while the resolver resolves another one'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'two')

        when:
        Tenants.withId(Team, 'one') { new Team(name: 'Wolves').save(flush: true) }

        then:
        Tenants.withId('one') { Team.countByName('Wolves') } == 1
    }

    void 'Tenants.withId with a domain class inside Tenants.withId for another tenant runs for the given tenant'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'two')

        when:
        Tenants.withId('two') {
            Tenants.withId(Team, 'one') { new Team(name: 'Everton').save(flush: true) }
        }

        then:
        Tenants.withId('one') { Team.countByName('Everton') } == 1
    }
}

@WithoutTenant
class PlayService {

    int countPlays() {
        return 10;
    }

}

@CurrentTenant
class TeamService {

    @WithoutTenant
    int countPlayers() {
        Player.count()
    }

    @Tenant({ "two" })
    List<Team> allTwoTeams() {
        Team.list()
    }

    List<Team> listTeams() {
        Team.list(max: 10)
    }

    @Transactional
    void addTeam(String name) {
        new Team(name: name).save(flush: true)
    }

    @Transactional
    void addTeamCheckTransaction(String name) {
        assert transactionStatus.transaction.sessionHolder.sessions.first().datastore.connectionSources.defaultConnectionSource.name == Tenants.currentId()
    }
}
