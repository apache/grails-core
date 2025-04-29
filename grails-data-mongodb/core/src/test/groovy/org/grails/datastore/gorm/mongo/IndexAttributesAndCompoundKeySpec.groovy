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

import com.mongodb.WriteConcern
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 25/03/14.
 */
class IndexAttributesAndCompoundKeySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([ServerStream])
    }

    @Issue('GPMONGODB-359')
    void "Test that a compound index works"() {
        expect: "No exceptions on startup"
        ServerStream.count() == 0

        ServerStream.collection.listIndexes()[1].key == [server: 1, stream: 1]
        ServerStream.collection.listIndexes()[1].unique

    }
}


@Entity
class ServerStream {
    ObjectId id
    Long version
    String server
    String stream
    Boolean fBackfill = false

    static mapping = {
        version false
        compoundIndex server: 1, stream: 1, indexAttributes: [unique: true, dropDups: true]
        writeConcern WriteConcern.ACKNOWLEDGED
    }
}