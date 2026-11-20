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

package functionaltests.events

/**
 * Domain class demonstrating GORM lifecycle events.
 */
class AuditedEntity {

    String name
    String description
    String status = 'DRAFT'
    Integer version = 0
    
    // Audit fields populated by lifecycle events
    String createdBy
    Date createdAt
    String modifiedBy
    Date modifiedAt
    
    // Event tracking for testing
    static transients = ['eventLog']
    List<String> eventLog = []
    
    Date dateCreated
    Date lastUpdated

    static constraints = {
        name blank: false, size: 1..100
        description nullable: true, maxSize: 500
        status inList: ['DRAFT', 'ACTIVE', 'ARCHIVED', 'DELETED']
        createdBy nullable: true
        createdAt nullable: true
        modifiedBy nullable: true
        modifiedAt nullable: true
    }

    static mapping = {
        table 'audited_entities'
    }

    // ========== GORM Lifecycle Events ==========

    def beforeInsert() {
        eventLog << 'beforeInsert'
        createdAt = new Date()
        createdBy = 'system'
        // Ensure status is valid before insert
        if (!status) {
            status = 'DRAFT'
        }
    }

    def afterInsert() {
        eventLog << 'afterInsert'
    }

    def beforeUpdate() {
        eventLog << 'beforeUpdate'
        modifiedAt = new Date()
        modifiedBy = 'system'
    }

    def afterUpdate() {
        eventLog << 'afterUpdate'
    }

    def beforeDelete() {
        eventLog << 'beforeDelete'
    }

    def afterDelete() {
        eventLog << 'afterDelete'
    }

    def beforeValidate() {
        eventLog << 'beforeValidate'
        // Clean up name
        if (name) {
            name = name.trim()
        }
    }

    def afterLoad() {
        eventLog << 'afterLoad'
    }

    def onSave() {
        eventLog << 'onSave'
    }
}
