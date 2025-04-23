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

class CustomSequenceIdentifierSpec extends GormDatastoreSpec {

    void "Test sequence identifiers"() {
        when:"when a book with a sequence id is saved"
            new Book(title:"Blah").save(flush:true)
            session.clear()
            def b = Book.findByTitle("Blah")
        then:"It can be retrieved"
            b != null
            b.id != null
    }

    @Override
    List getDomainClasses() {
        [Book]
    }
}

@Entity
class Book {

    Long id
    String title

    static mapping = {
        id generator:'sequence', params:[sequence:'book_seq']
    }
}
