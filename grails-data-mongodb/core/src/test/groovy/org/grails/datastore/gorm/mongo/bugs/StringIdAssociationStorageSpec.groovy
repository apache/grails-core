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

import com.mongodb.DBRef
import com.mongodb.client.MongoCollection
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId

/**
 * Association identifiers must be written in the type the target's {@code _id} is stored as.
 *
 * <p>Before this was fixed the encoders wrote a reference using the id's *declared* type, so a
 * {@code String id} domain whose {@code _id} is a BSON ObjectId was pointed at by a BSON String.
 * GORM traversal still worked -- the decoder coerces on read -- so the mismatch was invisible
 * from application code while {@code $lookup}, the raw driver and any external client silently
 * matched nothing.
 *
 * <p>These cases assert the BSON that actually lands on disk, not just that traversal works,
 * because traversal passed even when the storage was wrong.
 */
class StringIdAssociationStorageSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(RefProject, RefTicket, RefTag, RefPerson, RefNote, RefBiParent, RefBiChild, RefFace, RefNose, RefLinkedProject, RefRole, RefRight)
    }

    void "a to-one reference is written as the target's stored _id type"() {
        given:
        RefProject project = new RefProject(name: 'Apollo').save(flush: true)
        RefTicket ticket = new RefTicket(title: 'Boot failure', project: project).save(flush: true)

        when:
        manager.session.clear()
        Document raw = rawTickets().find(new Document('_id', new ObjectId(ticket.id))).first()

        then: 'the reference is an ObjectId, matching RefProject._id'
        raw.get('project') instanceof ObjectId
        raw.get('project') == new ObjectId(project.id)

        and: 'so a raw driver join resolves, which is what was broken'
        rawProjects().find(new Document('_id', raw.get('project'))).first().getString('name') == 'Apollo'
    }

    void "a to-one reference still resolves through GORM"() {
        given:
        RefProject project = new RefProject(name: 'Gemini').save(flush: true)
        String ticketId = new RefTicket(title: 'Telemetry', project: project).save(flush: true).id

        when:
        manager.session.clear()
        RefTicket loaded = RefTicket.get(ticketId)

        then: 'the decoder hands back the declared String type'
        loaded.project.id == project.id
        loaded.project.id instanceof String
        loaded.project.name == 'Gemini'
    }

    void "a to-many reference array is written as the target's stored _id type"() {
        given:
        RefTag a = new RefTag(label: 'urgent').save(flush: true)
        RefTag b = new RefTag(label: 'backend').save(flush: true)
        RefProject project = new RefProject(name: 'Mercury')
        project.tags = [a, b]
        project.save(flush: true)

        when:
        manager.session.clear()
        Document raw = rawProjects().find(new Document('_id', new ObjectId(project.id))).first()

        then:
        raw.get('tags').every { it instanceof ObjectId }
        raw.get('tags') as Set == [new ObjectId(a.id), new ObjectId(b.id)] as Set
    }

    void "a to-many collection still resolves through GORM"() {
        given:
        RefTag a = new RefTag(label: 'ui').save(flush: true)
        RefProject project = new RefProject(name: 'Vostok')
        project.tags = [a]
        project.save(flush: true)

        when:
        manager.session.clear()
        RefProject loaded = RefProject.get(project.id)

        then:
        loaded.tags.size() == 1
        loaded.tags.first().label == 'ui'
    }

    void "a bidirectional one-to-many resolves, because the foreign-key filter is coerced too"() {
        given: 'the inverse side is fetched by querying tickets whose project is this id'
        RefProject project = new RefProject(name: 'Soyuz').save(flush: true)
        new RefTicket(title: 'One', project: project).save(flush: true)
        new RefTicket(title: 'Two', project: project).save(flush: true)

        when:
        manager.session.clear()
        List<RefTicket> found = RefTicket.findAllByProject(RefProject.get(project.id))

        then: 'without coercion this sent a hex String against an ObjectId FK and returned nothing'
        found.size() == 2
        found*.title as Set == ['One', 'Two'] as Set
    }

    void "findAllById resolves, having previously bypassed id coercion entirely"() {
        given: "a dynamic finder builds Equals('id', ..) rather than IdEquals"
        String id = new RefProject(name: 'Voskhod').save(flush: true).id

        when:
        manager.session.clear()
        List<RefProject> found = RefProject.findAllById(id)

        then:
        found.size() == 1
        found[0].name == 'Voskhod'
    }

    void "findAllByIdInList resolves the same ids"() {
        given:
        String one = new RefProject(name: 'Zond').save(flush: true).id
        String two = new RefProject(name: 'Luna').save(flush: true).id

        when:
        manager.session.clear()
        List<RefProject> found = RefProject.findAllByIdInList([one, two])

        then:
        found.size() == 2
    }

    void "a reference stored as a BSON String is still decoded, so pre-existing data keeps working"() {
        given: 'a document written before the storage type changed'
        RefProject project = new RefProject(name: 'Apollo 13').save(flush: true)
        ObjectId legacyTicketId = new ObjectId()
        rawTickets().insertOne(
                new Document('_id', legacyTicketId)
                        .append('title', 'Legacy row')
                        .append('project', project.id))   // hex String, the old format

        when:
        manager.session.clear()
        RefTicket loaded = RefTicket.get(legacyTicketId.toHexString())

        then: 'the decoder reads whatever BSON type is present rather than the predicted one'
        loaded.title == 'Legacy row'
        loaded.project.id == project.id
        loaded.project.name == 'Apollo 13'
    }

    void "updateAll writes a to-one reference in the target's stored _id type"() {
        given: 'the bulk update path builds its own $set document rather than going through the codec'
        RefProject from = new RefProject(name: 'Old owner').save(flush: true)
        RefProject to = new RefProject(name: 'New owner').save(flush: true)
        RefTicket ticket = new RefTicket(title: 'Reassign me', project: from).save(flush: true)

        when:
        manager.session.clear()
        RefTicket.where { title == 'Reassign me' }.updateAll(project: RefProject.get(to.id))
        Document raw = rawTickets().find(new Document('_id', new ObjectId(ticket.id))).first()

        then: 'the same representation normal persistence writes, not the declared String'
        raw.get('project') instanceof ObjectId
        raw.get('project') == new ObjectId(to.id)

        and: 'so the relationship is still queryable afterwards'
        manager.session.clear()
        RefTicket.get(ticket.id).project.name == 'New owner'
    }

    void "an id criterion nested in a not block is coerced"() {
        given: 'the inherited negation handler dispatches nested criteria itself, bypassing preprocessing'
        String keepId = new RefProject(name: 'Keep').save(flush: true).id
        String dropId = new RefProject(name: 'Drop').save(flush: true).id

        when:
        manager.session.clear()
        List<RefProject> found = RefProject.where { not { eq 'id', dropId } }.list()

        then: 'uncoerced, the nested String predicate excludes nothing and Drop comes back'
        found*.id.contains(keepId)
        !found*.id.contains(dropId)
    }

    void "findAllByIdNot excludes the document it names"() {
        given:
        String keepId = new RefProject(name: 'Retained').save(flush: true).id
        String dropId = new RefProject(name: 'Excluded').save(flush: true).id

        when:
        manager.session.clear()
        List<RefProject> found = RefProject.findAllByIdNot(dropId)

        then:
        found*.id.contains(keepId)
        !found*.id.contains(dropId)
    }

    void "a to-one association criterion nested in a not block is coerced"() {
        given:
        RefProject a = new RefProject(name: 'Alpha').save(flush: true)
        RefProject b = new RefProject(name: 'Beta').save(flush: true)
        new RefTicket(title: 'in alpha', project: a).save(flush: true)
        new RefTicket(title: 'in beta', project: b).save(flush: true)

        when:
        manager.session.clear()
        List<RefTicket> found = RefTicket.where { not { eq 'project', RefProject.get(a.id) } }.list()

        then:
        found*.title == ['in beta']
    }

    void "an IN criterion on a to-one association coerces each unwrapped id"() {
        given: 'getInListQueryValues unwraps instances to their declared id'
        RefProject a = new RefProject(name: 'In-A').save(flush: true)
        RefProject b = new RefProject(name: 'In-B').save(flush: true)
        RefProject c = new RefProject(name: 'In-C').save(flush: true)
        new RefTicket(title: 'ta', project: a).save(flush: true)
        new RefTicket(title: 'tb', project: b).save(flush: true)
        new RefTicket(title: 'tc', project: c).save(flush: true)

        when:
        manager.session.clear()
        List<RefTicket> found = RefTicket.where {
            project in [RefProject.get(a.id), RefProject.get(b.id)]
        }.list()

        then: 'without coercion these went as hex Strings against ObjectId foreign keys'
        found*.title as Set == ['ta', 'tb'] as Set
    }

    void "findAllByProjectInList resolves association ids"() {
        given:
        RefProject a = new RefProject(name: 'List-A').save(flush: true)
        RefProject b = new RefProject(name: 'List-B').save(flush: true)
        new RefTicket(title: 'la', project: a).save(flush: true)
        new RefTicket(title: 'lb', project: b).save(flush: true)

        when:
        manager.session.clear()
        List<RefTicket> found = RefTicket.findAllByProjectInList([RefProject.get(a.id)])

        then:
        found*.title == ['la']
    }

    void "updateAll does not mutate the caller's property map"() {
        given:
        RefProject to = new RefProject(name: 'Target').save(flush: true)
        RefTicket ticket = new RefTicket(title: 'Keep my map', project: to).save(flush: true)
        RefProject arg = RefProject.get(to.id)
        Map<String, Object> updates = [project: arg]

        when:
        manager.session.clear()
        RefTicket.where { title == 'Keep my map' }.updateAll(updates)

        then: 'the caller still holds their domain object, not an ObjectId or DBRef'
        updates.project.is(arg)
    }

    void "updateAll accepts an immutable property map"() {
        given:
        RefProject to = new RefProject(name: 'Immutable target').save(flush: true)
        new RefTicket(title: 'Immutable arg', project: to).save(flush: true)

        when:
        manager.session.clear()
        RefTicket.where { title == 'Immutable arg' }
                .updateAll(Collections.singletonMap('project', RefProject.get(to.id)))

        then: 'normalising into a copy means an unmodifiable argument is fine'
        noExceptionThrown()
    }

    void "updateAll extracts the id from a lazy proxy"() {
        given: 'a proxy keeps its id in the proxy handler, not in the reflected field'
        RefProject to = new RefProject(name: 'Proxy target').save(flush: true)
        RefTicket ticket = new RefTicket(title: 'Proxy update', project: to).save(flush: true)
        RefProject other = new RefProject(name: 'Other').save(flush: true)
        RefTicket.where { title == 'Proxy update' }.updateAll(project: other)

        when:
        manager.session.clear()
        RefTicket.where { title == 'Proxy update' }.updateAll(project: RefProject.load(to.id))
        Document raw = rawTickets().find(new Document('_id', new ObjectId(ticket.id))).first()

        then: 'reflecting the proxy would have yielded null'
        raw.get('project') == new ObjectId(to.id)
    }

    void "updateAll writes an embedded association as a subdocument, not an id"() {
        given: 'Embedded extends ToOne, so id-reference normalization must skip it'
        RefPerson p = new RefPerson(name: 'Embedded owner', address: new RefAddress(city: 'before'))
        p.save(flush: true)

        when:
        manager.session.clear()
        RefPerson.where { name == 'Embedded owner' }.updateAll(address: new RefAddress(city: 'after'))
        Document raw = rawPeople().find(new Document('_id', new ObjectId(p.id))).first()

        then: 'a subdocument, not a scalar id or null'
        raw.get('address') instanceof Document
        raw.get('address').getString('city') == 'after'
    }

    void "updateAll encodes a to-many association as stored ids"() {
        given: 'normal persistence stores ids; the bulk path used to send the domain objects'
        RefTag a = new RefTag(label: 'bulk-a').save(flush: true)
        RefTag b = new RefTag(label: 'bulk-b').save(flush: true)
        RefProject project = new RefProject(name: 'Bulk tags')
        project.save(flush: true)

        when:
        manager.session.clear()
        RefProject.where { name == 'Bulk tags' }
                .updateAll(tags: [RefTag.get(a.id), RefTag.get(b.id)])
        Document raw = rawProjects().find(new Document('_id', new ObjectId(project.id))).first()

        then:
        raw.get('tags').every { it instanceof ObjectId }
        raw.get('tags') as Set == [new ObjectId(a.id), new ObjectId(b.id)] as Set
    }

    void "updateAll on a basic collection passes the values through untouched"() {
        given: 'Basic extends ToMany but has no associated entity -- this must not enter the id branch'
        RefNote note = new RefNote(title: 'note', labels: ['x']).save(flush: true)

        when:
        manager.session.clear()
        RefNote.where { title == 'note' }.updateAll(labels: ['y', 'z'])
        Document raw = rawNotes().find(new Document('_id', new ObjectId(note.id))).first()

        then: 'the String list lands as-is, exactly as it did before this branch existed'
        notThrown(NullPointerException)
        raw.get('labels') == ['y', 'z']
    }

    void "updateAll rejects a bidirectional one-to-many instead of corrupting the owner"() {
        given: 'the foreign key lives on the inverse side, so there is no field here to update'
        RefBiParent parent = new RefBiParent(name: 'bi-parent').save(flush: true)
        RefBiChild child = new RefBiChild(name: 'kid', parent: parent).save(flush: true)

        when:
        manager.session.clear()
        RefBiParent.where { name == 'bi-parent' }.updateAll(children: [RefBiChild.get(child.id)])

        then: 'named clearly, rather than writing raw subdocuments the decoder cannot read'
        UnsupportedOperationException e = thrown()
        e.message.contains('children')

        and: 'nothing was written, so the owner still decodes'
        rawBiParents().find(new Document('_id', new ObjectId(parent.id))).first().get('children') == null

        and:
        manager.session.clear()
        RefBiParent.get(parent.id).name == 'bi-parent'
    }

    void "updateAll rejects a hasOne association"() {
        given: 'hasOne keeps the foreign key on the child, so the owner carries no field for it'
        RefFace face = new RefFace(name: 'face').save(flush: true)
        RefNose nose = new RefNose(size: 'big', face: face).save(flush: true)

        when:
        manager.session.clear()
        RefFace.where { name == 'face' }.updateAll(nose: RefNose.get(nose.id))

        then: 'ToOneEncoder writes nothing on the owner for this; neither should updateAll'
        UnsupportedOperationException e = thrown()
        e.message.contains('nose')

        and: 'no id field on a document that never carries one'
        rawFaces().find(new Document('_id', new ObjectId(face.id))).first().get('nose') == null
    }

    void "updateAll keeps nulls in a plain to-many id list, as the encoder does"() {
        given: 'OneToManyEncoder drops nulls only when wrapping DBRefs; a plain id list keeps them'
        RefTag a = new RefTag(label: 'keep-me').save(flush: true)
        RefProject project = new RefProject(name: 'Null tags').save(flush: true)

        when:
        manager.session.clear()
        RefProject.where { name == 'Null tags' }.updateAll(tags: [RefTag.get(a.id), null])
        Document raw = rawProjects().find(new Document('_id', new ObjectId(project.id))).first()

        then: 'a plain id list keeps what the caller passed, as the encoder does'
        raw.get('tags') == [new ObjectId(a.id), null]
    }

    void "updateAll accepts an identifier in place of a to-one instance"() {
        given:
        RefProject from = new RefProject(name: 'Id from').save(flush: true)
        RefProject to = new RefProject(name: 'Id to').save(flush: true)
        RefTicket ticket = new RefTicket(title: 'Assign by id', project: from).save(flush: true)

        when: 'a hex String is an identifier, not an instance to reflect'
        manager.session.clear()
        RefTicket.where { title == 'Assign by id' }.updateAll(project: to.id)

        then: 'the same stored ObjectId an instance would have produced'
        rawTickets().find(new Document('_id', new ObjectId(ticket.id))).first().get('project') == new ObjectId(to.id)

        when: 'and so is an ObjectId'
        RefTicket.where { title == 'Assign by id' }.updateAll(project: new ObjectId(from.id))

        then:
        rawTickets().find(new Document('_id', new ObjectId(ticket.id))).first().get('project') == new ObjectId(from.id)
    }

    void "updateAll accepts identifiers in place of to-many instances"() {
        given:
        RefTag a = new RefTag(label: 'id-a').save(flush: true)
        RefTag b = new RefTag(label: 'id-b').save(flush: true)
        RefProject project = new RefProject(name: 'Tags by id').save(flush: true)

        when:
        manager.session.clear()
        RefProject.where { name == 'Tags by id' }.updateAll(tags: [a.id, b.id])

        then:
        rawProjects().find(new Document('_id', new ObjectId(project.id))).first().get('tags') ==
                [new ObjectId(a.id), new ObjectId(b.id)]
    }

    void "updateAll drops nulls from a reference to-many before wrapping DBRefs"() {
        given: 'OneToManyEncoder filters nulls before wrapping; a DBRef(collection, null) is invalid'
        RefTag a = new RefTag(label: 'ref-keep').save(flush: true)
        RefLinkedProject project = new RefLinkedProject(name: 'Ref null tags').save(flush: true)

        when:
        manager.session.clear()
        RefLinkedProject.where { name == 'Ref null tags' }.updateAll(tags: [RefTag.get(a.id), null])
        List stored = rawLinkedProjects().find(new Document('_id', new ObjectId(project.id))).first().get('tags')

        then: 'the null is gone and the one reference is a DBRef carrying the stored ObjectId'
        stored.size() == 1
        stored[0] instanceof DBRef
        ((DBRef) stored[0]).id == new ObjectId(a.id)

        and: 'and it reads back'
        manager.session.clear()
        RefLinkedProject.get(project.id).tags*.label == ['ref-keep']
    }

    private MongoCollection<Document> rawLinkedProjects() {
        manager.mongoClient.getDatabase('test').getCollection('refLinkedProject')
    }

    void "updateAll on a many-to-many writes the target stored ids"() {
        given: 'this engine shares the unidirectional branch for it, but it is its own association kind'
        RefRight one = new RefRight(label: 'right-one').save(flush: true)
        RefRight two = new RefRight(label: 'right-two').save(flush: true)
        RefRole role = new RefRole(label: 'role')
        role.addToRights(one)
        role.save(flush: true)

        when:
        manager.session.clear()
        RefRole.where { label == 'role' }.updateAll(rights: [RefRight.get(two.id)])

        then: 'an ObjectId array under the plain name, as OneToManyEncoder writes it'
        rawRoles().find(new Document('_id', new ObjectId(role.id))).first().get('rights') == [new ObjectId(two.id)]

        and: 'and it reads back'
        manager.session.clear()
        RefRole.get(role.id).rights*.label == ['right-two']
    }

    private MongoCollection<Document> rawRoles() {
        manager.mongoClient.getDatabase('test').getCollection('refRole')
    }

    private MongoCollection<Document> rawFaces() {
        manager.mongoClient.getDatabase('test').getCollection('refFace')
    }

    private MongoCollection<Document> rawBiParents() {
        manager.mongoClient.getDatabase('test').getCollection('refBiParent')
    }

    private MongoCollection<Document> rawNotes() {
        manager.mongoClient.getDatabase('test').getCollection('refNote')
    }

    private MongoCollection<Document> rawPeople() {
        manager.mongoClient.getDatabase('test').getCollection('refPerson')
    }

    private MongoCollection<Document> rawTickets() {
        manager.mongoClient.getDatabase('test').getCollection('refTicket')
    }

    private MongoCollection<Document> rawProjects() {
        manager.mongoClient.getDatabase('test').getCollection('refProject')
    }
}

