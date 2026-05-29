/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.support

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import org.grails.orm.hibernate.support.hibernate7.HibernateSystemException
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

class ClosureEventListenerSpec extends HibernateGormDatastoreSpec {

    @Override
    void setupSpec() {
        manager.addAllDomainClasses([
            EventBook,
            ValidatedBook,
            MutatingBook,
            LegacyLoadBook,
            NoEventBook
        ])
    }

    void cleanup() {
        EventBook.callLog.clear()
    }

    // -------------------------------------------------------------------------
    // beforeInsert
    // -------------------------------------------------------------------------

    @Rollback
    void "beforeInsert is called before a new entity is persisted"() {
        when:
        new EventBook(title: "Groovy in Action").save(flush: true, failOnError: true)

        then:
        'beforeInsert' in EventBook.callLog
    }

    @Rollback
    void "beforeInsert returning false vetoes the insert"() {
        given:
        EventBook.vetoInsert = true

        when:
        new EventBook(title: "Vetoed Book").save(flush: true)

        then:
        thrown(HibernateSystemException)

        cleanup:
        EventBook.vetoInsert = false
    }

    // -------------------------------------------------------------------------
    // afterInsert
    // -------------------------------------------------------------------------

    @Rollback
    void "afterInsert is called after a new entity is persisted"() {
        when:
        new EventBook(title: "Clean Code").save(flush: true, failOnError: true)

        then:
        'afterInsert' in EventBook.callLog
    }

    // -------------------------------------------------------------------------
    // beforeUpdate / afterUpdate
    // -------------------------------------------------------------------------

    @Rollback
    void "beforeUpdate is called before an existing entity is updated"() {
        given:
        def book = new EventBook(title: "Original Title").save(flush: true, failOnError: true)
        EventBook.callLog.clear()

        when:
        book.title = "Updated Title"
        book.save(flush: true, failOnError: true)

        then:
        'beforeUpdate' in EventBook.callLog
    }

    @Rollback
    void "afterUpdate is called after an existing entity is updated"() {
        given:
        def book = new EventBook(title: "First Edition").save(flush: true, failOnError: true)
        EventBook.callLog.clear()

        when:
        book.title = "Second Edition"
        book.save(flush: true, failOnError: true)

        then:
        'afterUpdate' in EventBook.callLog
    }

    // -------------------------------------------------------------------------
    // beforeDelete / afterDelete
    // -------------------------------------------------------------------------

    @Rollback
    void "beforeDelete is called before an entity is deleted"() {
        given:
        def book = new EventBook(title: "Ephemeral Book").save(flush: true, failOnError: true)
        EventBook.callLog.clear()

        when:
        book.delete(flush: true)

        then:
        'beforeDelete' in EventBook.callLog
    }

    @Rollback
    void "afterDelete is called after an entity is deleted"() {
        given:
        def book = new EventBook(title: "Gone Book").save(flush: true, failOnError: true)
        EventBook.callLog.clear()

        when:
        book.delete(flush: true)

        then:
        'afterDelete' in EventBook.callLog
    }

    @Rollback
    void "beforeDelete returning false vetoes the delete"() {
        given:
        def book = new EventBook(title: "Protected Book").save(flush: true, failOnError: true)
        Long id = book.id
        EventBook.vetoDelete = true

        when:
        book.delete(flush: true)

        then:
        EventBook.get(id) != null

        cleanup:
        EventBook.vetoDelete = false
    }

    // -------------------------------------------------------------------------
    // onLoad / afterLoad
    // -------------------------------------------------------------------------

    @Rollback
    void "onLoad is called when an entity is loaded from the database"() {
        given:
        def book = new EventBook(title: "Loaded Book").save(flush: true, failOnError: true)
        session.clear()
        EventBook.callLog.clear()

        when:
        EventBook.get(book.id)

        then:
        'onLoad' in EventBook.callLog
    }

    @Rollback
    void "afterLoad is called after an entity is loaded from the database"() {
        given:
        def book = new EventBook(title: "After Load Book").save(flush: true, failOnError: true)
        session.clear()
        EventBook.callLog.clear()

        when:
        EventBook.get(book.id)

        then:
        'afterLoad' in EventBook.callLog
    }

    // -------------------------------------------------------------------------
    // beforeValidate
    // -------------------------------------------------------------------------

    @Rollback
    void "beforeValidate is called before validation runs"() {
        when:
        new EventBook(title: "Validated").save(flush: true, failOnError: true)

        then:
        'beforeValidate' in EventBook.callLog
    }

    // -------------------------------------------------------------------------
    // failOnError — validation failure throws ValidationException
    // -------------------------------------------------------------------------

