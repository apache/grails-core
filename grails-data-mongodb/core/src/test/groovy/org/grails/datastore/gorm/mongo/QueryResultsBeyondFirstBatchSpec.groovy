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

import com.mongodb.client.MongoCursor
import grails.gorm.annotation.Entity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCreationEvent
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import spock.lang.Shared

/**
 * MongoDB returns the first 101 documents with a query and the rest in later batches, which the
 * cursor fetches as the results are read. A query run with no session bound opens a session for
 * itself and closes it when the query returns, before any later batch arrives, and decoding an
 * association needs a session.
 */
class QueryResultsBeyondFirstBatchSpec extends AutoStartedMongoSpec {

    @Shared
    MongoDatastore datastore

    @Shared
    int sessionsOpened

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl('queryResultsBeyondFirstBatch')] as Map,
                BatchTeam, BatchPlayer, BatchClub)
        datastore.start()
        datastore.applicationEventPublisher.addApplicationListener({ ApplicationEvent event ->
            if (event instanceof SessionCreationEvent) {
                sessionsOpened++
            }
        } as ApplicationListener)
        BatchPlayer.withNewSession { Session session ->
            BatchTeam team = new BatchTeam(name: 'Rovers').save()
            102.times { int i ->
                BatchPlayer player = new BatchPlayer(name: "Player $i", team: team).save()
                new BatchClub(name: "Club $i").addToMembers(player).save()
            }
            session.flush()
        }
    }

    void cleanupSpec() {
        datastore?.close()
    }

    void "the results beyond the first batch are read with no session bound"() {
        expect:
        !datastore.hasCurrentSession()
        BatchPlayer.list().size() == 102
    }

    void "the associations of the results beyond the first batch load as the first batch's do"() {
        expect:
        BatchPlayer.list().every { BatchPlayer player -> player.team.name == 'Rovers' }
    }

    void "the results beyond the first batch with a one-to-many association are read with no session bound"() {
        expect:
        BatchClub.list().size() == 102
    }

    void "the results of an aggregation beyond the first batch are read with no session bound"() {
        expect:
        BatchPlayer.aggregate([['$match': [:]]]).size() == 102
    }

    void "tryNext reads the results beyond the first batch with no session bound"() {
        when:
        MongoCursor cursor = ((MongoQuery.MongoResultList) BatchPlayer.list()).cursor
        int read = 0
        while (cursor.tryNext() != null) {
            read++
        }

        then:
        read == 102
    }

    void "the results beyond the first batch are read on a thread with no session"() {
        when:
        def read = null
        Thread.start {
            try {
                read = BatchPlayer.list().size()
            } catch (Throwable e) {
                read = e
            }
        }.join()

        then:
        read == 102
    }

    void "reading results that fit in the first batch opens no session"() {
        when:
        List<BatchTeam> teams = BatchTeam.list()

        then:
        sessionsOpenedReading(teams) == 0
    }

    void "reading the results beyond the first batch opens one session, for the later batch"() {
        when:
        List<BatchPlayer> players = BatchPlayer.list()

        then:
        sessionsOpenedReading(players) == 1
    }

    void "the results beyond the first batch are read through the bound session"() {
        when:
        List read = BatchPlayer.withNewSession { Session session ->
            List<BatchPlayer> players = BatchPlayer.list()
            [players.size(), session.contains(players.last())]
        }

        then:
        read == [102, true]
    }

    private int sessionsOpenedReading(List results) {
        int before = sessionsOpened
        results.size()
        sessionsOpened - before
    }
}

@Entity
class BatchTeam {
    String name
}

@Entity
class BatchPlayer {
    String name
    BatchTeam team
}

@Entity
class BatchClub {
    String name
    static hasMany = [members: BatchPlayer]
}
