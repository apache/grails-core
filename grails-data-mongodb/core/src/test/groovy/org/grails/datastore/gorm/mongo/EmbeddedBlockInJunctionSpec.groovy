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

package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.Unroll

/**
 * A block on an embedded property is a condition of the junction it stands in. It was reported that the or beside
 * such a block was dropped and its branches were ANDed with the rest.
 */
@Issue('https://github.com/apache/grails-core/issues/14664')
class EmbeddedBlockInJunctionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([EmbeddedHolder, EmbeddedDetail])
    }

    private void seed() {
        new EmbeddedHolder(field1: 'v1', field3: 'x', field2: new EmbeddedDetail(sub: 'a')).save(failOnError: true)
        new EmbeddedHolder(field1: 'v1', field3: 'y', field2: new EmbeddedDetail(sub: 'b')).save(failOnError: true)
        new EmbeddedHolder(field1: 'v1', field3: 'z', field2: new EmbeddedDetail(sub: 'c')).save(failOnError: true)
        new EmbeddedHolder(field1: 'other', field3: 'x', field2: new EmbeddedDetail(sub: 'a')).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    @Unroll
    void "#shape"() {
        given:
        seed()

        expect:
        EmbeddedHolder.createCriteria().list(closure)*.field3.sort() == expected

        where:
        shape                                                              | closure                                                                                                    | expected
        'a block beside a property'                                        | { -> eq('field1', 'v1'); field2 { eq('sub', 'a') } }                                                       | ['x']
        'a block and a property in a disjunction'                          | { -> or { field2 { eq('sub', 'b') }; eq('field3', 'z') } }                                                 | ['y', 'z']
        'a disjunction beside a property and a block'                      | { -> eq('field1', 'v1'); field2 { isNotNull('sub') }; or { field2 { eq('sub', 'a') }; eq('field3', 'z') } } | ['x', 'z']
        'a block with an in list in a disjunction beside a property'       | { -> eq('field1', 'v1'); or { field2 { inList('sub', ['b', 'c']) }; eq('field3', 'x') } }                   | ['x', 'y', 'z']
    }
}

@Entity
class EmbeddedHolder {
    String field1
    String field3
    EmbeddedDetail field2

    static embedded = ['field2']
}

@Entity
class EmbeddedDetail {
    String sub
}
