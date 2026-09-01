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
import org.bson.Document

/**
 * @author Graeme Rocher
 */
class SwitchDatabaseAtRuntimeSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Person)
    }

    void setup() {
        manager.session.nativeInterface.getDatabase('thesimpsons').drop()
        Person.DB.getCollection('simpsonsOnly').drop()
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


    void "Test withDatabase runs the closure against the given database then restores the original"() {
        given: "Some test data"
        createPeople()
        def initialDb = Person.DB.name

        when: "withDatabase runs a closure against another database"
        def dbNameSeenInClosure
        def result = Person.withDatabase("thesimpsons") { db ->
            dbNameSeenInClosure = db.name
            new Document([firstName: "Maggie", lastName: "Simpson"])
                    .with { doc -> db.getCollection(Person.collection.namespace.collectionName).insertOne(doc) }
            "closure result"
        }

        then: "the closure ran against the other database and its result is returned"
        dbNameSeenInClosure == 'thesimpsons'
        result == "closure result"

        and: "the original database is restored once the closure completes"
        Person.DB.name == initialDb
        Person.count() == 6
    }

    void "Test useCollection switches the collection until switched back"() {
        given: "Some test data, flushed so nothing is left pending when the collection switches"
        createPeople()
        Person.withSession { it.flush() }
        def initialCollection = Person.collection.namespace.collectionName

        when: "we switch to another collection"
        def previous = Person.useCollection("simpsonsOnly")

        then: "the new collection is empty and now current"
        Person.collection.namespace.collectionName == 'simpsonsOnly'
        Person.collection.countDocuments() == 0

        when: "we save a new person"
        new Person(firstName: "Maggie", lastName: "Simpson").save(flush: true)

        then: "it is visible in the new collection"
        Person.collection.namespace.collectionName == 'simpsonsOnly'
        Person.collection.countDocuments() == 1

        when: "we switch back"
        Person.useCollection(previous)

        then: "the original collection and its data are current again"
        Person.collection.namespace.collectionName == initialCollection
        Person.collection.countDocuments() == 6
    }

    void "Test withCollection runs the closure against the given collection then restores the original"() {
        given: "Some test data"
        createPeople()
        def initialCollection = Person.collection.namespace.collectionName

        when: "withCollection runs a closure against another collection"
        def collectionNameSeenInClosure
        def result = Person.withCollection("simpsonsOnly") { coll ->
            collectionNameSeenInClosure = coll.namespace.collectionName
            coll.insertOne(new Document([firstName: "Maggie", lastName: "Simpson"]))
            "closure result"
        }

        then: "the closure ran against the other collection and its result is returned"
        collectionNameSeenInClosure == 'simpsonsOnly'
        result == "closure result"

        and: "the original collection is restored once the closure completes, unaffected by the write"
        Person.collection.namespace.collectionName == initialCollection
        Person.count() == 6
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
