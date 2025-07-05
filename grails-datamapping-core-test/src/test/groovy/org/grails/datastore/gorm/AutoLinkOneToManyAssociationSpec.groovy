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

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

class AutoLinkOneToManyAssociationSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([AutoLinkListAuthor, AutoLinkListBook])
    }

    @Issue('GRAILS-8815')
    void "Test that associations are linked automatically when saving"() {
        given: "A new domain class with a one-to-many association"
        def author = new AutoLinkListAuthor(firstName: 'foo', lastName: 'bar')
        when: "The domain is saved"
        author.save()
        then: "The association is intially empty"
        author.id != null
        author.books == null

        when: "An associated object is added"
        def book1 = new AutoLinkListBook(title: 'grails', price: 43, published: new Date(), author: author)

        // add the book to the author to complete the other side
        author.addToBooks(book1)
        then: "The relationship size is correct"
        author.books.size() == 1

        when: "The domain is saved"
        author.save()
        then: "The relationship size is still correct"
        author.books.size() == 1
    }
}

@Entity
class AutoLinkListAuthor {
    Long id
    String firstName
    String lastName

    // Hi, see here!
    List books

    static hasMany = [books: AutoLinkListBook]
}

@Entity
class AutoLinkListBook {
    Long id
    String title
    Date published
    BigDecimal price

    static belongsTo = [author: AutoLinkListAuthor]
}
