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
import groovy.transform.EqualsAndHashCode
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 22/04/14.
 */
class MapOfDomainsSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Smartphones])
    }

    void "Test that a map of embedded objects can be persisted"() {
        when: "A domain class with a map of embedded objects is persisted"
        def phones = new Smartphones()

        def data = [apple: new Smartphone(name: "iPhone"), samsung: new Smartphone(name: "Galaxy")]
        phones.phonesByManufacturer = data
        phones.save(flush: true)
        manager.session.clear()
        phones = Smartphones.get(phones.id)

        then: "The results are correct"
        phones.phonesByManufacturer == data

    }
}

@Entity
class Smartphones {
    ObjectId id
    Map<String, Smartphone> phonesByManufacturer

    static embedded = ['phonesByManufacturer']
}

@EqualsAndHashCode
class Smartphone {
    String name
}
