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
import spock.lang.Specification

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore

class MultipleDataSourceSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [ConnectionSource.DEFAULT, 'one'],
            Player, Coach
    )

    void 'test multiple datasource support with in-memory GORM'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        new Player(name: 'Keane').save(flush: true)
        def service = new PlayerService()
        def dataService = datastore.getService(IPlayerService)
        dataService.savePlayer('Neville')

        expect:
        Player.count() == 2
        new DetachedCriteria<>(Player).count() == 2
        new DetachedCriteria<>(Player).withConnection('one').count() == 1
        Player.one.count() == 1
        service.countPlayers() == 2
        service.countPlayersOne() == 1
        dataService.countPlayers() == 1
    }

    void 'test the operations that name the entity follow a connection scope'() {
        given: 'two players on the default connection and one on the other'
        new Player(name: 'Giggs').save(flush: true)
        new Player(name: 'Keane').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])

        expect: 'a static call on the class uses the scope\'s connection'
        GormRegistry.withConnectionScope(Player, 'one') { Player.count() } == 1
        GormRegistry.withConnectionScope(Player, 'one') { Player.list()*.name } == ['Neville']

        and: 'one that names its connection keeps it'
        GormRegistry.withConnectionScope(Player, 'one') { Player.'default'.count() } == 2

        when: 'an instance is saved inside the scope'
        GormRegistry.withConnectionScope(Player, 'one') {
            new Player(name: 'Irwin').save(flush: true)
        }

        then: 'it reaches the scope\'s connection, and outside the scope the default applies again'
        Player.one.count() == 2
        Player.count() == 2
    }

    void 'test connection scopes nest and are undone by an exception'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        Player.one.save(new Player(name: 'Irwin'), [flush: true])

        expect: 'an inner scope applies inside it, and the outer one again after it'
        GormRegistry.withConnectionScope(Player, 'one') {
            [GormRegistry.withConnectionScope(Player, ConnectionSource.DEFAULT) { Player.count() }, Player.count()]
        } == [1, 2]

        when: 'a scope ends with an exception'
        GormRegistry.withConnectionScope(Player, 'one') {
            throw new IllegalStateException('failed inside the scope')
        }

        then:
        thrown(IllegalStateException)

        and: 'it no longer applies'
        Player.count() == 1
    }

    void 'test an instance saved inside a named connection\'s own session or transaction is written to that connection'() {
        when:
        Player.one.withNewSession {
            new Player(name: 'Scholes').save(flush: true)
        }
        Player.one.withTransaction {
            new Player(name: 'Butt').save(flush: true)
        }

        then:
        Player.one.count() == 2
        Player.count() == 0

        and: 'the class\'s own calls inside that connection\'s session read from it'
        Player.one.withNewSession { Player.count() } == 2
    }

    void 'test a default connection block inside a named connection\'s block uses the default connection'() {
        given: 'one player on the default connection and two on the other'
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        Player.one.save(new Player(name: 'Irwin'), [flush: true])

        expect: 'the class\'s own calls follow the innermost block, and the outer one again after it'
        Player.one.withTransaction {
            [Player.'default'.withTransaction { Player.count() }, Player.count()]
        } == [1, 2]
    }

    void 'test the class\'s own calls inside a named connection\'s current session read from it'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        Player.one.save(new Player(name: 'Irwin'), [flush: true])

        expect:
        Player.one.withSession { Player.count() } == 2
        Player.count() == 1
    }

    void 'test the class\'s own calls inside a method annotated @Transactional with a connection use it'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        def service = new PlayerService()

        when:
        service.savePlayerInOne('Irwin')

        then:
        Player.one.count() == 2
        Player.count() == 1
        service.countPlayersInOne() == 2
    }

    void 'test a class not mapped to the connection keeps its own inside such a method'() {
        given:
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        new Coach(name: 'Ferguson').save(flush: true)

        expect:
        new PlayerService().countPlayersAndCoachesInOne() == [1, 1]
    }

    void 'test the class\'s own calls inside the transaction service of a named connection use it'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        Player.one.save(new Player(name: 'Irwin'), [flush: true])

        expect:
        datastore.getDatastoreForConnection('one').getService(TransactionService).withTransaction { Player.count() } == 2
        datastore.getService(TransactionService).withTransaction { Player.count() } == 1
    }

    void 'test the transaction service of the default datastore leaves an enclosing block alone'() {
        given:
        new Player(name: 'Giggs').save(flush: true)
        Player.one.save(new Player(name: 'Neville'), [flush: true])
        Player.one.save(new Player(name: 'Irwin'), [flush: true])

        expect: "the block's connection, as an unqualified @Transactional in the same place keeps"
        Player.one.withTransaction {
            datastore.getService(TransactionService).withTransaction { Player.count() }
        } == 2

        and: "while the service of a named connection's datastore routes to that connection"
        datastore.getDatastoreForConnection('one').getService(TransactionService).withTransaction { Player.count() } == 2
        datastore.getService(TransactionService).withTransaction { Player.count() } == 1
    }

    void 'test delete on data service'() {
        given:
        def dataService = datastore.getService(IPlayerService)

        when:
        dataService.savePlayer('Neville')

        then:
        Player.count() == 0
        dataService.countPlayers() == 1

        when:
        dataService.deletePlayer('Neville')

        then:
        dataService.countPlayers() == 0
    }
}

@Entity
class Player {

    String name

    static mapping = {
        datasources(ConnectionSource.DEFAULT, 'one')
    }
}

@Entity
class Coach {

    String name
}

@Transactional
class PlayerService {

    @Transactional('one')
    Number countPlayersOne() {
        // check the right datastore transaction is being used
        assert transactionStatus
                .transaction
                .sessionHolder
                .sessions
                .first()
                .datastore
                .backingMap[Player.name]
                .size() == 1
        Player.one.count()
    }

    @Transactional('one')
    Player savePlayerInOne(String name) {
        new Player(name: name).save(flush: true)
    }

    @Transactional('one')
    Number countPlayersInOne() {
        Player.count()
    }

    @Transactional('one')
    List<Number> countPlayersAndCoachesInOne() {
        [Player.count(), Coach.count()]
    }

    Number countPlayers() {
        assert !transactionStatus
                .transaction
                .sessionHolder
                .sessions
                .first()
                .datastore
                .backingMap[Player.name]
                .isEmpty()
        Player.count()
    }
}

@Service(Player)
@Transactional('one')
interface IPlayerService {

    Number countPlayers()

    Player savePlayer(String name)

    void deletePlayer(String name)
}