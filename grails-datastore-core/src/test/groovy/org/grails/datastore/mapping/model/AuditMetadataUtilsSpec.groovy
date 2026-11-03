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
package org.grails.datastore.mapping.model

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.CreatedDate
import grails.gorm.annotation.LastModifiedBy
import grails.gorm.annotation.LastModifiedDate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.AuditMetadataType
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext

class AuditMetadataUtilsSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(AuditedRecord)

    @Unroll
    void "#property is detected as #expected without caching"() {
        expect:
        AuditMetadataUtils.getAuditMetadataType(entity.getPropertyByName(property), false) == expected
        AuditMetadataUtils.hasAuditMetadataAnnotation(entity.getPropertyByName(property), false) == (expected != AuditMetadataType.NONE)

        where:
        property    | expected
        'created'   | AuditMetadataType.CREATED
        'updated'   | AuditMetadataType.UPDATED
        'createdBy' | AuditMetadataType.CREATED_BY
        'updatedBy' | AuditMetadataType.UPDATED_BY
        'plain'     | AuditMetadataType.NONE
    }

    void "caching stores the detected type on the mapped form and reuses it"() {
        given:
        PersistentProperty created = entity.getPropertyByName('created')
        created.mapping.mappedForm.auditMetadataType = null

        when:
        AuditMetadataType first = AuditMetadataUtils.getAuditMetadataType(created, true)

        then:
        first == AuditMetadataType.CREATED
        created.mapping.mappedForm.auditMetadataType == AuditMetadataType.CREATED

        when: 'the cached value is changed the cached value wins'
        created.mapping.mappedForm.auditMetadataType = AuditMetadataType.UPDATED_BY

        then:
        AuditMetadataUtils.getAuditMetadataType(created, true) == AuditMetadataType.UPDATED_BY
        AuditMetadataUtils.getAuditMetadataType(created, false) == AuditMetadataType.CREATED

        cleanup:
        created.mapping.mappedForm.auditMetadataType = null
    }

    void "a null property never has audit metadata"() {
        expect:
        !AuditMetadataUtils.hasAuditMetadataAnnotation(null, true)
        !AuditMetadataUtils.hasAuditMetadataAnnotation(null, false)
    }

    void "a property without a backing field is reported as NONE"() {
        given:
        PersistentEntity derived = mappingContext.addPersistentEntity(AuditedDerived)

        expect:
        AuditMetadataUtils.getAuditMetadataType(derived.getPropertyByName('stamp'), false) == AuditMetadataType.NONE
    }
}

class AuditedRecord {
    Long id
    @CreatedDate
    Date created
    @LastModifiedDate
    Date updated
    @CreatedBy
    String createdBy
    @LastModifiedBy
    String updatedBy
    String plain
}

class AuditedDerived {
    Long id
    String name

    Date getStamp() {
        new Date(0)
    }

    void setStamp(Date stamp) {
    }
}
