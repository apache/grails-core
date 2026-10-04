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
 * The same defensive re-init the embedded case covers, on a to-many association. A stored empty
 * association decodes into a PersistentList rather than one of the generic DirtyChecking*
 * wrappers, so the re-init has to be recognised there too or the add that follows goes to a
 * plain list nobody tracks.
 */
class HasManyReassignDirtyTrackingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(ProbeBoard, ProbeMember)
    }

    void "an in-place add after re-initialising an empty hasMany is persisted"() {
        given: "a board stored with members: [], which decodes as an empty PersistentList"
        ProbeBoard board = new ProbeBoard(name: 'board', members: []).save(flush: true, validate: false)
        manager.session.clear()

        when: "the defensive re-init runs — an empty collection is falsy in Groovy — then a member is added"
        board = ProbeBoard.get(board.id)
        board.trackChanges()
        if (!board.members) {
            board.members = []
        }
        board.members.add(new ProbeMember(userId: 'u1'))
        board.save(flush: true)
        manager.session.clear()
        board = ProbeBoard.get(board.id)

        then: "the member reaches the document"
        board.members.size() == 1
        board.members[0].userId == 'u1'
    }
}

@Entity
class ProbeBoard {
    ObjectId id
    String name
    List<ProbeMember> members
    // Load-bearing, as in EmbeddedCollectionDirtyTrackingSpec: the PreUpdate timestamp write
    // consumes the whole-class dirty marker an explicit save() sets, so the update carries only
    // the properties that were individually tracked.
    Date lastUpdated

    static mapWith = "mongo"
    static hasMany = [members: ProbeMember]
    static constraints = {
        members nullable: true
    }
}

@Entity
class ProbeMember {
    ObjectId id
    String userId

    static mapWith = "mongo"
}
