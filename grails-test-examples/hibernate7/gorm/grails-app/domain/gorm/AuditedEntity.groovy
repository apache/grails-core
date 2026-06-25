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

/**
 * Domain class demonstrating GORM event listeners.
 *
 * GORM provides event hooks that are called at various points in the
 * persistence lifecycle: beforeInsert, afterInsert, beforeUpdate,
 * afterUpdate, beforeDelete, afterDelete, beforeValidate, onLoad.
 */
class AuditedEntity {

    String name
    String description
    String status = 'ACTIVE'

    // Audit fields - automatically populated by event listeners
    Date dateCreated
    Date lastUpdated
    String createdBy
    String updatedBy

    // Tracking fields for testing events
    Integer version = 0
    Integer updateCount = 0
    Boolean wasValidated = false
    Boolean wasLoaded = false

    static constraints = {
        name blank: false, size: 1..100
        description nullable: true, maxSize: 500
        status inList: ['ACTIVE', 'INACTIVE', 'DELETED']
        dateCreated nullable: true
        lastUpdated nullable: true
        createdBy nullable: true
        updatedBy nullable: true
    }

    static mapping = {
        // Auto-timestamp is enabled by default, but we'll manage manually for testing
        autoTimestamp false
    }

    /**
     * Called before the entity is validated.
     */
    def beforeValidate() {
        wasValidated = true
        // Normalize name
        if (name) {
            name = name.trim()
        }
    }

    /**
     * Called before inserting a new entity.
     * Use this for setting default values, generating codes, etc.
     */
    def beforeInsert() {
        dateCreated = new Date()
        lastUpdated = new Date()
        createdBy = getCurrentUser()
        updatedBy = getCurrentUser()
    }

    /**
     * Called after inserting a new entity.
     * Use this for post-insert operations like notifications, caching.
     */
    def afterInsert() {
        // Could trigger notifications, update caches, etc.
    }

    /**
     * Called before updating an existing entity.
     * Can return false to cancel the update.
     */
    def beforeUpdate() {
        lastUpdated = new Date()
        updatedBy = getCurrentUser()
        updateCount++

        // Example: prevent updates to DELETED entities
        if (status == 'DELETED') {
            return false  // Cancel the update
        }
    }

    /**
     * Called after updating an existing entity.
     */
    def afterUpdate() {
        // Could log changes, trigger notifications, etc.
    }

    /**
     * Called before deleting an entity.
     * Can return false to cancel the delete.
     */
    def beforeDelete() {
        // Example: implement soft delete instead
        // return false // Uncomment to prevent deletion
    }

    /**
     * Called after deleting an entity.
     */
    def afterDelete() {
        // Could clean up related resources, trigger notifications, etc.
    }

    /**
     * Called when entity is loaded from database.
     */
    def onLoad() {
        wasLoaded = true
    }

    /**
     * Helper method to simulate getting current user.
     * In real applications, this would integrate with security context.
     */
    private static String getCurrentUser() {
        return "test-user"
    }

    String toString() {
        "AuditedEntity[name=${name}, status=${status}]"
    }
}
