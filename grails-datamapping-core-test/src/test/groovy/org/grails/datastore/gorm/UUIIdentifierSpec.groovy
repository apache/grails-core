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

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class UUIIdentifierSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([DocumentModel])
    }

    void "Test that a UUID identifier is correctly generated"() {
        when: "A domain with a UUID is saved"
        def dm = new DocumentModel(name: "My Doc").save()

        then: "The UUID is correctly generated"
        dm != null
        dm.id != null
        DocumentModel.count() == 1

        when: "Another entity is saved"
        new DocumentModel(name: "Another").save()
        then: "There are 2"
        DocumentModel.count() == 2
    }
}

@Entity
class DocumentModel {
    static final SCORE = 40

    String id // UUID , for replications / optimization
    String name
    String description = ''

    Date dateCreated
    Date lastUpdated

    long estimatedScore = 0
    long score = 0

    Map<String, Object> parameters = new HashMap<String, Object>()

    static mapping = {
        id generator: 'uuid'
        name index: 'idx_doc_name'
        description size: 0..300, nullable: true
    }

    static constraints = {
        estimatedScore min: 0l
        score min: 0l
        name blank: false, unique: 'workspace'
    }
}
