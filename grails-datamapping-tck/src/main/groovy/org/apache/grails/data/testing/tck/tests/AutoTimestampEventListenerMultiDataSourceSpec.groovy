/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import spock.lang.Issue
import spock.lang.Requires

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.AllConnectionsTimestampNote
import org.apache.grails.data.testing.tck.domains.DefaultConnectionTimestampNote
import org.apache.grails.data.testing.tck.domains.SecondaryConnectionTimestampNote
import org.apache.grails.data.testing.tck.domains.TimestampNoteConnectionService

/**
 * Every connection has an auto-timestamp listener of its own, and a domain class answers with the listener of the
 * connection its saves go through. Which connection that is for the unqualified operations of a class mapped only to
 * another connection is the datastore's own: a datastore with a single session, as the in-memory one of unit tests,
 * keeps them on the default connection.
 */
@Issue('https://github.com/apache/grails-core/issues/14509')
@Requires({ instance.manager?.supportsMultipleDataSources() })
class AutoTimestampEventListenerMultiDataSourceSpec extends GrailsDataTckSpec {

    private static final Date PAST = new Date(110_000_000_000L)

    void setup() {
        manager.setupMultiDataSource(DefaultConnectionTimestampNote, SecondaryConnectionTimestampNote, AllConnectionsTimestampNote)
    }

    void cleanup() {
        manager.cleanupMultiDataSource()
    }

    void 'each connection answers with a listener of its own'() {
        given:
        def defaultListener = AllConnectionsTimestampNote.autoTimestampEventListener
        def secondaryListener = AllConnectionsTimestampNote.secondary.autoTimestampEventListener

        expect:
        !defaultListener.is(secondaryListener)
        DefaultConnectionTimestampNote.autoTimestampEventListener.is(defaultListener)
        AllConnectionsTimestampNote.secondary.withTransaction { AllConnectionsTimestampNote.autoTimestampEventListener }.is(secondaryListener)
    }

    void 'a method transactional on a connection answers with that connection\'s listener'() {
        given:
        TimestampNoteConnectionService service = manager.getServiceForConnection(TimestampNoteConnectionService, 'secondary')
        def secondaryListener = AllConnectionsTimestampNote.secondary.autoTimestampEventListener

        when:
        AllConnectionsTimestampNote note = service.saveWithoutTimestamps('transactional', PAST)

        then:
        service.listener().is(secondaryListener)
        !service.listener().is(AllConnectionsTimestampNote.autoTimestampEventListener)
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).dateCreated } == PAST
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).lastUpdated } == PAST
    }

    void 'the listener of each domain class turns off the timestamps of its own saves'() {
        given:
        DefaultConnectionTimestampNote car = new DefaultConnectionTimestampNote(text: 'car', dateCreated: PAST, lastUpdated: PAST)
        SecondaryConnectionTimestampNote person = new SecondaryConnectionTimestampNote(text: 'person', dateCreated: PAST, lastUpdated: PAST)

        when:
        DefaultConnectionTimestampNote.withNewTransaction {
            DefaultConnectionTimestampNote.autoTimestampEventListener.withoutTimestamps {
                car.save(flush: true)
            }
        }
        SecondaryConnectionTimestampNote.withNewTransaction {
            SecondaryConnectionTimestampNote.autoTimestampEventListener.withoutTimestamps {
                person.save(flush: true)
            }
        }

        then:
        DefaultConnectionTimestampNote.withNewTransaction { DefaultConnectionTimestampNote.get(car.id).dateCreated } == PAST
        DefaultConnectionTimestampNote.withNewTransaction { DefaultConnectionTimestampNote.get(car.id).lastUpdated } == PAST
        SecondaryConnectionTimestampNote.withNewTransaction { SecondaryConnectionTimestampNote.get(person.id).dateCreated } == PAST
        SecondaryConnectionTimestampNote.withNewTransaction { SecondaryConnectionTimestampNote.get(person.id).lastUpdated } == PAST
    }

    void 'the listener of one connection does not reach a save through another'() {
        given:
        def defaultListener = AllConnectionsTimestampNote.autoTimestampEventListener
        AllConnectionsTimestampNote note = new AllConnectionsTimestampNote(text: 'shared', dateCreated: PAST, lastUpdated: PAST)

        when:
        AllConnectionsTimestampNote.secondary.withNewTransaction {
            defaultListener.withoutTimestamps {
                note.secondary.save(flush: true)
            }
        }

        then:
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).dateCreated } != PAST
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).lastUpdated } != PAST
    }

    void 'a domain class on several connections turns off the timestamps of the connection it names'() {
        given:
        AllConnectionsTimestampNote note = new AllConnectionsTimestampNote(text: 'shared', dateCreated: PAST, lastUpdated: PAST)

        when:
        AllConnectionsTimestampNote.secondary.withNewTransaction {
            AllConnectionsTimestampNote.secondary.autoTimestampEventListener.withoutTimestamps {
                note.secondary.save(flush: true)
            }
        }

        then:
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).dateCreated } == PAST
        AllConnectionsTimestampNote.secondary.withNewTransaction { AllConnectionsTimestampNote.secondary.get(note.id).lastUpdated } == PAST
    }
}
