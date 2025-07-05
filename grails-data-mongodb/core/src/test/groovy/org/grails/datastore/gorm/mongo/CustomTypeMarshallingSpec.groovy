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

import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

class CustomTypeMarshallingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Person])
    }

    void "Test basic crud with custom types"() {
        given: "A custom type registered for the Birthday class"
        final now = new Date()
        def p = new Person(name: "Fred", birthday: new Birthday(now))
        p.save(flush: true)
        manager.session.clear()

        when: "We query the person"
        p = Person.findByName("Fred")

        then: "The birthday is returned"
        p != null
        p.name == "Fred"
        p.birthday != null

        when: "We query with a custom type"
        p = Person.findByBirthday(new Birthday(now))

        then:
        p != null

        when: "A range query is executed"

        p = Person.findByBirthdayBetween(new Birthday(now - 1), new Birthday(now + 1))
        def p2 = Person.findByBirthdayBetween(new Birthday(now + 1), new Birthday(now + 2))

        then:
        p != null
        p2 == null
    }
}

@Entity
class Person implements MongoEntity<Person> {
    ObjectId id
    String name
    Birthday birthday
}

class Birthday implements Comparable {
    Date date

    Birthday(Date date) {
        this.date = date
    }

    int compareTo(t) {
        date.compareTo(t.date)
    }
}
