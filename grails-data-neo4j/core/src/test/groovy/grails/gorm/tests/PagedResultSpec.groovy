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

import grails.gorm.PagedResultList
import spock.lang.Ignore

/**
 *
 */
class PagedResultSpec extends GormDatastoreSpec{
    @Override
    List getDomainClasses() {
        [Person]
    }
//@Ignore("temprary disabled due to implicit required sorting based on Comparable")
    void "Test that a paged result list is returned from the list() method with pagination params"() {
        given:"Some people"
            createPeople()

        when:"The list method is used with pagination params"
            def results = Person.list(offset:2, max:2, sort: 'firstName')

        then:"You get a paged result list back"
            results instanceof PagedResultList
            results.size() == 2
            results[0].firstName == "Fred"
            results[1].firstName == "Homer"
            results.totalCount == 6

    }

    //@Ignore("temporary disabled due to undefined sorting order")
    void "Test that a paged result list is returned from the critera with pagination params"() {
        given:"Some people"
            createPeople()

        when:"The list method is used with pagination params"
            def results = Person.createCriteria().list(offset:1, max:2, sort:'firstName') {
                eq 'lastName', 'Simpson'
            }

        then:"You get a paged result list back"
            results instanceof PagedResultList
            results.size() == 2
            results[0].firstName == "Homer"
            results[1].firstName == "Lisa"
            results.totalCount == 4

    }


    protected def createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson", age:45).save()
        new Person(firstName: "Marge", lastName: "Simpson", age:40).save()
        new Person(firstName: "Bart", lastName: "Simpson", age:9).save()
        new Person(firstName: "Lisa", lastName: "Simpson", age:7).save()
        new Person(firstName: "Barney", lastName: "Rubble", age:35).save()
        new Person(firstName: "Fred", lastName: "Flinstone", age:41).save()
    }

}
