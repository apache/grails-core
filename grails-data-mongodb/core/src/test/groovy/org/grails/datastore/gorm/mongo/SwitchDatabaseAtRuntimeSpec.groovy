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

import grails.gorm.tests.Person
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author Graeme Rocher
 */
class SwitchDatabaseAtRuntimeSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Person])
    }

    void setup() {
        manager.session.nativeInterface.getDatabase('thesimpsons').drop()
    }

    void "Test switch database at runtime"() {
        given: "Some test data"
        createPeople()
        def initialDb = Person.DB.name

        when: "A count is issued"
        int total = Person.count()

        then: "The result is correct"
        total == 6

        when: "We switch to another database"
        def previous = Person.useDatabase("thesimpsons")

        then: "The count is now 0"
        Person.count() == 0
        Person.DB.name == 'thesimpsons'

        when: "We save a new person"
        new Person(firstName: "Maggie", lastName: "Simpson").save(flush: true)

        then: "The count is now 1"
        Person.count() == 1
        Person.DB.name == 'thesimpsons'


        when: "we switch back all is good"
        Person.useDatabase(previous)

        then: "the people count is 6 again"
        Person.count() == 6
        Person.DB.name == initialDb
    }


    protected void createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson").save()
        new Person(firstName: "Marge", lastName: "Simpson").save()
        new Person(firstName: "Bart", lastName: "Simpson").save()
        new Person(firstName: "Lisa", lastName: "Simpson").save()
        new Person(firstName: "Barney", lastName: "Rubble").save()
        new Person(firstName: "Fred", lastName: "Flinstone").save()
    }
}
