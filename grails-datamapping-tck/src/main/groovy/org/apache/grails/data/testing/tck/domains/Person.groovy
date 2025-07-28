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

package org.apache.grails.data.testing.tck.domains

import grails.gorm.async.AsyncEntity
import grails.gorm.DetachedCriteria
import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import groovy.transform.EqualsAndHashCode
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@DirtyCheck
@Entity
@ApplyDetachedCriteriaTransform
//@groovy.transform.EqualsAndHashCode - breaks gorm-neo4j: TODO: http://jira.grails.org/browse/GPNEO4J-10
@EqualsAndHashCode(includes = ['firstName', 'lastName', 'age'])
class Person implements Serializable, Comparable<Person>, AsyncEntity<Person> {
    static simpsons = where {
        lastName == "Simpson"
    }

    Long id
    Long version
    String firstName
    String lastName
    Integer age = 0
    Set<Pet> pets = [] as Set
    static hasMany = [pets: Pet]
    SimpleCountry country
    Face face
    boolean myBooleanProperty

    static DetachedCriteria<Person> withAge(int a) {
        Person.where {
            age > a
        }
    }

    static DetachedCriteria<Person> peopleWithOlderPets = where {
        pets {
            age > 9
        }
    }
    static DetachedCriteria<Person> peopleWithOlderPets2 = where {
        pets.age > 9
    }

    static Person getByFirstNameAndLastNameAndAge(String firstName, String lastName, int age) {
        find(new Person(firstName: firstName, lastName: lastName, age: age))
    }

    static mapping = {
        firstName index: true
        lastName index: true
        age index: true
//        pets cascade: 'all-delete-orphan'
    }

    static constraints = {
        face nullable: true
        country nullable: true
    }

    @Override
    int compareTo(Person t) {
        age <=> t.age
    }
}
