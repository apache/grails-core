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
package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.grails.datastore.mapping.core.SessionCreationEvent
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import spock.lang.Shared

/**
 * A query run with no session bound opens a session for itself and closes it when the query
 * returns. An association of a result loads later, through that closed session, and decoding the
 * loaded instance's own associations needs a session.
 */
class LazyAssociationsWithNoSessionSpec extends AutoStartedMongoSpec {

    /** MongoDB returns the first 101 documents with the query and the rest in later batches. */
    static final int BEYOND_FIRST_BATCH = 102

    @Shared
    MongoDatastore datastore

    @Shared
    int sessionsOpened

    @Shared
    Serializable teamId

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl('lazyAssociationsWithNoSession')] as Map,
                LazyLeague, LazyTeam, LazyPlayer, LazyClub, LazyOwner, LazyPet)
        datastore.start()
        datastore.applicationEventPublisher.addApplicationListener({ ApplicationEvent event ->
            if (event instanceof SessionCreationEvent) {
                sessionsOpened++
            }
        } as ApplicationListener)
        LazyPlayer.withNewSession { Session session ->
            LazyLeague league = new LazyLeague(name: 'Premier').save()
            LazyTeam team = new LazyTeam(name: 'Rovers', league: league).save()
            teamId = team.id
            LazyPlayer player = new LazyPlayer(name: 'Player', team: team).save()
            new LazyClub(name: 'Club').addToMembers(player).save()
            new LazyOwner(name: 'Owner').addToPets(new LazyPet(name: 'Rex')).save()
            LazyClub bigClub = new LazyClub(name: 'Big club')
            LazyOwner bigOwner = new LazyOwner(name: 'Big owner')
            (1..BEYOND_FIRST_BATCH).each { int i ->
                bigClub.addToMembers(new LazyPlayer(name: "Member $i", team: team).save())
                bigOwner.addToPets(new LazyPet(name: "Pet $i"))
            }
            bigClub.save()
            bigOwner.save()
            session.flush()
        }
    }

    void cleanupSpec() {
        datastore?.close()
    }

    void "a to-one whose class has associations of its own loads with no session bound"() {
        when:
        String name = LazyPlayer.findByName('Player').team.name

        then:
        name == 'Rovers'
        !datastore.hasCurrentSession()
    }

    void "the loaded instance's own to-one loads with no session bound"() {
        expect:
        LazyPlayer.findByName('Player').team.league.name == 'Premier'
    }

    void "a unidirectional one-to-many whose class has associations of its own loads with no session bound"() {
        expect:
        LazyClub.findByName('Club').members*.name == ['Player']
    }

    void "a bidirectional one-to-many loads with no session bound"() {
        expect:
        LazyOwner.findByName('Owner').pets*.name == ['Rex']
    }

    void "a unidirectional one-to-many longer than the first batch loads with no session bound"() {
        expect:
        LazyClub.findByName('Big club').members*.name.toSet().size() == BEYOND_FIRST_BATCH
    }

    void "a bidirectional one-to-many longer than the first batch loads with no session bound"() {
        expect:
        LazyOwner.findByName('Big owner').pets*.name.toSet().size() == BEYOND_FIRST_BATCH
    }

    void "a to-one loads with no session bound through the Groovy proxy factory"() {
        given:
        ProxyFactory proxyFactory = datastore.mappingContext.proxyFactory
        datastore.mappingContext.proxyFactory = new GroovyProxyFactory()

        when:
        LazyTeam team = LazyPlayer.findByName('Player').team

        then:
        datastore.mappingContext.proxyFactory.isProxy(team)
        team.getClass() == LazyTeam
        team.name == 'Rovers'

        cleanup:
        datastore.mappingContext.proxyFactory = proxyFactory
    }

    void "a proxy from the proxy method loads with no session bound"() {
        expect:
        LazyTeam.proxy(teamId).name == 'Rovers'
    }

    void "a load with a session bound opens no session"() {
        when:
        List<Integer> opened = LazyPlayer.withNewSession {
            LazyPlayer player = LazyPlayer.findByName('Player')
            LazyClub club = LazyClub.findByName('Club')
            [sessionsOpenedRunning { player.team.name }, sessionsOpenedRunning { club.members.size() }]
        }

        then:
        opened == [0, 0]
    }

    private int sessionsOpenedRunning(Closure load) {
        int before = sessionsOpened
        load()
        sessionsOpened - before
    }
}

@Entity
class LazyLeague {
    String name
}

@Entity
class LazyTeam {
    String name
    LazyLeague league
}

@Entity
class LazyPlayer {
    String name
    LazyTeam team
}

@Entity
class LazyClub {
    String name
    static hasMany = [members: LazyPlayer]
}

@Entity
class LazyOwner {
    String name
    static hasMany = [pets: LazyPet]
}

@Entity
class LazyPet {
    String name
    static belongsTo = [owner: LazyOwner]
}
