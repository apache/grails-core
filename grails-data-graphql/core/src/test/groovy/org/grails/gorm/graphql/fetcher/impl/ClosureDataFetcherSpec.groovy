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

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.entity.EntityFetchOptions
import spock.lang.Subject

@Subject(ClosureDataFetcher)
class ClosureDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    void "test closure is called with the source argument"() {
        given:
        String result
        Closure closure = { String s ->
            result = s
        }

        when:
        new ClosureDataFetcher(closure).get(Mock(DataFetchingEnvironment) {
            1 * getSource() >> 'foo'
        })

        then:
        result == 'foo'
    }

    void "test a no arg closure works"() {
        given:
        String result
        Closure closure = {
            result = 'hello'
        }

        when:
        new ClosureDataFetcher(closure).get(Mock(DataFetchingEnvironment) {
            1 * getSource() >> 'foo'
        })

        then:
        result == 'hello'
    }

    void "test buildFetchOptions returns null when no domain type is supplied"() {
        given:
        ClosureDataFetcher fetcher = new ClosureDataFetcher({})

        expect:
        fetcher.buildFetchOptions() == null
    }

    void "test buildFetchOptions returns null when the domain type is not a GORM entity"() {
        given:
        ClosureDataFetcher fetcher = new ClosureDataFetcher({}, String)

        expect:
        fetcher.buildFetchOptions() == null
    }

    void "test buildFetchOptions returns fetch options for a GORM entity domain type"() {
        given:
        ClosureDataFetcher fetcher = new ClosureDataFetcher({}, One)

        expect:
        fetcher.buildFetchOptions() instanceof EntityFetchOptions
    }

    void "test buildFetchOptions caches the result"() {
        given:
        ClosureDataFetcher fetcher = new ClosureDataFetcher({}, One)

        expect:
        fetcher.buildFetchOptions().is(fetcher.buildFetchOptions())
    }
}
