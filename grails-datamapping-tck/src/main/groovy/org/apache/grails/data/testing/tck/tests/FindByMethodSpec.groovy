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
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * Combined TCK Spec for Dynamic Finders.
 * Supports legacy Hibernate 5 behavior and validated Hibernate 7+ behavior.
 */
class FindByMethodSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        // Register domains in the TCK manager to prevent NoSuchElementException
        manager.addAllDomainClasses([Person, TckBook, Highway])
    }

    // --- Standard GORM TCK Tests ---

    void 'Test Using AND Multiple Times In A Dynamic Finder'() {
        given:
        new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()

        when:
        def people = Person.findAllByFirstNameAndLastNameAndAge('Jeff', 'Brown', 41)

        then:
        1 == people?.size()
    }

    void "Test findOrCreateBy For A Record That Does Not Exist"() {
        when:
        def book = TckBook.findOrCreateByAuthor('Someone')

        then:
        'Someone' == book.author
        null == book.id
    }

    // --- Parameterized Logic ---

    @Unroll
    @IgnoreIf({ System.getProperty('hibernate7.gorm.suite') == 'true' || System.getProperty('mongodb.gorm.suite') == 'true'  })
    void "Test Hib5 pattern [#index] #methodName should throw MissingMethodException"() {
        when:
        action.call()

        then:
        thrown(MissingMethodException)

        where:
        index | methodName                                | action
        // findOrCreateBy patterns
        1     | 'findOrCreateByAuthorOrTitle'             | { TckBook.findOrCreateByAuthorOrTitle('Jim', 'Title') }
        2     | 'findOrCreateByAuthorGreaterThan'         | { TckBook.findOrCreateByAuthorGreaterThan('B') }
        3     | 'findOrCreateByAuthorLessThan'            | { TckBook.findOrCreateByAuthorLessThan('B') }
        4     | 'findOrCreateByAuthorGreaterThanEquals'   | { TckBook.findOrCreateByAuthorGreaterThanEquals('B') }
        5     | 'findOrCreateByAuthorLessThanEquals'      | { TckBook.findOrCreateByAuthorLessThanEquals('B') }
        6     | 'findOrCreateByAuthorInList'              | { TckBook.findOrCreateByAuthorInList(['Jeff']) }
        7     | 'findOrCreateByAuthorNotEqual'            | { TckBook.findOrCreateByAuthorNotEqual('B') }
        8     | 'findOrCreateByAuthorBetween'             | { TckBook.findOrCreateByAuthorBetween('A', 'B') }

        // findOrSaveBy patterns
        9     | 'findOrSaveByAuthorInList'                | { TckBook.findOrSaveByAuthorInList(['Jeff']) }
        10    | 'findOrSaveByAuthorOrTitle'               | { TckBook.findOrSaveByAuthorOrTitle('Jim', 'Title') }
        11    | 'findOrSaveByAuthorNotEqual'              | { TckBook.findOrSaveByAuthorNotEqual('B') }
        12    | 'findOrSaveByAuthorGreaterThan'           | { TckBook.findOrSaveByAuthorGreaterThan('B') }
        13    | 'findOrSaveByAuthorLessThan'              | { TckBook.findOrSaveByAuthorLessThan('B') }
        14    | 'findOrSaveByAuthorBetween'               | { TckBook.findOrSaveByAuthorBetween('A', 'B') }
        15    | 'findOrSaveByAuthorGreaterThanEquals'     | { TckBook.findOrSaveByAuthorGreaterThanEquals('B') }
        16    | 'findOrSaveByAuthorLessThanEquals'        | { TckBook.findOrSaveByAuthorLessThanEquals('B') }
    }

    @Unroll
    @IgnoreIf({ System.getProperty('hibernate5.gorm.suite') == 'true' || System.getProperty('mongodb.gorm.suite') == 'true' })
    void "Test Hib7 pattern [#index] #methodName should throw #exception.simpleName"() {
        when:
        action.call()

        then:
        thrown(exception)

        where:
        index | methodName                                | exception              | action
        // findOrCreateBy patterns
        1     | 'findOrCreateByAuthorOrTitle'             | MissingMethodException | { TckBook.findOrCreateByAuthorOrTitle('Jim', 'Title') }
        2     | 'findOrCreateByAuthorGreaterThan'         | ConfigurationException | { TckBook.findOrCreateByAuthorGreaterThan('B') }
        3     | 'findOrCreateByAuthorLessThan'            | ConfigurationException | { TckBook.findOrCreateByAuthor_LessThan('B') }
        4     | 'findOrCreateByAuthorGreaterThanEquals'   | ConfigurationException | { TckBook.findOrCreateByAuthorGreaterThanEquals('B') }
        5     | 'findOrCreateByAuthorLessThanEquals'      | ConfigurationException | { TckBook.findOrCreateByAuthorLessThanEquals('B') }
        6     | 'findOrCreateByAuthorInList'              | MissingMethodException | { TckBook.findOrCreateByAuthorInList(['Jeff']) }
        7     | 'findOrCreateByAuthorNotEqual'            | MissingMethodException | { TckBook.findOrCreateByAuthorNotEqual('B') }
        8     | 'findOrCreateByAuthorBetween'             | MissingMethodException | { TckBook.findOrCreateByAuthorBetween('A', 'B') }

        // findOrSaveBy patterns
        9     | 'findOrSaveByAuthorInList'                | MissingMethodException | { TckBook.findOrSaveByAuthorInList(['Jeff']) }
        10    | 'findOrSaveByAuthorOrTitle'               | MissingMethodException | { TckBook.findOrSaveByAuthorOrTitle('Jim', 'Title') }
        11    | 'findOrSaveByAuthorNotEqual'              | MissingMethodException | { TckBook.findOrSaveByAuthorNotEqual('B') }
        12    | 'findOrSaveByAuthorGreaterThan'           | ConfigurationException | { TckBook.findOrSaveByAuthorGreaterThan('B') }
        13    | 'findOrSaveByAuthorLessThan'              | ConfigurationException | { TckBook.findOrSaveByAuthorLessThan('B') }
        14    | 'findOrSaveByAuthorBetween'               | MissingMethodException | { TckBook.findOrSaveByAuthorBetween('A', 'B') }
        15    | 'findOrSaveByAuthorGreaterThanEquals'     | ConfigurationException | { TckBook.findOrSaveByAuthorGreaterThanEquals('B') }
        16    | 'findOrSaveByAuthorLessThanEquals'        | ConfigurationException | { TckBook.findOrSaveByAuthorLessThanEquals('B') }
    }
}
