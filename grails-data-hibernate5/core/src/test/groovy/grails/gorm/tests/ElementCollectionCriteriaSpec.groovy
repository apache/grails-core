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

@Issue('https://github.com/apache/grails-core/issues/14374')
class ElementCollectionCriteriaSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EcContact)
    }

    void setup() {
        new EcContact(name: 'a', kinds: [EcKind.Email, EcKind.Phone]).save(flush: true)
        new EcContact(name: 'b', kinds: [EcKind.None]).save(flush: true)
        session.clear()
    }

    void "a block over a collection of enums restricts its elements with eq"() {
        expect:
        EcContact.createCriteria().list { kinds { eq('elements', EcKind.Email) } }*.name == ['a']
        EcContact.createCriteria().list { kinds { eq('elements', EcKind.None) } }*.name == ['b']
    }

    void "a block over a collection of enums restricts its elements with in"() {
        expect:
        EcContact.createCriteria().list { kinds { 'in'('elements', [EcKind.Phone, EcKind.None]) } }*.name.sort() == ['a', 'b']
    }

    void "two restrictions in a block over a collection of enums apply to one element"() {
        expect: 'no element is both of them, though the contact has one of each'
        EcContact.createCriteria().list {
            kinds {
                eq('elements', EcKind.Email)
                eq('elements', EcKind.Phone)
            }
        }.isEmpty()
    }

    void "an in and an eq in a block over a collection of enums apply to one element"() {
        expect: 'the Phone element is in the list and is the one eq asks for'
        EcContact.createCriteria().list {
            kinds {
                'in'('elements', [EcKind.Email, EcKind.Phone])
                eq('elements', EcKind.Phone)
            }
        }*.name == ['a']

        and: 'a contact with an Email element and a None element has no element that is both in the list and None'
        new EcContact(name: 'c', kinds: [EcKind.Email, EcKind.None]).save(flush: true)
        session.clear()
        EcContact.createCriteria().list {
            kinds {
                'in'('elements', [EcKind.Email, EcKind.Phone])
                eq('elements', EcKind.None)
            }
        }.isEmpty()
    }

    void "the block combines with other criteria"() {
        expect:
        EcContact.createCriteria().list {
            eq('name', 'b')
            kinds { eq('elements', EcKind.Email) }
        }.isEmpty()
    }

    void "a property other than elements is rejected"() {
        when:
        EcContact.createCriteria().list { kinds { eq('id', EcKind.Email) } }

        then:
        thrown(Exception)
    }
}

enum EcKind { None, Email, Phone }

@Entity
class EcContact {
    String name
    static hasMany = [kinds: EcKind]
}
