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
import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
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
    CapturedLog log

    @Shared
    List<String> startupMessages

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        // Capture both inherited and MongoDatastore-specific logging categories.
        log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)

        datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl(DATABASE)] as Map,
                SummaryLoggedThing, OtherSummaryLoggedThing, UnindexedSummaryThing)

        // Snapshotted so that a feature triggering another build cannot change what the startup build said
        startupMessages = messagesForThisDatabase()
    }

    void cleanupSpec() {
        log?.close()
    }

    /**
     * Other specifications create datastores of their own in this JVM, so the summaries are picked out by
     * the database this specification uses rather than by being the only messages logged.
     */
    private List<String> messagesForThisDatabase() {
        log.events.collect { it.formattedMessage }.findAll { it.contains("database [$DATABASE]") }
    }

    void "test a successful index build logs one summary of what it applied and what it cost"() {
        given:
        String summary = startupMessages.first()

        expect: "exactly one summary for the build, not one line per index"
        startupMessages.size() == 1

        and: "a duplicate declaration confirms the index just created instead of counting it twice"
        summary.contains('3 created, 1 already present')

        and: "the class count includes a class that declares no indexes"
        summary.contains('from 3 domain class(es)')

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
        since.first().contains('0 created, 4 already present')
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
        compoundIndex name: 1
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

@Entity
class UnindexedSummaryThing {
    String name
}