@Entity
class RefProject {
    String id
    String name
    Set<RefTag> tags = []
    static hasMany = [tags: RefTag]
    static mapping = {
        version false
    }
}

@Entity
class RefTicket {
    String id
    String title
    RefProject project
    static mapping = {
        version false
    }
}

@Entity
class RefTag {
    String id
    String label
    static mapping = {
        version false
    }
}

@Entity
class RefPerson {
    String id
    String name
    RefAddress address
    static embedded = ['address']
    static mapping = { version false }
}

@Entity
class RefAddress {
    String city
}

@Entity
class RefNote {
    String id
    String title
    List<String> labels = []
    static hasMany = [labels: String]
    static mapping = { version false }
}

@Entity
class RefBiParent {
    String id
    String name
    Set<RefBiChild> children = []
    static hasMany = [children: RefBiChild]
    static mapping = { version false }
}

@Entity
class RefBiChild {
    String id
    String name
    RefBiParent parent
    static belongsTo = [parent: RefBiParent]
    static mapping = { version false }
}

@Entity
class RefFace {
    String id
    String name
    RefNose nose
    static hasOne = [nose: RefNose]
    static mapping = { version false }
}

@Entity
class RefNose {
    String id
    String size
    RefFace face
    static belongsTo = [face: RefFace]
    static mapping = { version false }
}

@Entity
class RefLinkedProject {
    String id
    String name
    Set<RefTag> tags = []
    static hasMany = [tags: RefTag]
    static mapping = {
        version false
        tags reference: true
    }
}

@Entity
class RefRole {
    String id
    String label
    Set<RefRight> rights = []
    static hasMany = [rights: RefRight]
    static mapping = { version false }
}

@Entity
class RefRight {
    String id
    String label
    Set<RefRole> roles = []
    static hasMany = [roles: RefRole]
    static belongsTo = RefRole
    static mapping = { version false }
}
