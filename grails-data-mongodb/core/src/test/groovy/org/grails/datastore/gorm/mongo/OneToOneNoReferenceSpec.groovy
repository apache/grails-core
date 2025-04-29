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

class OneToOneNoReferenceSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([OtherNoRef, NoRef])
    }

    void "Test that associations can be saved with no dbrefs"() {
        when: "A domain class is saved that has references disabled"
        def other = new OtherNoRef().save()
        def noref = new NoRef(other: other)
        noref.save flush: true

        then: "The association is saved without a dbref"
        println NoRef.collection.find().first()
        NoRef.collection.find().first().other == other.id
    }

    void "Test that querying an association works"() {
        when: "A domain class is saved that has references disabled"
        def other = new OtherNoRef().save()
        def noref = new NoRef(other: other)
        noref.save flush: true
        manager.session.clear()

        other = OtherNoRef.get(other.id)
        noref = NoRef.findByOther(other)

        then: "The association can be queried"
        other != null
        noref != null
    }
}

@Entity
class NoRef {

    ObjectId id

    OtherNoRef other

    static mapping = {
        other reference: false
    }
}

@Entity
class OtherNoRef {

    ObjectId id
}
