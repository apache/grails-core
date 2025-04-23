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

package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class CacheAndJoinSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8758')
    void "Test that the cache and join methods can be used in a test"() {
        given:"Some test data"
            new Author(name: "Bob").save flush:true
            session.clear()
        when:"The cache and join methods are used in criteria"
            def a = Author.createCriteria().get {
                eq 'name', "Bob"
                join 'books'
                maxResults 1
                cache true
            }

        then:"Results are returned"
            a != null
    }

    @Override
    List getDomainClasses() {
        [Author, Book]
    }
}

@Entity
class Author {
    Long id
    String name

    static hasMany = [books: Book]

    static constraints = {
        name blank: false
    }
}
