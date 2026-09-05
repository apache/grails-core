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

import com.mongodb.client.MongoCollection
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.Session
import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.MongoSession
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * The non-codec ("mapping") engine must store and query {@code _id}, and write association
 * references, in the same type as the codec engine.
 *
 * <p>It reaches MongoDB through a different set of classes -- {@code MongoSession},
 * {@code MongoEntityPersister}, {@code AbstractMongoObectEntityPersister} -- each of which
 * previously used the *declared* identifier type. With a bare {@code String id} resolving to
 * {@code storedAs: ObjectId} that meant documents written with a String {@code _id} while the
 * shared {@code MongoQuery} looked them up as ObjectId, and association references that no
 * coerced criterion could match.
 *
 * <p>These cases drive the session API directly rather than the GORM statics, which bind to
 * whichever datastore registered the class last -- the codec one here. The first case guards
 * that: without it, a green run would prove nothing about this engine.
 */
class MappingEngineStringIdStorageSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    @Shared
    @AutoCleanup
    MongoDatastore mappingEngineDatastore

    void setupSpec() {
        manager.registerDomainClasses(MeVideo, MeOwner, MeAsset)
        mappingEngineDatastore = new MongoDatastore(
                manager.configuration + [(MongoSettings.SETTING_ENGINE): 'mapping'],
                MeVideo, MeOwner, MeAsset)
    }

    void "the datastore under test really is the mapping engine"() {
        expect: 'a codec session would prove nothing about any of the cases below'
        mappingEngineDatastore.connect().getClass().simpleName == 'MongoSession'
    }

    void "insert writes _id in the configured storage type"() {
        when:
        String hex = persist { Session s -> new MeVideo(title: 'Inserted') }

        then:
        hex ==~ /[0-9a-f]{24}/
        raw('meVideo').find(new Document('_id', new ObjectId(hex))).first()?.getString('title') == 'Inserted'
        raw('meVideo').find(new Document('_id', hex)).first() == null
    }

    void "a point read by id resolves"() {
        given:
        String hex = persist { Session s -> new MeVideo(title: 'Readable') }

        expect:
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            s.retrieve(MeVideo, hex)?.title
        } == 'Readable'
    }

    void "an update lands on the row, rather than silently missing it"() {
        given:
        String hex = persist { Session s -> new MeVideo(title: 'Before') }

        when: 'the flush-time update filter is built from the identifier'
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            MeVideo v = s.retrieve(MeVideo, hex)
            v.title = 'After'
            s.persist(v)
            s.flush()
        }

        then:
        raw('meVideo').find(new Document('_id', new ObjectId(hex))).first().getString('title') == 'After'
    }

    void "a delete removes the row, rather than leaving it behind"() {
        given:
        String hex = persist { Session s -> new MeVideo(title: 'Doomed') }

        when: 'the flush-time delete filter is built from the identifier'
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            s.delete(s.retrieve(MeVideo, hex))
            s.flush()
        }

        then:
        raw('meVideo').find(new Document('_id', new ObjectId(hex))).first() == null
    }

    void "an iterable delete removes every row"() {
        given: 'this path filters on the literal _id field, not the logical identity name'
        String a = persist { Session s -> new MeVideo(title: 'Batch A') }
        String b = persist { Session s -> new MeVideo(title: 'Batch B') }

        when:
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            s.delete([s.retrieve(MeVideo, a), s.retrieve(MeVideo, b)])
            s.flush()
        }

        then:
        raw('meVideo').find(new Document('_id', new ObjectId(a))).first() == null
        raw('meVideo').find(new Document('_id', new ObjectId(b))).first() == null
    }

    void "a to-one association reference is written in the target's stored _id type"() {
        given:
        String ownerId = persist { Session s -> new MeOwner(name: 'Owner') }
        String assetId = null
        mappingEngineDatastore.withSession { Session s ->
            MeAsset a = new MeAsset(label: 'Owned', owner: s.retrieve(MeOwner, ownerId))
            s.persist(a); s.flush(); assetId = a.id
        }

        when:
        Document rawAsset = raw('meAsset').find(new Document('_id', new ObjectId(assetId))).first()

        then: 'the reference is an ObjectId matching MeOwner._id, so a raw join resolves'
        rawAsset.get('owner') instanceof ObjectId
        rawAsset.get('owner') == new ObjectId(ownerId)
        raw('meOwner').find(new Document('_id', rawAsset.get('owner'))).first().getString('name') == 'Owner'
    }

    void "a to-one association still resolves through GORM"() {
        given:
        String ownerId = persist { Session s -> new MeOwner(name: 'Traversed') }
        String assetId = null
        mappingEngineDatastore.withSession { Session s ->
            MeAsset a = new MeAsset(label: 'Traversable', owner: s.retrieve(MeOwner, ownerId))
            s.persist(a); s.flush(); assetId = a.id
        }

        expect:
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            s.retrieve(MeAsset, assetId).owner?.name
        } == 'Traversed'
    }

    void "updateAll encodes a to-one association rather than writing the domain object"() {
        given:
        String fromId = persist { Session s -> new MeOwner(name: 'From') }
        String toId = persist { Session s -> new MeOwner(name: 'To') }
        String assetId = null
        mappingEngineDatastore.withSession { Session s ->
            MeAsset a = new MeAsset(label: 'Reassign', owner: s.retrieve(MeOwner, fromId))
            s.persist(a); s.flush(); assetId = a.id
        }

        when: 'called on the mapping session itself -- a GORM static would bind to the codec datastore'
        mappingEngineDatastore.withSession { Session s ->
            s.clear()
            DetachedCriteria criteria = new DetachedCriteria(MeAsset).build { eq 'label', 'Reassign' }
            ((MongoSession) s).updateAll(criteria, [owner: s.retrieve(MeOwner, toId)])
        }
        Document rawAsset = raw('meAsset').find(new Document('_id', new ObjectId(assetId))).first()

        then: 'the same representation normal persistence writes'
        rawAsset.get('owner') instanceof ObjectId
        rawAsset.get('owner') == new ObjectId(toId)
    }

    private String persist(Closure<?> make) {
        String id = null
        mappingEngineDatastore.withSession { Session s ->
            def o = make(s)
            s.persist(o)
            s.flush()
            id = o.id
        }
        id
    }

    private MongoCollection<Document> raw(String name) {
        manager.mongoClient.getDatabase('test').getCollection(name)
    }
}

@Entity
class MeVideo {
    String id
    String title
    static mapping = { version false }
}

@Entity
class MeOwner {
    String id
    String name
    static mapping = { version false }
}

@Entity
class MeAsset {
    String id
    String label
    MeOwner owner
    static mapping = { version false }
}
