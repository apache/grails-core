package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class InOperatorWithAssociationsSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-core/issues/9279')
    void "Test query association using in operator in where query"() {
        setup: "Creating authors and books."
        InAuthor adams = new InAuthor(name: "Douglas Adams").save(failOnError: 'true')
        InAuthor meyerhoff = new InAuthor(name: "Joachim Meyerhoff").save(failOnError: 'true')
        new InBook(name: 'Per Anhalter durch die Galaxis', author: adams).save(failOnError: 'true')
        new InBook(name: 'Wann wird es endlich wieder so, wie es nie war', author: meyerhoff).save(failOnError: 'true', flush:true)
        def authors = [adams, meyerhoff]
        session.clear()

        when: "Getting books by list of authors."
        List<InBook> books = InBook.where { author in authors }.list()

        then: "The service will find them"
        books.size() == 2

    }

    @Override
    List getDomainClasses() {
        [InAuthor, InBook]
    }
}
@Entity
class InAuthor {
    Long id
    String name
}
@Entity
class InBook {
    Long id
    String name
    InAuthor author
}
