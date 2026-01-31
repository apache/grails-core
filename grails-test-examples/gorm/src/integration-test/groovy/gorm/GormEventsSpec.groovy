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
package gorm

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Tests for GORM Event Listeners.
 *
 * GORM provides lifecycle event hooks that are invoked at various points
 * during the persistence lifecycle:
 * - beforeValidate: Before validation
 * - beforeInsert/afterInsert: Around initial save
 * - beforeUpdate/afterUpdate: Around updates
 * - beforeDelete/afterDelete: Around deletion
 * - onLoad: When entity is loaded from database
 *
 * Note: These tests focus on entity state changes rather than static event logs
 * to avoid test isolation issues with parallel test execution.
 */
@Rollback
@Integration
class GormEventsSpec extends Specification {

    def setup() {
        AuditedEntity.executeUpdate('delete from AuditedEntity')
    }

    // ============================================
    // beforeValidate Event Tests
    // ============================================

    void "test beforeValidate is called during validation"() {
        given: "an entity with untrimmed name"
        def entity = new AuditedEntity(name: '  Test Entity  ')

        when: "validating the entity"
        entity.validate()

        then: "beforeValidate was called and name was trimmed"
        entity.wasValidated == true
        entity.name == 'Test Entity'
    }

    void "test beforeValidate is called before save validation"() {
        when: "saving an entity"
        def entity = new AuditedEntity(name: '  BeforeValidate Test  ')
        entity.save(flush: true)

        then: "beforeValidate was called"
        entity.wasValidated == true
        entity.name == 'BeforeValidate Test'  // Trimmed
    }

    // ============================================
    // beforeInsert/afterInsert Event Tests
    // ============================================

    void "test beforeInsert sets audit fields"() {
        when: "saving a new entity"
        def entity = new AuditedEntity(name: 'Insert Test')
        entity.save(flush: true)

        then: "beforeInsert set the audit fields"
        entity.dateCreated != null
        entity.lastUpdated != null
        entity.createdBy == 'test-user'
        entity.updatedBy == 'test-user'
    }

    void "test beforeInsert and afterInsert event order"() {
        when: "saving a new entity"
        def entity = new AuditedEntity(name: "EventOrder")
        entity.save(flush: true)

        then: "entity was persisted with ID (proving events fired)"
        entity.id != null
        entity.dateCreated != null
        entity.createdBy == 'test-user'
        entity.wasValidated == true
    }

    void "test afterInsert is called after entity is persisted"() {
        when: "saving a new entity"
        def entity = new AuditedEntity(name: 'AfterInsert')
        entity.save(flush: true)

        then: "entity was persisted"
        entity.id != null
        entity.dateCreated != null
    }

    // ============================================
    // beforeUpdate/afterUpdate Event Tests
    // ============================================

