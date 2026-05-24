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
package org.grails.datastore.gorm.mongo.bugs

import grails.persistence.Entity
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.OptimisticLockingException

/**
 * Reproduces a decoder/encoder asymmetry in GORM MongoDB when a domain class
 * declares {@code String id} but the underlying {@code _id} is stored as a BSON
 * {@code ObjectId} (e.g. legacy data from when the domain used {@code ObjectId id}).
 *
 * <ul>
 *   <li>{@code IdentityDecoder} has a fallback: if the declared type is String
 *       but the BSON is ObjectId, it still decodes the document (via Spring's
 *       {@code ConversionService}). Scan-style reads work.</li>
 *   <li>{@code MongoQuery.IdEquals} and {@code IdentityEncoder} do <em>not</em>
 *       have this forgiveness: point lookups send {@code {_id: "hex"}} as a
 *       BSON String, which does not match {@code _id: ObjectId("hex")} in
 *       storage; updates write BSON String and miss for the same reason.</li>
 * </ul>
 *
 * The net effect is that a domain can silently "read" its legacy documents but
 * silently fail to {@code get(id)}, update, or delete them.
 */
class StringIdWithObjectIdStorageSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([LegacyVideo, ObjectIdVideo, StoredAsVideo, AssignedNonHexVideo, VersionedStoredAsVideo])
    }

    void "scan read decodes a BSON ObjectId _id into a String-typed id field"() {
        given:
        ObjectId legacyId = new ObjectId()
        rawCollection().insertOne(new Document('_id', legacyId).append('title', 'Legacy'))

        when:
        manager.session.clear()
        List<LegacyVideo> all = LegacyVideo.list()

        then: 'decoder fallback coerces ObjectId -> String'
        all.size() == 1
        all[0].id == legacyId.toHexString()
        all[0].title == 'Legacy'
    }

    void "point lookup by hex string returns null because the query sends BSON String"() {
        given:
        ObjectId legacyId = new ObjectId()
        rawCollection().insertOne(new Document('_id', legacyId).append('title', 'Legacy'))

        when:
        manager.session.clear()
        LegacyVideo found = LegacyVideo.get(legacyId.toHexString())

        then: 'MongoQuery.IdEquals builds {_id: "<hex>"} as BSON String, which does not match the ObjectId _id'
        found == null
    }

    void "update-through-read throws a misleading OptimisticLockingException because the update filter misses the legacy doc"() {
        given:
        ObjectId legacyId = new ObjectId()
        rawCollection().insertOne(new Document('_id', legacyId).append('title', 'Original'))

        when: 'load via scan (works), mutate, save'
        manager.session.clear()
        LegacyVideo v = LegacyVideo.list().first()
        v.title = 'Updated'
        v.save(flush: true)

        then: '''the update targets {_id: "<hex>"} as BSON String which matches zero docs;
                 GORM interprets the zero-match as a concurrency conflict and throws
                 OptimisticLockingException, even though nothing else touched the data.
                 This error message is misleading — the real cause is the id-type asymmetry.'''
        OptimisticLockingException ex = thrown()
        ex.message.contains('updated by another user')

        and: 'the legacy ObjectId-_id document is unchanged'
        manager.session.clear()
        Document raw = rawCollection().find(new Document('_id', legacyId)).first()
        raw.getString('title') == 'Original'
    }

    void "reflective JSON serialization of an ObjectId-id domain emits the id as a nested object, not a hex string"() {
        given: 'a domain with ObjectId id saved via GORM'
        ObjectIdVideo v = new ObjectIdVideo(title: 'Symposium').save(flush: true, failOnError: true)

        when: '''we serialize it the way a controller would when no ObjectId marshaller
                 is registered — reflective bean walk over every property, which is exactly
                 what Grails\' default JSON converter falls back to for unknown types'''
        String json = JsonOutput.toJson([id: v.id, title: v.title])
        Map parsed = new JsonSlurper().parseText(json) as Map

        then: '''the id field is an object with internal ObjectId fields (timestamp/date)
                 rather than the hex string — this is the root cause of client-side bugs
                 like data-video-id="[object Object]" when HTML datasets or URLs are built
                 from the parsed value'''
        parsed.id instanceof Map
        parsed.id.containsKey('timestamp')
        !(parsed.id instanceof String)

        and: 'compare to what a String id would serialize as — a plain hex string'
        LegacyVideo wouldBeFine = new LegacyVideo(title: 'OK').save(flush: true, failOnError: true)
        Map parsedString = new JsonSlurper().parseText(JsonOutput.toJson([id: wouldBeFine.id, title: wouldBeFine.title])) as Map
        parsedString.id instanceof String
        parsedString.id.length() == 24   // hex ObjectId generated by GORM for String-id domains
    }

    // ----------------------------------------------------------------------
    // Cases covering `id storedAs: ObjectId` — the feature that makes the
    // three failure modes above go away without requiring a data migration.
    // ----------------------------------------------------------------------

    void "with storedAs ObjectId, a String-id domain writes BSON ObjectId to _id"() {
        given:
        StoredAsVideo v = new StoredAsVideo(title: 'Symposium').save(flush: true, failOnError: true)
        String hex = v.id

        when: 'peek at the raw document via the driver'
        Document raw = storedAsRawCollection().find(new Document('_id', new ObjectId(hex))).first()

        then: '''_id is BSON ObjectId, not BSON String. Query by ObjectId finds it;
                 query by String would not.'''
        raw != null
        raw.get('_id') instanceof ObjectId
        raw.get('_id').toString() == hex
        storedAsRawCollection().find(new Document('_id', hex)).first() == null
    }

    void "with storedAs ObjectId, point lookup by hex string works"() {
        given:
        StoredAsVideo v = new StoredAsVideo(title: 'Symposium').save(flush: true, failOnError: true)
        String hex = v.id

        when:
        manager.session.clear()
        StoredAsVideo found = StoredAsVideo.get(hex)

        then: 'MongoQuery.IdEquals converts to the storedAs type, matches BSON ObjectId'
        found != null
        found.id == hex
        found.title == 'Symposium'
    }

    void "with storedAs ObjectId, updates persist (no phantom OptimisticLockingException)"() {
        given:
        StoredAsVideo v = new StoredAsVideo(title: 'Original').save(flush: true, failOnError: true)
        String hex = v.id

        when:
        manager.session.clear()
        StoredAsVideo reloaded = StoredAsVideo.get(hex)
        reloaded.title = 'Updated'
        reloaded.save(flush: true, failOnError: true)
        manager.session.clear()

        and:
        Document raw = storedAsRawCollection().find(new Document('_id', new ObjectId(hex))).first()

        then:
        raw != null
        raw.getString('title') == 'Updated'
    }

    void "with storedAs ObjectId AND versioning on, updates persist (proves the OptimisticLockingException path is genuinely fixed)"() {
        // The headline bug is a misleading OptimisticLockingException on save: when the
        // update filter sends the id as the wrong BSON type, zero documents match, and a
        // versioned domain interprets that as a concurrency conflict. StoredAsVideo
        // declares 'version false' so it can't actually exercise the OLE check — this
        // parallel domain keeps versioning on so the regression test fails (OLE thrown)
        // if the storedAs id-coercion is ever broken on the update path.
        given:
        VersionedStoredAsVideo v = new VersionedStoredAsVideo(title: 'Original').save(flush: true, failOnError: true)
        String hex = v.id
        Long initialVersion = v.version

        when:
        manager.session.clear()
        VersionedStoredAsVideo reloaded = VersionedStoredAsVideo.get(hex)
        reloaded.title = 'Updated'
        reloaded.save(flush: true, failOnError: true)
        manager.session.clear()

        and:
        Document raw = manager.mongoClient.getDatabase('test')
                .getCollection('versionedStoredAsVideo')
                .find(new Document('_id', new ObjectId(hex))).first()

        then: 'no OLE was thrown, the document was updated, and version incremented'
        raw != null
        raw.getString('title') == 'Updated'
        raw.getLong('version') == initialVersion + 1
    }

    void "with storedAs ObjectId, batch getAll resolves all ids (coerces each key in the in-list filter)"() {
        given:
        StoredAsVideo a = new StoredAsVideo(title: 'A').save(flush: true, failOnError: true)
        StoredAsVideo b = new StoredAsVideo(title: 'B').save(flush: true, failOnError: true)
        StoredAsVideo c = new StoredAsVideo(title: 'C').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        List<StoredAsVideo> found = StoredAsVideo.getAll([a.id, b.id, c.id])

        then: 'regression test for in-list handler: batch queries must coerce each key to BSON ObjectId'
        found.size() == 3
        found*.title.sort() == ['A', 'B', 'C']
    }

    void "with storedAs ObjectId, findAllByIdInList resolves all ids"() {
        given:
        StoredAsVideo a = new StoredAsVideo(title: 'A').save(flush: true, failOnError: true)
        StoredAsVideo b = new StoredAsVideo(title: 'B').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        List<StoredAsVideo> found = StoredAsVideo.findAllByIdInList([a.id, b.id])

        then: 'regression test: dynamic-finder in-list must coerce each id to BSON ObjectId'
        found.size() == 2
        found*.title.sort() == ['A', 'B']
    }

    void "with storedAs ObjectId, criteria in('id', [...]) resolves all ids"() {
        given:
        StoredAsVideo a = new StoredAsVideo(title: 'A').save(flush: true, failOnError: true)
        StoredAsVideo b = new StoredAsVideo(title: 'B').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        List<StoredAsVideo> found = StoredAsVideo.createCriteria().list {
            'in' 'id', [a.id, b.id]
        }

        then: 'regression test: criteria in-list on id must coerce to BSON ObjectId'
        found.size() == 2
        found*.title.sort() == ['A', 'B']
    }

    void "with storedAs ObjectId, encoding a non-hex id falls back to BSON String instead of throwing"() {
        given: '''a domain using an assigned natural key (not a valid ObjectId hex).
                 This is a misconfiguration — the user should not be combining storedAs: ObjectId
                 with natural keys — but the library should degrade predictably rather than
                 throwing IllegalArgumentException deep inside the BSON write pipeline.'''
        AssignedNonHexVideo v = new AssignedNonHexVideo(id: 'my-slug', title: 'Slug-Keyed').save(flush: true)

        expect: 'save did not throw; a doc was written with BSON String _id (the fallback path)'
        v != null
        !v.hasErrors()

        when:
        Document raw = manager.mongoClient.getDatabase('test').getCollection('assignedNonHexVideo')
                .find(new Document('_id', 'my-slug')).first()

        then:
        raw != null
        raw.getString('title') == 'Slug-Keyed'
    }

    void "with storedAs ObjectId, point lookup of a non-hex id matches the BSON String the encoder wrote"() {
        given: '''save path falls back to BSON String for non-hex; the read path must mirror
                 that fallback — otherwise the ConversionService returns null for invalid hex
                 and the query targets {_id: null}, stranding the document.'''
        new AssignedNonHexVideo(id: 'my-slug', title: 'Slug-Keyed').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        AssignedNonHexVideo found = AssignedNonHexVideo.get('my-slug')

        then: 'regression: String→ObjectId converter returns null for non-hex; IdEquals handler keeps original'
        found != null
        found.id == 'my-slug'
        found.title == 'Slug-Keyed'
    }

    void "with storedAs ObjectId, update of a non-hex id document lands on the right row"() {
        given:
        new AssignedNonHexVideo(id: 'my-slug', title: 'Original').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        AssignedNonHexVideo reloaded = AssignedNonHexVideo.get('my-slug')
        reloaded.title = 'Updated'
        reloaded.save(flush: true, failOnError: true)

        and:
        Document raw = manager.mongoClient.getDatabase('test').getCollection('assignedNonHexVideo')
                .find(new Document('_id', 'my-slug')).first()

        then: 'regression: without the null-return fallback the update filter targets {_id: null} and silently misses'
        raw != null
        raw.getString('title') == 'Updated'
    }

    void "with storedAs ObjectId, batch getAll with non-hex ids falls back to BSON String in the in-list"() {
        given:
        new AssignedNonHexVideo(id: 'slug-a', title: 'A').save(flush: true, failOnError: true)
        new AssignedNonHexVideo(id: 'slug-b', title: 'B').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        List<AssignedNonHexVideo> found = AssignedNonHexVideo.getAll(['slug-a', 'slug-b'])

        then: 'regression: In handler converts each value to ObjectId (returns null for non-hex); fallback keeps original'
        found.size() == 2
        found*.title.sort() == ['A', 'B']
    }

    void "with storedAs ObjectId, delete of a non-hex id document removes the row"() {
        given:
        new AssignedNonHexVideo(id: 'my-slug', title: 'Delete Me').save(flush: true, failOnError: true)

        when:
        manager.session.clear()
        AssignedNonHexVideo reloaded = AssignedNonHexVideo.get('my-slug')
        reloaded.delete(flush: true)

        and:
        Document raw = manager.mongoClient.getDatabase('test').getCollection('assignedNonHexVideo')
                .find(new Document('_id', 'my-slug')).first()

        then: 'regression: delete filter must target {_id: "my-slug"} (BSON String), not {_id: null}'
        raw == null
    }

    void "with storedAs ObjectId, legacy documents written directly as BSON ObjectId are fully accessible"() {
        given: 'a document inserted outside GORM with _id as BSON ObjectId (simulates legacy data)'
        ObjectId legacyId = new ObjectId()
        storedAsRawCollection().insertOne(new Document('_id', legacyId).append('title', 'Legacy'))

        when: 'point lookup'
        manager.session.clear()
        StoredAsVideo found = StoredAsVideo.get(legacyId.toHexString())

        then: 'works — no migration needed'
        found != null
        found.id == legacyId.toHexString()

        when: 'update'
        found.title = 'Updated'
        found.save(flush: true, failOnError: true)
        manager.session.clear()

        and:
        Document raw = storedAsRawCollection().find(new Document('_id', legacyId)).first()

        then: 'update lands on the legacy ObjectId-_id doc'
        raw.getString('title') == 'Updated'
    }

    private com.mongodb.client.MongoCollection<Document> rawCollection() {
        manager.mongoClient
                .getDatabase('test')
                .getCollection('legacyVideo')
    }

    private com.mongodb.client.MongoCollection<Document> storedAsRawCollection() {
        manager.mongoClient
                .getDatabase('test')
                .getCollection('storedAsVideo')
    }
}

@Entity
class LegacyVideo {
    String id
    String title
}

@Entity
class ObjectIdVideo {
    ObjectId id
    String title
}

@Entity
class StoredAsVideo {
    String id
    String title

    static mapping = {
        id storedAs: ObjectId
        version false  // orthogonal to storedAs; avoids version-field noise in raw-insert tests
    }
}

@Entity
class AssignedNonHexVideo {
    String id
    String title

    static mapping = {
        id generator: 'assigned', storedAs: ObjectId
        version false
    }
}

// Mirrors StoredAsVideo but keeps versioning on. The 'updates persist (no phantom
// OptimisticLockingException)' regression can only be genuinely exercised when a
// version field is present — without it, GORM never raises OLE regardless of
// whether the update filter matches.
@Entity
class VersionedStoredAsVideo {
    String id
    String title

    static mapping = {
        id storedAs: ObjectId
    }
}