    void "validation failure with failOnError throws ValidationException"() {
        when:
        ValidatedBook.withTransaction {
            new ValidatedBook(title: null).save(flush: true, failOnError: true)
        }

        then:
        thrown(ValidationException)
    }

    void "validation failure without failOnError returns null"() {
        when:
        def book = ValidatedBook.withTransaction {
            new ValidatedBook(title: null).save(flush: true)
        }

        then:
        book == null || book.hasErrors()
    }

    // -------------------------------------------------------------------------
    // beforeInsert can mutate state that gets persisted
    // -------------------------------------------------------------------------

    @Rollback
    void "property mutation in beforeInsert is reflected in the persisted state"() {
        when:
        def book = new MutatingBook(title: "raw title").save(flush: true, failOnError: true)
        session.clear()
        def reloaded = MutatingBook.get(book.id)

        then:
        reloaded.title == "RAW TITLE"
    }

    @Rollback
    void "property mutation in beforeUpdate is reflected in the persisted state"() {
        given:
        def book = new MutatingBook(title: "first").save(flush: true, failOnError: true)
        session.clear()

        when:
        def loaded = MutatingBook.get(book.id)
        loaded.title = "second"
        loaded.save(flush: true, failOnError: true)
        session.clear()
        def reloaded = MutatingBook.get(book.id)

        then:
        reloaded.title == "SECOND"
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────

    @Rollback
    void "beforeLoad is called if onLoad is missing"() {
        given:
        def book = new LegacyLoadBook(name: "Legacy").save(flush: true)
        session.clear()
        LegacyLoadBook.beforeLoadCalled = false

        when:
        LegacyLoadBook.get(book.id)

        then:
        LegacyLoadBook.beforeLoadCalled
    }

    void "failOnError is enabled if package is in failOnErrorPackages"() {
        given:
        def persistentEntity = manager.hibernateDatastore.mappingContext.getPersistentEntity(ValidatedBook.name) as GrailsHibernatePersistentEntity
        def listener = new ClosureEventListener(persistentEntity, false, ["org.grails.orm.hibernate.support"])

        expect:
        listener.failOnErrorEnabled
    }

    @Rollback
    void "onPreDelete returns false if no listener present"() {
        given:
        def book = new NoEventBook(name: "NoEvent").save(flush: true)
        
        when:
        book.delete(flush: true)

        then:
        noExceptionThrown()
    }
}

// ---------------------------------------------------------------------------
// Domain class with all event hooks instrumented
// ---------------------------------------------------------------------------

@Entity
class EventBook implements HibernateEntity<EventBook> {

    String title

    static callLog = [].asSynchronized() as List<String>
    static boolean vetoInsert = false
    static boolean vetoDelete = false

    static mapping = {
        id generator: 'identity'
    }

    def beforeInsert() {
        callLog << 'beforeInsert'
        return vetoInsert ? false : null
    }

    def afterInsert() {
        callLog << 'afterInsert'
    }

    def beforeUpdate() {
        callLog << 'beforeUpdate'
    }

    def afterUpdate() {
        callLog << 'afterUpdate'
    }

    def beforeDelete() {
        callLog << 'beforeDelete'
        return vetoDelete ? false : null
    }

    def afterDelete() {
        callLog << 'afterDelete'
    }

    def onLoad() {
        callLog << 'onLoad'
    }

    def afterLoad() {
        callLog << 'afterLoad'
    }

    def beforeValidate() {
        callLog << 'beforeValidate'
    }
}

// ---------------------------------------------------------------------------
// Domain class for validation tests
// ---------------------------------------------------------------------------

@Entity
class ValidatedBook implements HibernateEntity<ValidatedBook> {

    String title

    static mapping = {
        id generator: 'identity'
    }

    static constraints = {
        title nullable: false, blank: false
    }
}

// ---------------------------------------------------------------------------
// Domain class that mutates state in event hooks
// ---------------------------------------------------------------------------

@Entity
class MutatingBook implements HibernateEntity<MutatingBook> {

    String title

    static mapping = {
        id generator: 'identity'
    }

    def beforeInsert() {
        title = title?.toUpperCase()
    }

    def beforeUpdate() {
        title = title?.toUpperCase()
    }
}

@Entity
class LegacyLoadBook implements HibernateEntity<LegacyLoadBook> {
    Long id
    String name
    static boolean beforeLoadCalled = false

    static mapping = {
        id generator: 'identity'
    }

    def beforeLoad() {
        beforeLoadCalled = true
    }
}

@Entity
class NoEventBook implements HibernateEntity<NoEventBook> {
    Long id
    String name
    static mapping = {
        id generator: 'identity'
    }
}
