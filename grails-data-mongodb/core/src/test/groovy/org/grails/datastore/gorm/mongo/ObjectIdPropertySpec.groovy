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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 29/02/16.
 */
class ObjectIdPropertySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([ObjectIdPerson])
    }

    void "test save and retrieve object id"() {
        when: "an object is saved and retrieved"

        def id = new ObjectId()
        ObjectIdPerson o = new ObjectIdPerson(name: "Fred", scopeId: id)
        o.save(flush: true)
        manager.session.clear()
        o = ObjectIdPerson.get(o.id)

        then: "The id is correct"
        o.scopeId == id
        manager.session.clear()

        when: "A query is used to retrieve the object"
        o = ObjectIdPerson.findByScopeId(id)

        then: "The result is correct"
        o != null
        o.scopeId == id
    }
}

@Entity
class ObjectIdPerson {
    ObjectId id;
    String name;
    ObjectId scopeId
}