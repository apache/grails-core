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
package org.grails.datastore.mapping.config;

/**
 * Enum representing the type of audit metadata annotation on a property.
 * This follows Spring Data's auditing model which supports both temporal
 * metadata (when changes occurred) and auditor metadata (who made changes).
 *
 * @author Scott Murphy Heiberg
 * @since 7.1
 */
public enum AuditMetadataType {
    /**
     * Property has {@code @CreatedDate} annotation or {@code @AutoTimestamp(CREATED)}.
     * Automatically populated with the creation timestamp on insert.
     */
    CREATED,

    /**
     * Property has {@code @LastModifiedDate} annotation or {@code @AutoTimestamp(UPDATED)}.
     * Automatically populated with the modification timestamp on insert and update.
     */
    UPDATED,

    /**
     * Property has {@code @CreatedBy} annotation.
     * Automatically populated with the current auditor on insert.
     */
    CREATED_BY,

    /**
     * Property has {@code @LastModifiedBy} annotation.
     * Automatically populated with the current auditor on insert and update.
     */
    UPDATED_BY,

    /**
     * Property has no auditing annotation.
     */
    NONE
}
