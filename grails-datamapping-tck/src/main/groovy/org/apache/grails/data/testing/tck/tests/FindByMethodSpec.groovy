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

import org.apache.grails.data.testing.tck.domains.Book as TckBook
import org.apache.grails.data.testing.tck.domains.Highway
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import spock.lang.Unroll

/**
 * TCK Spec for Dynamic Finders.
 *
 * @author graemerocher
 */
class FindByMethodSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(Person, TckBook, Highway)
    }

    void 'Test Using AND Multiple Times In A Dynamic Finder'() {
        given:
        new Person(firstName: 'Jake', lastName: 'Brown', age: 11).save()
        new Person(firstName: 'Zack', lastName: 'Brown', age: 14).save()
        new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()
        new Person(firstName: 'Zack', lastName: 'Galifianakis', age: 41).save()

        when:
        def people = Person.findAllByFirstNameAndLastNameAndAge('Jeff', 'Brown', 1)

        then:
        0 == people?.size()

        when:
        people = Person.findAllByFirstNameAndLastNameAndAgeGreaterThan('Zack', 'Brown', 20)

        then:
        0 == people?.size()

        when:
        people = Person.findAllByFirstNameAndLastNameAndAgeGreaterThan('Zack', 'Brown', 8)

        then:
        1 == people?.size()
        14 == people[0].age

        when:
        def cnt = Person.countByFirstNameAndLastNameAndAge('Jake', 'Brown', 11)

        then:
        1 == cnt

        when:
        cnt = Person.countByFirstNameAndLastNameAndAgeInList('Zack', 'Brown', [12, 13, 14, 15])

        then:
        1 == cnt
    }

    void 'Test Using OR Multiple Times In A Dynamic Finder'() {
        given:
        new Person(firstName: 'Jake', lastName: 'Brown', age: 11).save()
        new Person(firstName: 'Zack', lastName: 'Brown', age: 14).save()
        new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()
        new Person(firstName: 'Zack', lastName: 'Galifianakis', age: 41).save()

        when:
        def people = Person.findAllByFirstNameOrLastNameOrAge('Zack', 'Tyler', 125)

        then:
        2 == people?.size()

        when:
        people = Person.findAllByFirstNameOrLastNameOrAge('Zack', 'Brown', 125)

        then:
        4 == people?.size()

        when:
        def cnt = Person.countByFirstNameOrLastNameOrAgeInList('Jeff', 'Wilson', [11, 41])

        then:
        3 == cnt
    }

    void testBooleanPropertyQuery() {
        given:
        new Highway(bypassed: true, name: 'Bypassed Highway').save()
        new Highway(bypassed: true, name: 'Another Bypassed Highway').save()
        new Highway(bypassed: false, name: 'Not Bypassed Highway').save()
        new Highway(bypassed: false, name: 'Another Not Bypassed Highway').save()

        when:
        def highways = Highway.findAllBypassedByName('Not Bypassed Highway')

        then:
        0 == highways.size()

        when:
        highways = Highway.findAllNotBypassedByName('Not Bypassed Highway')

        then:
        1 == highways?.size()
        'Not Bypassed Highway' == highways[0].name

        when:
        highways = Highway.findAllBypassedByName('Bypassed Highway')

        then:
        1 == highways?.size()
        'Bypassed Highway' == highways[0].name

        when:
        highways = Highway.findAllNotBypassedByName('Bypassed Highway')
        then:
        0 == highways?.size()

        when:
        highways = Highway.findAllBypassed()
        then:
        2 == highways?.size()
        highways*.name.containsAll(['Bypassed Highway', 'Another Bypassed Highway'])

        when:
        highways = Highway.findAllNotBypassed()
        then:
        2 == highways?.size()
        highways*.name.containsAll(['Not Bypassed Highway', 'Another Not Bypassed Highway'])

        when:
        def highway = Highway.findNotBypassedByName('Not Bypassed Highway')
        then:
        'Not Bypassed Highway' == highway?.name

        when:
        highway = Highway.findBypassedByName('Bypassed Highway')
        then:
        'Bypassed Highway' == highway?.name

        when:
        TckBook.newInstance(author: 'Jeff', title: 'Fly Fishing For Everyone', published: false).save()
        TckBook.newInstance(author: 'Jeff', title: 'DGGv2', published: true).save()
        TckBook.newInstance(author: 'Graeme', title: 'DGGv2', published: true).save()
        TckBook.newInstance(author: 'Dierk', title: 'GINA', published: true).save()

        def book = TckBook.findPublishedByAuthor('Jeff')
        then:
        'Jeff' == book.author
        'DGGv2' == book.title

        when:
        book = TckBook.findPublishedByAuthor('Graeme')
        then:
        'Graeme' == book.author
        'DGGv2' == book.title

        when:
        book = TckBook.findPublishedByTitleAndAuthor('DGGv2', 'Jeff')
        then:
        'Jeff' == book.author
        'DGGv2' == book.title

        when:
        book = TckBook.findNotPublishedByAuthor('Jeff')
        then:
        'Fly Fishing For Everyone' == book.title

        when:
        book = TckBook.findPublishedByTitleOrAuthor('Fly Fishing For Everyone', 'Dierk')
        then:
        'GINA' == book.title

        when:
        book = TckBook.findNotPublished()
        then:
        'Fly Fishing For Everyone' == book?.title

        when:
        def books = TckBook.findAllPublishedByTitle('DGGv2')
        then:
        2 == books?.size()

        when:
        books = TckBook.findAllPublished()
        then:
        3 == books?.size()

        when:
        books = TckBook.findAllNotPublished()
        then:
        1 == books?.size()

        when:
        books = TckBook.findAllPublishedByTitleAndAuthor('DGGv2', 'Graeme')
        then:
        1 == books?.size()

        when:
        books = TckBook.findAllPublishedByAuthorOrTitle('Graeme', 'GINA')
        then:
        2 == books?.size()

        when:
        books = TckBook.findAllNotPublishedByAuthor('Jeff')
        then:
        1 == books?.size()

        when:
        books = TckBook.findAllNotPublishedByAuthor('Graeme')
        then:
        0 == books?.size()
    }

    void "Test findOrCreateBy For A Record That Does Not Exist In The Database"() {
        when:
        def book = TckBook.findOrCreateByAuthor('Someone')

        then:
        'Someone' == book.author
        null == book.title
        null == book.id
    }

    void "Test findOrCreateBy With An AND Clause"() {
        when:
        def book = TckBook.findOrCreateByAuthorAndTitle('Someone', 'Something')

        then:
        'Someone' == book.author
        'Something' == book.title
        null == book.id
    }

    void "Test findOrCreateBy Throws Exception If An OR Clause Is Used"() {
        when:
        TckBook.findOrCreateByAuthorOrTitle('Someone', 'Something')

        then:
        thrown(MissingMethodException)
    }

    void "Test findOrSaveBy For A Record That Does Not Exist In The Database"() {
        when:
        def book = TckBook.findOrSaveByAuthorAndTitle('Some New Author', 'Some New Title')

        then:
        'Some New Author' == book.author
        'Some New Title' == book.title
        book.id != null
    }

    void "Test findOrSaveBy For A Record That Does Exist In The Database"() {

        given:
        def originalId = new TckBook(author: 'Some Author', title: 'Some Title').save().id

        when:
        def book = TckBook.findOrSaveByAuthor('Some Author')

        then:
        'Some Author' == book.author
        'Some Title' == book.title
        originalId == book.id
    }

    @Unroll
    void "Test findOrCreateBy/findOrSaveBy patterns [#index] #methodName should throw #exception.simpleName"() {
        when:
        action.call()

        then:
        thrown(exception)

        where:
        index | methodName                              | exception              | action
        // findOrCreateBy patterns
        1     | 'findOrCreateByAuthorInList'            | MissingMethodException | { TckBook.findOrCreateByAuthorInList(['Jeff']) }
        2     | 'findOrCreateByAuthorOrTitle'           | MissingMethodException | { TckBook.findOrCreateByAuthorOrTitle('Jim', 'Title') }
        3     | 'findOrCreateByAuthorNotEqual'          | MissingMethodException | { TckBook.findOrCreateByAuthorNotEqual('B') }
        4     | 'findOrCreateByAuthorGreaterThan'       | ConfigurationException | { TckBook.findOrCreateByAuthorGreaterThan('B') }
        5     | 'findOrCreateByAuthorLessThan'          | ConfigurationException | { TckBook.findOrCreateByAuthorLessThan('B') }
        6     | 'findOrCreateByAuthorBetween'           | MissingMethodException | { TckBook.findOrCreateByAuthorBetween('A', 'B') }
        7     | 'findOrCreateByAuthorGreaterThanEquals' | ConfigurationException | { TckBook.findOrCreateByAuthorGreaterThanEquals('B') }
        8     | 'findOrCreateByAuthorLessThanEquals'    | ConfigurationException | { TckBook.findOrCreateByAuthorLessThanEquals('B') }

        // findOrSaveBy patterns
        9     | 'findOrSaveByAuthorInList'              | MissingMethodException | { TckBook.findOrSaveByAuthorInList(['Jeff']) }
        10    | 'findOrSaveByAuthorOrTitle'             | MissingMethodException | { TckBook.findOrSaveByAuthorOrTitle('Jim', 'Title') }
        11    | 'findOrSaveByAuthorNotEqual'            | MissingMethodException | { TckBook.findOrSaveByAuthorNotEqual('B') }
        12    | 'findOrSaveByAuthorGreaterThan'         | ConfigurationException | { TckBook.findOrSaveByAuthorGreaterThan('B') }
        13    | 'findOrSaveByAuthorLessThan'            | ConfigurationException | { TckBook.findOrSaveByAuthorLessThan('B') }
        14    | 'findOrSaveByAuthorBetween'             | MissingMethodException | { TckBook.findOrSaveByAuthorBetween('A', 'B') }
        15    | 'findOrSaveByAuthorGreaterThanEquals'   | ConfigurationException | { TckBook.findOrSaveByAuthorGreaterThanEquals('B') }
        16    | 'findOrSaveByAuthorLessThanEquals'      | ConfigurationException | { TckBook.findOrSaveByAuthorLessThanEquals('B') }
    }
}
