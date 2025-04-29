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
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class GetAllWithStringIdSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([GetItem])
    }

    @Issue('GPMONGODB-278')
    void "Test that getAll returns the correct items"() {

        when: "domains with String ids are saved"
        def id1 = new GetItem(title: "Item 1").save(flush: true, failOnError: true).id
        def id2 = new GetItem(title: "Item 2").save(flush: true, failOnError: true).id
        def id3 = new GetItem(title: "Item 3").save(flush: true, failOnError: true).id
        def id4 = new GetItem(title: "Item 4").save(flush: true, failOnError: true).id
        def id5 = new GetItem(title: "Item 5").save(flush: true, failOnError: true).id
        def id6 = new GetItem(title: "Item 6").save(flush: true, failOnError: true).id
        def id7 = new GetItem(title: "Item 7").save(flush: true, failOnError: true).id
        def id8 = new GetItem(title: "Item 8").save(flush: true, failOnError: true).id

        then: "The ids are strings and can be queried"
        id5.class == String.class
        id6.class == String.class
        id7.class == String.class
        GetItem.get(id5).id
        GetItem.get(id6).id
        GetItem.get(id7).id
        GetItem.findAllById(id5).id
        GetItem.findAllByIdInList([id5, id6, id7]).size() == 3
        GetItem.getAll(id5).size() == 1
        GetItem.getAll(id5, id6, id7).size() == 3
        GetItem.getAll([id5, id6, id7]).size() == 3
    }
}

@Entity
class GetItem {
    String id
    String title

    static constraints = {
    }
}
