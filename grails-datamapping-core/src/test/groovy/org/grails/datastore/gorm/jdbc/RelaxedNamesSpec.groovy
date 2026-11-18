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
package org.grails.datastore.gorm.jdbc

import spock.lang.Specification
import spock.lang.Unroll

class RelaxedNamesSpec extends Specification {

    private static Set<String> namesFor(String source) {
        new RelaxedNames(source).iterator().toList() as Set
    }

    void "dash separated names produce underscore, camelCase and case variations"() {
        expect:
        namesFor('foo-bar') == ['foo-bar', 'foo_bar', 'fooBar', 'foobar', 'FOO-BAR', 'FOO_BAR', 'FOOBAR'] as Set
    }

    void "camelCase names produce dash, underscore and lowercase/uppercase variations"() {
        expect:
        namesFor('fooBar') == ['fooBar', 'foo_bar', 'foo-bar', 'foobar', 'FOOBAR', 'FOO_BAR', 'FOO-BAR'] as Set
    }

    void "a single lowercase word only produces case variations"() {
        expect:
        namesFor('foo') == ['foo', 'FOO'] as Set
    }

    void "null name is treated as an empty string"() {
        expect:
        namesFor(null) == [''] as Set
    }

    void "empty name only produces itself"() {
        expect:
        namesFor('') == [''] as Set
    }

    @Unroll
    void "'#source' variations include '#expected'"() {
        expect:
        namesFor(source).contains(expected)

        where:
        source        | expected
        'db-create'   | 'dbCreate'
        'db-create'   | 'db_create'
        'db-create'   | 'DB-CREATE'
        'dbCreate'    | 'db-create'
        'dbCreate'    | 'db_create'
        'DB_CREATE'   | 'dbCreate'
    }

    void "iterating twice returns the same values"() {
        given:
        def names = new RelaxedNames('foo-bar')

        expect:
        names.iterator().toList() == names.iterator().toList()
    }
}
