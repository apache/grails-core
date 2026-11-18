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

package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

class CollectionCriterionAssociationBlockSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(ColCritPerson, ColCritPet)
    }

    def setup() {
        def rex = new ColCritPet(name: "Rex")
        def spot = new ColCritPet(name: "Spot")
        def alice = new ColCritPerson(name: "Alice")
        alice.addToPets(rex)
        alice.addToPets(spot)
        alice.save(failOnError: true, flush: true)

        def bob = new ColCritPerson(name: "Bob")
        bob.save(failOnError: true, flush: true)
    }

    void "top level criteria: association block then isNotEmpty"() {
        when:
        def results = ColCritPerson.createCriteria().list {
            pets {
                eq("name", "Rex")
            }
            isNotEmpty("pets")
        }

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "top level criteria: isNotEmpty then association block"() {
        when:
        def results = ColCritPerson.createCriteria().list {
            isNotEmpty("pets")
            pets {
                eq("name", "Rex")
            }
        }

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "nested criteria: association block then isNotEmpty"() {
        when:
        def results = ColCritPet.createCriteria().list {
            owner {
                pets {
                    eq("name", "Rex")
                }
                isNotEmpty("pets")
            }
        }

        then:
        results.size() == 2
        results*.name.sort() == ["Rex", "Spot"]
    }

    void "nested criteria: isNotEmpty then association block"() {
        when:
        def results = ColCritPet.createCriteria().list {
            owner {
                isNotEmpty("pets")
                pets {
                    eq("name", "Rex")
                }
            }
        }

        then:
        results.size() == 2
        results*.name.sort() == ["Rex", "Spot"]
    }

    void "top level criteria: association block then isEmpty"() {
        when:
        def results = ColCritPerson.createCriteria().list {
            pets {
                eq("name", "Rex")
            }
            isEmpty("pets")
        }

        then:
        results.isEmpty()
    }

    void "top level criteria: association block then sizeEq"() {
        when:
        def results = ColCritPerson.createCriteria().list {
            pets {
                eq("name", "Rex")
            }
            sizeEq("pets", 2)
        }

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "nested criteria: association block then sizeGt"() {
        when:
        def results = ColCritPet.createCriteria().list {
            owner {
                pets {
                    eq("name", "Rex")
                }
                sizeGt("pets", 1)
            }
        }

        then:
        results.size() == 2
        results*.name.sort() == ["Rex", "Spot"]
    }
}

@Entity
class ColCritPerson {
    String name
    Set<ColCritPet> pets

    static hasMany = [pets: ColCritPet]
}

@Entity
class ColCritPet {
    String name
    ColCritPerson owner

    static belongsTo = [owner: ColCritPerson]
}
