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
import org.bson.types.ObjectId
import spock.lang.Requires

/**
 * @author Graeme Rocher
 */
@Requires({
    System.getenv().get('CI') as Boolean
})
class ReadManyObjectsSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([ProfileDoc])
    }

    void "Test that reading thousands of objects doesn't run out of memory"() {
        given: "A lot of test data"
        createData()

        when: "The data is read"
        long took = 30000
        final now = System.currentTimeMillis()
        for (p in ProfileDoc.list()) {
            println p.n1
        }
        final then = System.currentTimeMillis()
        took = then - now
        println "Took ${then - now}ms"

        then: "Check that it doesn't take too long"
        took < 30000
    }

    void "Test that reading thousands of objects doesn't run out of memory native query"() {
        given: "A lot of test data"
        createData()

        when: "The data is read"
        final now = System.currentTimeMillis()
        final cursor = ProfileDoc.collection.find()
        for (p in cursor) {
            println p.n1
        }
        final then = System.currentTimeMillis()
        long took = then - now
        println "Took ${then - now}ms"

        then: "If it gets to this point we "
        took < 30000

    }

    void createData() {
        ProfileDoc.collection.drop()
        100000.times {
            ProfileDoc.collection.insertOne(new Document(n1: "Plane $it".toString(), n2: it, n3: it.toLong(), date: new Date()))
        }
    }
}

@Entity
class ProfileDoc {
    ObjectId id
    String n1
    Integer n2
    Long n3
    Date date

    static mapping = {
        stateless true
    }
}
