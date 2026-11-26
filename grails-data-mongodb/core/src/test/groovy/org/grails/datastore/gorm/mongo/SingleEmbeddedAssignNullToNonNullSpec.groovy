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
 * Reproduces a bug in GORM-MongoDB where assigning a new instance to a
 * previously-null single-valued embedded property does not persist on
 * {@code save(flush: true)}: dirty tracking fails to flag the transition
 * from {@code null} to an embedded value, so the resulting {@code $set}
 * never includes the embedded sub-document.
 *
 * Top-level scalar fields on the same entity save fine — the bug is
 * specific to single-valued embedded references going from null to non-null.
 */
class SingleEmbeddedAssignNullToNonNullSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([FramePogo, CropPogo, FrameEntity, CropEntity])
    }

    void "POGO embedded value: null→non-null assignment persists"() {
        given: "a Frame with no embedded Crop"
        FramePogo frame = new FramePogo(name: 'frame-a')
        frame.save(flush: true, failOnError: true)
        ObjectId id = frame.id
        manager.session.clear()

        expect: "the persisted document has no crop field"
        FramePogo.get(id).crop == null

        when: "we reload, set a new Crop value, and save"
        FramePogo reloaded = FramePogo.get(id)
        reloaded.crop = new CropPogo(status: 'CROPPED', ratio: 0.75d)
        reloaded.save(flush: true, failOnError: true)
        manager.session.clear()

        then: "the embedded value is now persisted"
        FramePogo persisted = FramePogo.get(id)
        persisted.crop != null
        persisted.crop.status == 'CROPPED'
        persisted.crop.ratio == 0.75d
    }

    void "POGO embedded: top-level scalar save works (control)"() {
        given:
        FramePogo frame = new FramePogo(name: 'frame-b')
        frame.save(flush: true, failOnError: true)
        ObjectId id = frame.id
        manager.session.clear()

        when:
        FramePogo reloaded = FramePogo.get(id)
        reloaded.name = 'frame-b-renamed'
        reloaded.save(flush: true, failOnError: true)
        manager.session.clear()

        then:
        FramePogo.get(id).name == 'frame-b-renamed'
    }

    void "@Entity embedded value: null→non-null assignment persists"() {
        given:
        FrameEntity frame = new FrameEntity(name: 'frame-c')
        frame.save(flush: true, failOnError: true)
        ObjectId id = frame.id
        manager.session.clear()

        expect:
        FrameEntity.get(id).crop == null

        when:
        FrameEntity reloaded = FrameEntity.get(id)
        reloaded.crop = new CropEntity(status: 'CROPPED', ratio: 0.75d)
        reloaded.save(flush: true, failOnError: true)
        manager.session.clear()

        then:
        FrameEntity persisted = FrameEntity.get(id)
        persisted.crop != null
        persisted.crop.status == 'CROPPED'
        persisted.crop.ratio == 0.75d
    }
}

@Entity
class FramePogo {
    static mapWith = 'mongo'
    static embedded = ['crop']
    static constraints = {
        crop nullable: true
    }

    ObjectId id
    String name
    CropPogo crop
}

class CropPogo {
    String status
    Double ratio
}

@Entity
class FrameEntity {
    static mapWith = 'mongo'
    static embedded = ['crop']
    static constraints = {
        crop nullable: true
    }

    ObjectId id
    String name
    CropEntity crop
}

@Entity
class CropEntity {
    ObjectId id
    String status
    Double ratio
    static constraints = {
        status nullable: true
        ratio nullable: true
    }
}
