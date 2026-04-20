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
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore

class MultipleDataSourceSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [ConnectionSource.DEFAULT, 'one'],
            MultiDsPlayer
    )

    void 'test multiple datasource support with in-memory GORM'() {
        given:
        new MultiDsPlayer(name: 'Giggs').save(flush: true)
        new MultiDsPlayer(name: 'Keane').save(flush: true)
        def service = new PlayerService()
        def dataService = datastore.getService(IMultiDsPlayerService)
        dataService.savePlayer('Neville')

        expect:
        dataService.countPlayers() == 1
        MultiDsPlayer.count() == 2
        new DetachedCriteria<>(MultiDsPlayer).count() == 2
        new DetachedCriteria<>(MultiDsPlayer).withConnection('one').count() == 1
        MultiDsPlayer.one.count() == 1
        service.countPlayers() == 2
        service.countPlayersOne() == 1
    }

    void 'test delete on data service'() {
        given:
        def dataService = datastore.getService(IMultiDsPlayerService)

        when:
        dataService.savePlayer('Neville')

        then:
        MultiDsPlayer.count() == 0
        dataService.countPlayers() == 1

        when:
        dataService.deletePlayer('Neville')

        then:
        dataService.countPlayers() == 0
    }
}

@Entity
class MultiDsPlayer {

    String name

    static mapping = {
        datasources(ConnectionSource.DEFAULT, 'one')
    }
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
                .backingMap[MultiDsPlayer.name]
                .size() == 1
        MultiDsPlayer.one.count()
    }

    Number countPlayers() {
        assert !transactionStatus
                .transaction
                .sessionHolder
                .sessions
                .first()
                .datastore
                .backingMap[MultiDsPlayer.name]
                .isEmpty()
        MultiDsPlayer.count()
    }
}

@Service(MultiDsPlayer)
@Transactional('one')
interface IMultiDsPlayerService {

    Number countPlayers()
    MultiDsPlayer savePlayer(String name)
    void deletePlayer(String name)
}