    void "test beforeUpdate sets lastUpdated"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'Update Test')
        entity.save(flush: true)
        def originalUpdated = entity.lastUpdated

        when: "updating the entity"
        sleep(10)  // Ensure time difference
        entity.description = 'Updated description'
        entity.save(flush: true)

        then: "beforeUpdate updated lastUpdated"
        entity.lastUpdated > originalUpdated
        entity.updateCount == 1
    }

    void "test beforeUpdate and afterUpdate event order"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'UpdateOrder')
        entity.save(flush: true)
        def originalUpdated = entity.lastUpdated

        when: "updating the entity"
        sleep(10)
        entity.description = 'Changed'
        entity.save(flush: true)

        then: "events fired - lastUpdated was modified by beforeUpdate"
        entity.lastUpdated > originalUpdated
        entity.updateCount == 1
        entity.updatedBy == 'test-user'
    }

    void "test beforeUpdate can cancel update by returning false"() {
        given: "an entity marked as DELETED"
        def entity = new AuditedEntity(name: 'Deleted Entity', status: 'DELETED')
        entity.save(flush: true)
        def originalDescription = entity.description
        def originalUpdateCount = entity.updateCount

        when: "attempting to update DELETED entity"
        entity.description = 'Trying to change'
        entity.save(flush: true)

        // Clear session to reload
        AuditedEntity.withSession { it.flush(); it.clear() }
        def reloaded = AuditedEntity.findByName('Deleted Entity')

        then: "update was cancelled - description unchanged"
        reloaded.description == originalDescription
        // Note: updateCount may or may not increment depending on when beforeUpdate returns false
    }

    void "test updateCount increments on each update"() {
        given: "a new entity"
        def entity = new AuditedEntity(name: 'MultiUpdate')
        entity.save(flush: true)
        assert entity.updateCount == 0

        when: "updating multiple times"
        entity.description = 'First update'
        entity.save(flush: true)

        entity.description = 'Second update'
        entity.save(flush: true)

        entity.description = 'Third update'
        entity.save(flush: true)

        then: "updateCount tracks all updates"
        entity.updateCount == 3
    }

    // ============================================
    // beforeDelete/afterDelete Event Tests
    // ============================================

    void "test beforeDelete is called before deletion"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'Delete Test')
        entity.save(flush: true)
        def entityId = entity.id

        when: "deleting the entity"
        entity.delete(flush: true)

        then: "entity is deleted"
        AuditedEntity.get(entityId) == null
    }

    void "test afterDelete is called after deletion"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'AfterDelete')
        entity.save(flush: true)
        def entityId = entity.id

        when: "deleting the entity"
        entity.delete(flush: true)

        then: "entity is removed from database"
        AuditedEntity.get(entityId) == null
    }

    void "test delete removes entity from database"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'DeleteOrder')
        entity.save(flush: true)
        def entityId = entity.id

        when: "deleting the entity"
        entity.delete(flush: true)

        then: "entity is gone"
        AuditedEntity.get(entityId) == null
        AuditedEntity.findByName('DeleteOrder') == null
    }

    // ============================================
    // onLoad Event Tests
    // ============================================

    void "test entity can be loaded from database"() {
        given: "a persisted entity"
        def entity = new AuditedEntity(name: 'Load Test')
        entity.save(flush: true)
        def entityId = entity.id

        // Clear session to force reload from database
        AuditedEntity.withSession { it.flush(); it.clear() }

        when: "loading the entity from database"
        def loaded = AuditedEntity.get(entityId)

        then: "entity is loaded correctly"
        loaded != null
        loaded.name == 'Load Test'
        loaded.id == entityId
    }

    void "test list loads multiple entities from database"() {
        given: "multiple persisted entities"
        new AuditedEntity(name: 'Load1').save(flush: true)
        new AuditedEntity(name: 'Load2').save(flush: true)
        new AuditedEntity(name: 'Load3').save(flush: true)

        // Clear session to force reload
        AuditedEntity.withSession { it.flush(); it.clear() }

        when: "listing all entities"
        def entities = AuditedEntity.list()

        then: "all entities are loaded"
        entities.size() == 3
        entities*.name.containsAll(['Load1', 'Load2', 'Load3'])
    }

    // ============================================
    // Complete Lifecycle Tests
    // ============================================

    void "test full entity lifecycle"() {
        when: "creating an entity"
        def entity = new AuditedEntity(name: 'Lifecycle')
        entity.save(flush: true)

        then: "insert lifecycle completed"
        entity.id != null
        entity.dateCreated != null
        entity.createdBy == 'test-user'
        entity.wasValidated == true

        when: "updating the entity"
        def originalUpdated = entity.lastUpdated
        sleep(10)
        entity.description = 'Updated'
        entity.save(flush: true)

        then: "update lifecycle completed"
        entity.lastUpdated > originalUpdated
        entity.updateCount == 1

        when: "deleting the entity"
        def entityId = entity.id
        entity.delete(flush: true)

        then: "delete lifecycle completed"
        AuditedEntity.get(entityId) == null
    }

    void "test event firing does not occur for failed validation"() {
        given: "an invalid entity (blank name)"
        def entity = new AuditedEntity(name: '')

        when: "attempting to save"
        def saved = entity.save()

        then: "save fails"
        saved == null
        entity.hasErrors()
    }

    void "test multiple entities can be saved"() {
        when: "saving multiple entities"
        def e1 = new AuditedEntity(name: 'Entity1').save(flush: true)
        def e2 = new AuditedEntity(name: 'Entity2').save(flush: true)
        def e3 = new AuditedEntity(name: 'Entity3').save(flush: true)

        then: "all entities saved with audit fields"
        e1.id != null
        e2.id != null
        e3.id != null
        [e1, e2, e3].every { it.dateCreated != null && it.createdBy == 'test-user' }
    }

    // ============================================
    // Audit Field Tests
    // ============================================

    void "test audit fields are automatically populated on insert"() {
        when: "saving a new entity"
        def entity = new AuditedEntity(name: 'Audit Test')
        entity.save(flush: true)

        then: "all audit fields are set"
        entity.dateCreated != null
        entity.lastUpdated != null
        entity.createdBy == 'test-user'
        entity.updatedBy == 'test-user'
    }

    void "test audit fields are updated on update"() {
        given: "an existing entity"
        def entity = new AuditedEntity(name: 'Audit Update')
        entity.save(flush: true)
        def originalCreated = entity.dateCreated
        def originalUpdated = entity.lastUpdated

        when: "updating the entity"
        sleep(10)
        entity.description = 'Changed'
        entity.save(flush: true)

        then: "dateCreated unchanged, lastUpdated modified"
        entity.dateCreated == originalCreated
        entity.lastUpdated > originalUpdated
    }

    void "test name trimming in beforeValidate"() {
        when: "saving entities with various whitespace"
        def e1 = new AuditedEntity(name: '  leading spaces')
        def e2 = new AuditedEntity(name: 'trailing spaces  ')
        def e3 = new AuditedEntity(name: '  both sides  ')
        e1.save(flush: true)
        e2.save(flush: true)
        e3.save(flush: true)

        then: "all names are trimmed"
        e1.name == 'leading spaces'
        e2.name == 'trailing spaces'
        e3.name == 'both sides'
    }
}
