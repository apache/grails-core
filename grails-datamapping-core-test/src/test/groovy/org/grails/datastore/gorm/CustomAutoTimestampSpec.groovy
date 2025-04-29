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
package org.grails.datastore.gorm

import grails.gorm.annotation.AutoTimestamp
import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.events.AutoTimestampEventListener

import static grails.gorm.annotation.AutoTimestamp.EventType.CREATED

class CustomAutoTimestampSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([RecordCustom])
    }

    void "Test when the auto timestamp properties are customized, they are correctly set"() {
        when: "An entity is persisted"
        def r = new RecordCustom(name: "Test")
        r.save(flush: true, failOnError: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)
        sleep(1) // give the date comparison below a chance diff

        then: "the custom lastUpdated and dateCreated are set"
        r.modified != null
        r.created != null
        r.modified.time < new Date().time
        r.created.time < new Date().time

        when: "An entity is modified"
        Date previousCreated = r.created
        Date previousModified = r.modified
        r.name = "Test 2"
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)

        then: "the custom lastUpdated property is updated and dateCreated is not"
        r.modified != null
        previousModified.time < r.modified.time
        previousCreated.time == r.created.time
    }

    void "Test when the auto timestamp properties are already set, they are overwritten"() {
        when: "An entity is persisted"
        def r = new RecordCustom(name: "Test")
        def now = new Date()
        r.created = new Date(now.time)
        r.modified = r.created
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true, failOnError: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)

        then: "the custom lastUpdated and dateCreated are set"
        now.time < r.modified.time
        now.time < r.created.time

        when: "An entity is modified"
        Date previousCreated = r.created
        Date previousModified = r.modified
        r.name = "Test 2"
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)

        then: "the custom lastUpdated property is updated and dateCreated is not"
        r.modified != null
        previousModified.time < r.modified.time
        previousCreated.time == r.created.time
    }

    void "Test when the auto timestamp properties are already set, they are not overwritten if config is set"() {
        when: "An entity is persisted and insertOverwrite is false"
        AutoTimestampEventListener autoTimestampEventListener =
                RecordCustom.gormPersistentEntity.mappingContext.eventListeners.find { it.class == AutoTimestampEventListener }
        autoTimestampEventListener.insertOverwrite = false

        def r = new RecordCustom(name: "Test")
        def now = new Date()
        r.created = new Date(now.time)
        r.modified = r.created
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true, failOnError: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)

        then: "the custom lastUpdated and dateCreated are not overwritten"
        now.time == r.modified.time
        now.time == r.created.time

        when: "An entity is modified"
        Date previousCreated = r.created
        Date previousModified = r.modified
        r.name = "Test 2"
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true)
        manager.session.clear()
        r = RecordCustom.get(r.id)

        then: "the custom lastUpdated property is updated and dateCreated is not"
        r.modified != null
        previousModified.time < r.modified.time
        previousCreated.time == r.created.time
    }
}

@Entity
class RecordCustom {
    Long id
    String name
    @AutoTimestamp(CREATED)
    Date created
    @AutoTimestamp
    Date modified
}
