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
import org.bson.Document
import org.bson.types.Binary
import org.bson.types.ObjectId

class MongoTypesSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([MongoTypes])
    }

    void "Test that an entity can save and load native mongo types"() {
        when: "A domain class with mongodb types is saved and read"
        def mt = new MongoTypes()
        mt.bson = new Document(foo: new Document([embedded: "bar"]))
        mt.binary = new Binary("foo".bytes)
        def otherId = new ObjectId()
        mt.otherId = otherId
        mt.save flush: true
        manager.session.clear()
        mt = MongoTypes.get(mt.id)

        then: "Then it is in the correct state"
        mt != null
        mt.bson != null
        mt.bson.foo instanceof Document
        mt.bson.foo.embedded == 'bar'
        mt.binary.data == 'foo'.bytes
        mt.otherId == otherId
    }
}

@Entity
class MongoTypes {
    ObjectId id
    Document bson
    Binary binary
    ObjectId otherId
}
