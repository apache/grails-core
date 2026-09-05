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
        manager.registerDomainClasses(RefProject, RefTicket, RefTag)
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
