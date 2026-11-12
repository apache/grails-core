/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import spock.lang.IgnoreIf

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * The criteria guide documents an association block as one branch of {@code or { }}: "accounts that have either
 * performed transactions in the last 10 days OR have been recently created". An entity without the association is
 * therefore found by the other branch, and a branch that is an association block still asks for an associated
 * entity. MongoDB has no association blocks. Neo4j is skipped until its fix, #16836, is merged; that change removes
 * the skip.
 */
@IgnoreIf({ System.getProperty('mongodb.gorm.suite') == 'true' || System.getProperty('neo4j.gorm.suite') == 'true' })
class CriteriaOrAssociationBlockSpec extends GrailsDataTckSpec {

    private void createEntities() {
        new TestEntity(name: 'with', child: new ChildEntity(name: 'kid')).save(flush: true)
        new TestEntity(name: 'other', child: new ChildEntity(name: 'stranger')).save(flush: true)
        new TestEntity(name: 'orphan').save(flush: true)
    }

    void 'an association block in or keeps the entities without the association that another branch matches'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
        }

        then:
        found*.name.sort() == ['orphan', 'with']
    }

    void 'an association block in or finds the entities with the association when no other branch matches'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                child { eq('name', 'kid') }
                eq('name', 'nobody')
            }
        }

        then:
        found*.name == ['with']
    }

    void 'a null test in an association block in or does not match the entities without the association'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                child { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then:
        found.isEmpty()
    }

    void 'an empty association block in or still requires an associated entity'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                child {}
                eq('name', 'orphan')
            }
        }

        then: 'the block adds no branch, and the entity without an associated entity is left out, as it always was'
        found.isEmpty()
    }

    void 'a block outside or that follows a block in or still requires an associated entity'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
            child { isNull('name') }
        }

        then:
        found.isEmpty()
    }

    void 'a block nested in and inside or still requires an associated entity'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                and {
                    child { isNull('name') }
                }
                eq('name', 'nobody')
            }
        }

        then:
        found.isEmpty()
    }

    void 'an association block outside or still excludes the entities without the association'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            child { eq('name', 'kid') }
        }

        then:
        found*.name == ['with']
    }

    void 'a to-many association block in or keeps the entities without any'() {
        given:
        Person owner = new Person(firstName: 'Fred', lastName: 'Flintstone')
        owner.addToPets(new Pet(name: 'rex'))
        owner.save(flush: true)
        new Person(firstName: 'Barney', lastName: 'Rubble').save(flush: true)
        Person other = new Person(firstName: 'Wilma', lastName: 'Flintstone')
        other.addToPets(new Pet(name: 'tom'))
        other.save(flush: true)

        when:
        def found = Person.withCriteria {
            or {
                pets { eq('name', 'rex') }
                eq('firstName', 'Barney')
            }
        }

        then:
        found*.firstName.sort() == ['Barney', 'Fred']
    }
}
