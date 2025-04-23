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

class AddToMethodWithBasicCollectionSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8779')
    void "Test that the addTo* method works with basic collections"() {
         when:"A book is saved with a basic collection"
            def book = new BasicBook(title: "DGG")
            book.addToAuthors("Graeme")
                .addToAuthors("Jeff")
                .save(flush:true)

            session.clear()

            book = BasicBook.get(book.id)
        then:"The model is saved correctly"
            book.title == "DGG"
            book.authors.size() == 2
            book.authors.contains "Graeme"
            book.authors.contains "Jeff"
    }

    @Override
    List getDomainClasses() {
        [BasicBook]
    }
}

@Entity
class BasicBook {

    Long id
    static hasMany = [authors:String]

    Set<String> authors
    String title
}
