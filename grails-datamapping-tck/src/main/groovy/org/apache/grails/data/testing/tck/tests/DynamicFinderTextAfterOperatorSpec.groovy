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

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.TestEntity
import spock.lang.Issue
import spock.lang.Unroll

/**
 * Nothing may follow the operator of a dynamic finder expression. An OrderBy suffix used to be dropped, so the finder
 * ran without the sort on every datastore; it is refused now, and the documented sort and order parameters work.
 */
@Issue('https://github.com/apache/grails-core/issues/14605')
class DynamicFinderTextAfterOperatorSpec extends GrailsDataTckSpec {

    @Unroll
    void "a dynamic finder with text after its operator is refused: #finder"() {
        when:
        TestEntity."$finder"(*arguments)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('sort and order arguments')

        where:
        finder                                | arguments
        'findAllByAgeGreaterThanOrderByName'  | [0]
        'findAllByAgeInListOrderByName'       | [[30]]
        'findAllByAgeInListOrderByNameDesc'   | [[30]]
        'countByNameLikeFoo'                  | ['a%']
    }

    @Unroll
    void "a dynamic finder that ends with a joining operator is refused: #finder"() {
        given:
        new TestEntity(name: 'Ada', age: 30).save(flush: true, failOnError: true)

        when:
        TestEntity."$finder"(*arguments)

        then: 'it does not run as if the operator were not there'
        IllegalArgumentException e = thrown()
        e.message.contains('operator')

        where:
        finder                          | arguments
        'findAllByNameAndAgeInListAnd'  | ['Ada', [30]]
        'findAllByNameAndAgeAnd'        | ['Ada', 30]
        'findAllByNameOrAgeOr'          | ['Ada', 30]
        'countByNameAndAgeInListAnd'    | ['Ada', [30]]
    }

    void "the sort and order parameters sort a dynamic finder"() {
        given:
        new TestEntity(name: 'cat').save(failOnError: true)
        new TestEntity(name: 'ann').save(failOnError: true)
        new TestEntity(name: 'bob').save(flush: true, failOnError: true)

        expect:
        TestEntity.findAllByAgeInList([30], [sort: 'name'])*.name == ['ann', 'bob', 'cat']
        TestEntity.findAllByAgeInList([30], [sort: 'name', order: 'desc'])*.name == ['cat', 'bob', 'ann']
    }

    void "text after Equal is part of the property name, so it never finds the row of the property before it"() {
        given:
        new TestEntity(name: 'Ada').save(flush: true, failOnError: true)

        when:
        Object found = null
        Exception refused = null
        try {
            found = TestEntity.findByNameEqualIgnoreCasex('Ada')
        }
        catch (Exception e) {
            refused = e
        }

        then: 'Equal is the operator a finder has without writing it, so the text is read as the name of a property: a datastore that refuses an unknown field names it, one that allows it finds nothing'
        found == null
        refused == null || refused.message?.contains('nameEqualIgnoreCasex')
    }
}
