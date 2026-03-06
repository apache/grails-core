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

import org.grails.datastore.mapping.model.PersistentEntity
import org.hibernate.FetchMode
import org.hibernate.query.Query
import org.hibernate.query.QueryFlushMode
import org.springframework.core.convert.ConversionService
import spock.lang.Specification
import spock.lang.Unroll

class GrailsHibernateQueryUtilsSpec extends Specification {

    GrailsHibernateQueryUtils queryUtils = new GrailsHibernateQueryUtils()

    @Unroll
    def "test convertQueryFlushMode for #input"() {
        expect:
        queryUtils.convertQueryFlushMode(input) == expected

        where:
        input               | expected
        "ALWAYS"            | QueryFlushMode.FLUSH
        "MANUAL"            | QueryFlushMode.NO_FLUSH
        "COMMIT"            | QueryFlushMode.NO_FLUSH
        "AUTO"              | QueryFlushMode.DEFAULT
        null                | QueryFlushMode.DEFAULT
        "INVALID"           | QueryFlushMode.NO_FLUSH // defaults to COMMIT which is NO_FLUSH
    }

    @Unroll
    def "test getFetchMode for #input"() {
        expect:
        queryUtils.getFetchMode(input) == expected

        where:
        input               | expected
        "JOIN"              | FetchMode.JOIN
        "eager"             | FetchMode.JOIN
        "SELECT"            | FetchMode.SELECT
        "lazy"              | FetchMode.SELECT
        "default"           | FetchMode.DEFAULT
        null                | FetchMode.DEFAULT
    }

    def "test populateArgumentsForCriteria for Query"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity)
        Query query = Mock(Query)
        ConversionService conversionService = Mock(ConversionService)
        
        Map argMap = [
            max: 10,
            offset: 20,
            fetchSize: 50,
            timeout: 30,
            readOnly: true,
            cache: true
        ]

        entity.getJavaClass() >> Object.class
        conversionService.convert(10, Integer.class) >> 10
        conversionService.convert(20, Integer.class) >> 20
        conversionService.convert(50, Integer.class) >> 50
        conversionService.convert(30, Integer.class) >> 30

        when:
        queryUtils.populateArgumentsForCriteria(entity, query, argMap, conversionService)

        then:
        1 * query.setMaxResults(10)
        1 * query.setFirstResult(20)
        1 * query.setFetchSize(50)
        1 * query.setTimeout(30)
        1 * query.setReadOnly(true)
        1 * query.setCacheable(true)
    }

    def "test populateArgumentsForCriteria for Query with null conversion results"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity)
        Query query = Mock(Query)
        ConversionService conversionService = Mock(ConversionService)
        
        Map argMap = [
            fetchSize: 50,
            timeout: 30
        ]

        entity.getJavaClass() >> Object.class
        // Simulate conversion returning null
        conversionService.convert(50, Integer.class) >> null
        conversionService.convert(30, Integer.class) >> null

        when:
        queryUtils.populateArgumentsForCriteria(entity, query, argMap, conversionService)

        then:
        0 * query.setFetchSize(_)
        0 * query.setTimeout(_)
    }
}
