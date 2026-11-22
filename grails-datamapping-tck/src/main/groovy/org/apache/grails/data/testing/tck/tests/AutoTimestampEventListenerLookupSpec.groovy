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

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.TimestampNote
import org.grails.datastore.gorm.events.AutoTimestampingDatastore

class AutoTimestampEventListenerLookupSpec extends GrailsDataTckSpec {

    private static final Date PAST = new Date(110_000_000_000L)

    void setupSpec() {
        manager.registerDomainClasses(TimestampNote)
    }

    void 'a domain class answers with the listener of its datastore'() {
        expect:
        manager.session.datastore instanceof AutoTimestampingDatastore
        TimestampNote.autoTimestampEventListener != null
        TimestampNote.autoTimestampEventListener.is(((AutoTimestampingDatastore) manager.session.datastore).autoTimestampEventListener)
    }

    void 'the listener of a domain class turns its timestamps off'() {
        given:
        TimestampNote kept = new TimestampNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)
        TimestampNote stamped = new TimestampNote(text: 'stamped', dateCreated: PAST, lastUpdated: PAST)

        when:
        TimestampNote.autoTimestampEventListener.withoutTimestamps {
            kept.save(flush: true)
        }
        stamped.save(flush: true)
        manager.session.clear()

        then:
        TimestampNote.get(kept.id).dateCreated == PAST
        TimestampNote.get(kept.id).lastUpdated == PAST
        TimestampNote.get(stamped.id).dateCreated != PAST
        TimestampNote.get(stamped.id).lastUpdated != PAST
    }
}
