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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Issue

/**
 * Created by graemerocher on 15/08/2017.
 */
class OrphanDeleteSpec extends GormDatastoreSpec {


    @Issue('https://github.com/grails/gorm-neo4j/issues/6')
    void "test cascade delete orphan results in removing orphaned nodes"() {
        when:
        new Contact()
                .addToPhones(phoneNumber: "1234")
                .addToPhones(phoneNumber: "4567")
                .save(flush:true)

        then:
        Contact.count == 1
        Phone.count == 2

        when:"The child is removed from the parent"
        Contact c = Contact.first()
        Phone phone = c.phones.find() { it.phoneNumber == '1234'}
        c.phones.remove(phone)
        c.save(flush:true)
        session.clear()

        then:"The child was also deleted"
        Contact.count == 1
        Contact.first().phones.size() == 1
        Contact.first().phones.first().phoneNumber == '4567'
        Phone.count == 1
    }

    @Override
    List getDomainClasses() {
        [Contact, Phone]
    }
}

@Entity
class Contact {
    static hasMany = [phones: Phone]
    static mapping = {
        phones cascade: "all-delete-orphan"
    }
}

@Entity
class Phone {
    String phoneNumber

    static belongsTo = Person
}