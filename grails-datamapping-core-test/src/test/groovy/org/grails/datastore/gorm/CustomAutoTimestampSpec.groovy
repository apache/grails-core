package org.grails.datastore.gorm

import grails.gorm.annotation.AutoTimestamp
import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import spock.lang.Retry
import spock.lang.Stepwise

import static grails.gorm.annotation.AutoTimestamp.EventType.CREATED

@Retry // this test is flaky on CI due to https://github.com/apache/grails-data-mapping/issues/1877
@Stepwise
class CustomAutoTimestampSpec extends GormDatastoreSpec {

    void "Test when the auto timestamp properties are customized, they are correctly set"() {
        when:"An entity is persisted"
            def r = new RecordCustom(name: "Test")
            r.save(flush:true, failOnError:true)
            session.clear()
            r = RecordCustom.get(r.id)

        then:"the custom lastUpdated and dateCreated are set"
            r.modified != null && r.modified < new Date()
            r.created != null && r.created < new Date()

        when:"An entity is modified"
            Date previousCreated = r.created
            Date previousModified = r.modified
            r.name = "Test 2"
            r.save(flush:true)
            session.clear()
            r = RecordCustom.get(r.id)

            then:"the custom lastUpdated property is updated and dateCreated is not"
            r.modified != null && previousModified < r.modified
            previousCreated.time == r.created.time
    }

    void "Test when the auto timestamp properties are already set, they are overwritten"() {
        when:"An entity is persisted"
        def r = new RecordCustom(name: "Test")
        def now = new Date()
        r.created = new Date(now.time)
        r.modified = r.created
        r.save(flush:true, failOnError:true)
        session.clear()
        r = RecordCustom.get(r.id)

        then:"the custom lastUpdated and dateCreated are set"
        now < r.modified
        now < r.created

        when:"An entity is modified"
        Date previousCreated = r.created
        Date previousModified = r.modified
        r.name = "Test 2"
        r.save(flush:true)
        session.clear()
        r = RecordCustom.get(r.id)

        then:"the custom lastUpdated property is updated and dateCreated is not"
        r.modified != null && previousModified < r.modified
        previousCreated.time == r.created.time
    }

    void "Test when the auto timestamp properties are already set, they are not overwritten if config is set"() {
        when:"An entity is persisted and insertOverwrite is false"
        AutoTimestampEventListener autoTimestampEventListener =
                RecordCustom.gormPersistentEntity.mappingContext.eventListeners.find { it.class == AutoTimestampEventListener}
        autoTimestampEventListener.insertOverwrite = false

        def r = new RecordCustom(name: "Test")
        def now = new Date()
        r.created = new Date(now.time)
        r.modified = r.created
        r.save(flush:true, failOnError:true)
        session.clear()
        r = RecordCustom.get(r.id)

        then:"the custom lastUpdated and dateCreated are not overwritten"
        now.time == r.modified.time
        now.time == r.created.time

        when:"An entity is modified"
        Date previousCreated = r.created
        Date previousModified = r.modified
        r.name = "Test 2"
        r.save(flush:true)
        session.clear()
        r = RecordCustom.get(r.id)

        then:"the custom lastUpdated property is updated and dateCreated is not"
        r.modified != null && previousModified < r.modified
        previousCreated.time == r.created.time

        cleanup:
        (RecordCustom.gormPersistentEntity.mappingContext.eventListeners.find { it.class == AutoTimestampEventListener} as AutoTimestampEventListener).insertOverwrite = true
    }

    @Override
    List getDomainClasses() {
        [RecordCustom]
    }
}

@Entity
class RecordCustom {
    Long id
    String name
    @AutoTimestamp(CREATED) Date created
    @AutoTimestamp Date modified
}
