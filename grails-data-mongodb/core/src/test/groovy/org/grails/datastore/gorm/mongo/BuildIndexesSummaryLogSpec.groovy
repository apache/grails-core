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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import grails.gorm.annotation.Entity
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * An index build that succeeds says so once, at the end: what it created, what was already there, how many
 * domain classes it covered, and how long the caller spent waiting. That summary is the only signal a
 * background build has finished at all, and the created/already-present split is what makes the elapsed
 * time interpretable — a build that created nothing had nothing to wait for.
 */
class BuildIndexesSummaryLogSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'summaryLogDb'

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    Logger datastoreLogger

    @Shared
    ListAppender<ILoggingEvent> logged = new ListAppender<>()

    @Shared
    Level previousLevel

    @Shared
    List<String> startupMessages

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        // The datastore logs through the logger its base class declares
        datastoreLogger = LoggerFactory.getLogger(AbstractDatastore) as Logger
        previousLevel = datastoreLogger.level
        datastoreLogger.level = Level.INFO
        logged.start()
        datastoreLogger.addAppender(logged)

        datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl(DATABASE)] as Map,
                SummaryLoggedThing, OtherSummaryLoggedThing)

        // Snapshotted so that a feature triggering another build cannot change what the startup build said
        startupMessages = messagesForThisDatabase()
    }

    void cleanupSpec() {
        datastoreLogger?.detachAppender(logged)
        datastoreLogger?.level = previousLevel
    }

    /**
     * Other specifications create datastores of their own in this JVM, so the summaries are picked out by
     * the database this specification uses rather than by being the only messages logged.
     */
    private List<String> messagesForThisDatabase() {
        logged.list.collect { it.formattedMessage }.findAll { it.contains("database [$DATABASE]") }
    }

    void "test a successful index build logs one summary of what it applied and what it cost"() {
        given:
        String summary = startupMessages.first()

        expect: "exactly one summary for the build, not one line per index"
        startupMessages.size() == 1

        and: "it counted every declared index as created: two on one domain class, one on the other"
        summary.contains('3 created, 0 already present')

        and: "and the domain classes they came from"
        summary.contains('from 2 domain class(es)')

        and: "and how long the caller waited"
        summary ==~ /Index build for database \[$DATABASE] finished in \d+ms: .*/
    }

    void "test a repeated build reports the indexes as already present rather than created"() {
        given: "the summaries logged so far"
        int before = messagesForThisDatabase().size()

        when: "the same declarations are applied again, as they would be on the next restart"
        datastore.buildIndex()

        then: "the build reports that it created nothing, which is why it cost next to nothing"
        List<String> since = messagesForThisDatabase().drop(before)
        since.size() == 1
        since.first().contains('0 created, 3 already present')
    }
}

@Entity
class SummaryLoggedThing {
    String name
    Integer age

    static mapping = {
        version false
        collection 'summaryLoggedThing'
        name index: true
        compoundIndex name: 1, age: -1
    }
}

@Entity
class OtherSummaryLoggedThing {
    String title

    static mapping = {
        version false
        collection 'otherSummaryLoggedThing'
        title index: true
    }
}
