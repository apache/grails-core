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

import spock.lang.Requires

import grails.gorm.annotation.AutoTimestamp
import grails.gorm.annotation.CreatedDate
import grails.gorm.annotation.LastModifiedDate
import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.events.AutoTimestampEventListener

import static grails.gorm.annotation.AutoTimestamp.EventType.CREATED

class CustomAutoTimestampSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([AutoTimestampedChildEntity, AutoTimestampedParentEntity, Image, RecordCustom, RecordWithAliases])
    }

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
                System.getProperty('hibernate7.gorm.suite') == 'true' ||
                System.getProperty('mongodb.gorm.suite') == 'true' })
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

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
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

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
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

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
    void 'AutoTimestamp annotation works when used in non-entity parent classes'() {
        when: 'An entity extending a non-entity class using AutoTimestamp is persisted'
        def i = new Image(format: 'jpg')
        i.save(flush: true, failOnError: true)
        manager.session.clear()
        i = Image.get(i.id)

        then: 'the auto timestamp properties are set'
        i.modified != null
        i.created != null
    }

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
    void 'AutoTimestamp annotation works when used in entity parent classes'() {
        when: 'An entity extending an entity class using AutoTimestamp is persisted'
        def e = new AutoTimestampedChildEntity(name: 'John')
        e.save(flush: true, failOnError: true)
        manager.session.clear()
        e = AutoTimestampedChildEntity.get(e.id)

        then: 'the auto timestamp properties are set'
        e.modified != null
        e.created != null
    }

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
    void "Test @CreatedDate and @LastModifiedDate annotation aliases"() {
        when: "An entity with alias annotations is persisted"
        def r = new RecordWithAliases(name: "Test")
        r.save(flush: true, failOnError: true)
        manager.session.clear()
        r = RecordWithAliases.get(r.id)
        sleep(1) // give the date comparison below a chance diff

        then: "the timestamp properties are set"
        r.createdAt != null
        r.updatedAt != null
        r.createdAt.time < new Date().time
        r.updatedAt.time < new Date().time

        when: "An entity is modified"
        Date previousCreated = r.createdAt
        Date previousUpdated = r.updatedAt
        r.name = "Test 2"
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true)
        manager.session.clear()
        r = RecordWithAliases.get(r.id)

        then: "the lastModified property is updated and createdDate is not"
        r.updatedAt != null
        previousUpdated.time < r.updatedAt.time
        previousCreated.time == r.createdAt.time
    }

    @Requires({ System.getProperty('hibernate5.gorm.suite') == 'true' ||
            System.getProperty('hibernate7.gorm.suite') == 'true' ||
            System.getProperty('mongodb.gorm.suite') == 'true' })
    void "Test @CreatedDate and @LastModifiedDate with insertOverwrite config"() {
        when: "An entity is persisted and insertOverwrite is false"
        AutoTimestampEventListener autoTimestampEventListener =
                RecordWithAliases.gormPersistentEntity.mappingContext.eventListeners.find { it.class == AutoTimestampEventListener }
        autoTimestampEventListener.insertOverwrite = false

        def r = new RecordWithAliases(name: "Test")
        def now = new Date()
        r.createdAt = new Date(now.time)
        r.updatedAt = r.createdAt
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true, failOnError: true)
        manager.session.clear()
        r = RecordWithAliases.get(r.id)

        then: "the timestamp properties are not overwritten"
        now.time == r.updatedAt.time
        now.time == r.createdAt.time

        when: "An entity is modified"
        Date previousCreated = r.createdAt
        Date previousUpdated = r.updatedAt
        r.name = "Test 2"
        sleep(1) // give the save a chance to set a different time
        r.save(flush: true)
        manager.session.clear()
        r = RecordWithAliases.get(r.id)

        then: "the lastModified property is updated and createdDate is not"
        r.updatedAt != null
        previousUpdated.time < r.updatedAt.time
        previousCreated.time == r.createdAt.time
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

class Media {
    @AutoTimestamp(CREATED)
    Date created
    @AutoTimestamp
    Date modified
}

@Entity
class Image extends Media {
    Long id
    String format
}

@Entity
class AutoTimestampedParentEntity {
    Long id
    @AutoTimestamp(CREATED)
    Date created
    @AutoTimestamp
    Date modified
}

@Entity
class AutoTimestampedChildEntity extends AutoTimestampedParentEntity {
    String name
}

@Entity
class RecordWithAliases {
    Long id
    String name
    @CreatedDate
    Date createdAt
    @LastModifiedDate
    Date updatedAt
}