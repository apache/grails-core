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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

/**
 * MongoDB relies exclusively on interception-based dirty checking (there is no flush-time
 * snapshot comparison), so mutations that escape the DirtyChecking* wrappers are silently
 * lost: save() sees a clean entity and writes nothing. Two real-world escapes are covered
 * here end-to-end:
 *
 * <ul>
 * <li>The defensive re-init {@code if (!entity.shares) entity.shares = []} — true for an
 *     EMPTY tracked list (Groovy falsy) — used to replace the tracked wrapper installed at
 *     decode time with a plain untracked list, so the add() that followed never persisted.</li>
 * <li>Groovy's {@code removeAll(Closure)} removes through {@code iterator().remove()}, which
 *     used to bypass the wrapper's change tracking entirely.</li>
 * </ul>
 */
class EmbeddedCollectionDirtyTrackingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(SharedCalendar, CalendarShare)
    }

    void "in-place add after a falsy empty re-init is persisted"() {
        given: "a persisted entity with an empty embedded collection"
        SharedCalendar calendar = new SharedCalendar(name: "My Gym", shares: []).save(flush: true, validate: false)
        manager.session.clear()

        when: "the entity is loaded, defensively re-initialised and mutated in place"
        calendar = SharedCalendar.get(calendar.id)
        // A full application activates change tracking on load (DomainEventListener
        // .activateDirtyChecking); without it hasChanged() defaults to true and every save
        // writes unconditionally, which would mask the bug this spec guards against.
        calendar.trackChanges()
        if (!calendar.shares) {
            calendar.shares = []
        }
        calendar.shares.add(new CalendarShare(userId: "user-1", role: "VIEWER"))
        calendar.save(flush: true)
        manager.session.clear()
        calendar = SharedCalendar.get(calendar.id)

        then: "the added element was persisted"
        calendar.shares.size() == 1
        calendar.shares[0].userId == "user-1"
        calendar.shares[0].role == "VIEWER"
    }

    /**
     * Regression guard: pre-fix this passed only by luck — the wrapper missed the iterator
     * removal, but the Mongo persister's size-change net (DirtyCheckableCollection
     * .hasChangedSize()) rescued the still-wrapped collection. The wrapper-level bypass is
     * proven by DirtyCheckingCollectionSpec; this pins the end-to-end outcome either way.
     */
    void "closure-based removeAll on a loaded embedded collection is persisted"() {
        given: "a persisted entity with two embedded elements"
        SharedCalendar calendar = new SharedCalendar(name: "Work", shares: [
                new CalendarShare(userId: "user-1", role: "VIEWER"),
                new CalendarShare(userId: "user-2", role: "EDITOR")
        ]).save(flush: true, validate: false)
        manager.session.clear()

        when: "an element is removed via the Groovy closure variant (iterator-based)"
        calendar = SharedCalendar.get(calendar.id)
        calendar.trackChanges() // see above — mirror a full application's on-load activation
        calendar.shares.removeAll { it.userId == "user-1" }
        calendar.save(flush: true)
        manager.session.clear()
        calendar = SharedCalendar.get(calendar.id)

        then: "the removal was persisted"
        calendar.shares.size() == 1
        calendar.shares[0].userId == "user-2"
    }
}

@Entity
class SharedCalendar {
    ObjectId id
    String name
    List<CalendarShare> shares
    // Auto-timestamped like real-world domains. The PreUpdate timestamp write destroys the
    // whole-class dirty marker that an explicit save() sets (markDirty(String) resets the
    // marker map), so the update only contains individually-tracked properties — making the
    // persisted outcome depend entirely on the collection wrappers this spec exercises.
    Date lastUpdated

    static mapWith = "mongo"
    static mapping = {
        collection "sharedCalendar"
    }
    static constraints = {
        shares(blank: true, nullable: true)
    }
    static embedded = ['shares']
}

@Entity
class CalendarShare {
    ObjectId id
    String userId
    String role

    static mapWith = "mongo"
    static constraints = {
    }
}
