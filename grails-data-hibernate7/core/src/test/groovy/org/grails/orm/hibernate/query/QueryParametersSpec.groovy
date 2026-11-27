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
package org.grails.orm.hibernate.query

import spock.lang.Specification
import spock.lang.Unroll

class QueryParametersSpec extends Specification {

    void "creates query parameters with defensive copies and conversions"() {
        given:
        def named = [str: new StringBuilder("hello"), list: [new StringBuilder("item1")]]
        def positional = [new StringBuilder("first"), 42]

        when:
        def params = QueryParameters.of(named, positional)

        then:
        params.hasNamed()
        params.hasPositional()
        !params.isEmpty()
        params.named() == [str: "hello", list: ["item1"]]
        params.positional() == ["first", 42]

        when: "mutating original collections"
        named.put("newKey", "val")
        positional.add("extra")

        then: "QueryParameters collections remain unchanged"
        !params.named().containsKey("newKey")
        params.positional().size() == 2
    }

    void "expands GString into named parameters"() {
        given:
        def title = "The Hobbit"
        def minPages = 300
        CharSequence query = "from Book where title = ${title} and pages >= ${minPages}"

        when:
        def expanded = QueryParameters.expand(query, null, null, false)

        then:
        expanded.query() == "from Book where title = :p0 and pages >= :p1"
        expanded.parameters().hasNamed()
        !expanded.parameters().hasPositional()
        expanded.parameters().named() == [p0: "The Hobbit", p1: 300]
    }

    void "expands GString into positional parameters for HQL"() {
        given:
        def title = "The Hobbit"
        def minPages = 300
        CharSequence query = "from Book where title = ${title} and pages >= ${minPages}"
        def positional = ["seed"]

        when:
        def expanded = QueryParameters.expand(query, null, positional, false)

        then:
        expanded.query() == "from Book where title = ?2 and pages >= ?3"
        expanded.parameters().hasPositional()
        expanded.parameters().positional() == ["seed", "The Hobbit", 300]
    }

    void "expands GString into positional parameters for native SQL"() {
        given:
        def title = "The Hobbit"
        CharSequence query = "SELECT * FROM book WHERE title = ${title}"
        def positional = ["seed"]

        when:
        def expanded = QueryParameters.expand(query, null, positional, true)

        then:
        expanded.query() == "SELECT * FROM book WHERE title = ?"
        expanded.parameters().positional() == ["seed", "The Hobbit"]
    }

    void "expands GString with names containing select keyword"() {
        given:
        def selected = "active"
        def selectionVal = 10
        CharSequence query = "from Account where selectedStatus = ${selected} and selectCount > ${selectionVal}"

        when:
        def expanded = QueryParameters.expand(query, null, null, false)

        then:
        expanded.query() == "from Account where selectedStatus = :p0 and selectCount > :p1"
        expanded.parameters().named() == [p0: "active", p1: 10]
    }

    void "expands GString query with leading with clause"() {
        given:
        def limitVal = 50
        CharSequence query = "with sub as (select id from Item) select i from Item i where i.qty > ${limitVal}"

        when:
        def expanded = QueryParameters.expand(query, null, null, false)

        then:
        expanded.query() == "with sub as (select id from Item) select i from Item i where i.qty > :p0"
        expanded.parameters().named() == [p0: 50]
    }

    void "expands GString query containing escaped quotes"() {
        given:
        def authorName = "O'Connor"
        CharSequence query = "from Author a where a.bio like '%\\'quote\\'' and a.name = ${authorName}"

        when:
        def expanded = QueryParameters.expand(query, null, null, false)

        then:
        expanded.query() == "from Author a where a.bio like '%\\'quote\\'' and a.name = :p0"
        expanded.parameters().named() == [p0: "O'Connor"]
    }

    void "normalizes multiline query strings"() {
        given:
        def multiline = "from Book b\nwhere b.pages > 100\norder by b.title"

        when:
        def normalized = QueryParameters.normalizeMultiLineQueryString(multiline)

        then:
        normalized == "from Book b where b.pages > 100 order by b.title"
    }

    @Unroll
    void "convertValue handles various types correctly for #input"() {
        expect:
        QueryParameters.convertValue(input) == expected

        where:
        input                               | expected
        null                                | null
        "plain string"                      | "plain string"
        new StringBuilder("string builder") | "string builder"
        [new StringBuilder("a"), 123]       | ["a", 123]
        new CharSequence[]{new StringBuilder("x"), "y"} | ["x", "y"] as String[]
        [1, 2, 3] as int[]                  | [1, 2, 3] as int[]
    }
}
