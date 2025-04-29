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

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests for using subqueries in criteria and where method calls
 *
 */
class SubquerySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    def "Test subquery with projection and criteria with closure"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people above a certain age average"
        def results = Person.withCriteria {
            gt "age", {
                projections {
                    avg "age"
                }
            }

            order "firstName"
        }

        then: "the correct results are returned"
        results.size() == 4
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
        results[2].firstName == "Homer"
        results[3].firstName == "Marge"
    }

    def "Test subquery with projection and criteria"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people above a certain age average"
        def results = Person.withCriteria {
            gt "age", new DetachedCriteria(Person).build {
                projections {
                    avg "age"
                }
            }

            order "firstName"
        }

        then: "the correct results are returned"
        results.size() == 4
        results[0].firstName == "Barney"
        results[1].firstName == "Fred"
        results[2].firstName == "Homer"
        results[3].firstName == "Marge"
    }

    def "Test subquery that returned multiple results and criteria"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people above a certain age average"
        def results = Person.withCriteria {
            gtAll "age", new DetachedCriteria(Person).build {
                projections {
                    property "age"
                }
                between 'age', 5, 39
            }

            order "firstName"
        }

        then: "the correct results are returned"
        results.size() == 3
        results[0].firstName == "Fred"
        results[1].firstName == "Homer"
        results[2].firstName == "Marge"
    }

    def "Test subquery that returned multiple results and criteria using a closure"() {
        given: "A bunch of people"
        createPeople()

        when: "We query for people above a certain age average"
        def results = Person.withCriteria {
            gtAll "age", {
                projections {
                    property "age"
                }
                between 'age', 5, 39
            }

            order "firstName"
        }

        then: "the correct results are returned"
        results.size() == 3
        results[0].firstName == "Fred"
        results[1].firstName == "Homer"
        results[2].firstName == "Marge"
    }

    protected def createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson", age: 45).save()
        new Person(firstName: "Marge", lastName: "Simpson", age: 40).save()
        new Person(firstName: "Bart", lastName: "Simpson", age: 9).save()
        new Person(firstName: "Lisa", lastName: "Simpson", age: 7).save()
        new Person(firstName: "Barney", lastName: "Rubble", age: 35).save()
        new Person(firstName: "Fred", lastName: "Flinstone", age: 41).save()
    }
}
