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
package org.grails.orm.hibernate.services

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.services.Where

import org.grails.orm.hibernate.HibernateDatastore

/**
 * An aggregate on the right of a comparison in the {@code @Where} of a data service is run as a subquery over the
 * entity of the service, as it is in a {@code where} query: the comparisons with the average, the maximum and the
 * minimum give the same rows as the query written with {@code where}.
 */
class WhereServiceAggregateSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(WsaReading, WsaBase, WsaSub)

    @Shared WsaReadingService service

    void setupSpec() {
        service = datastore.getService(WsaReadingService)
    }

    void setup() {
        WsaReading.withNewTransaction {
            WsaReading.executeUpdate('delete from WsaReading')
            [1, 2, 3, 4, 10].each { new WsaReading(qty: it).save(failOnError: true, flush: true) }
        }
    }

    void "a data service @Where compares with the average of the entity like the same where query"() {
        expect: 'the quantities are 1, 2, 3, 4 and 10, so the average is 4'
        found(service.aboveAverage()) == [10]
        found(listOf(aboveAverageQuery())) == [10]
    }

    void "a data service @Where compares with the average of the entity or equal to it like the same where query"() {
        expect:
        found(service.atLeastAverage()) == [4, 10]
        found(listOf(atLeastAverageQuery())) == [4, 10]
    }

    void "a data service @Where compares with the maximum of the entity like the same where query"() {
        expect:
        found(service.theMaximum()) == [10]
        found(listOf(maximumQuery())) == [10]
    }

    void "a data service @Where compares with the sum of the entity like the same where query"() {
        expect: 'the sum is 20, above every quantity'
        found(service.belowTheSum()) == [1, 2, 3, 4, 10]
        found(listOf(belowTheSumQuery())) == [1, 2, 3, 4, 10]
    }

    void "a data service @Where compares with the count of the entity like the same where query"() {
        expect: 'five distinct quantities'
        found(service.atMostTheCount()) == [1, 2, 3, 4]
        found(listOf(countQuery())) == [1, 2, 3, 4]
    }

    void "a data service @Where compares with the minimum of the entity like the same where query"() {
        expect:
        found(service.theMinimum()) == [1]
        found(listOf(minimumQuery())) == [1]
    }

    void "an aggregate in a where query of a method that names the base class is of the criteria passed to it: #function"() {
        given: 'subclass quantities 1, 2 and 4, and base class quantities -50, 0 and 10'
        WsaBase.withNewTransaction {
            WsaBase.executeUpdate('delete from WsaBase')
            [-50, 0, 10].each { new WsaBase(qty: it).save(failOnError: true, flush: true) }
            [1, 2, 4].each { new WsaSub(qty: it).save(failOnError: true, flush: true) }
        }

        expect: 'the aggregate is of the three subclass rows, not of the six rows of the base class'
        WsaBase.withNewTransaction { query(new DetachedCriteria<WsaBase>(WsaSub)).list() }*.qty.sort() == expected

        where:
        function | query                                    || expected
        'avg'    | WhereServiceAggregateSpec.&aboveAverageOf || [4]
        'max'    | WhereServiceAggregateSpec.&maximumOf      || [4]
        'min'    | WhereServiceAggregateSpec.&minimumOf      || [1]
        'sum'    | WhereServiceAggregateSpec.&belowTheSumOf  || [1, 2, 4]
        'count'  | WhereServiceAggregateSpec.&atMostCountOf  || [1, 2]
    }

    static DetachedCriteria<WsaBase> aboveAverageOf(DetachedCriteria<WsaBase> criteria) {
        criteria.where { qty > avg(qty) }
    }

    static DetachedCriteria<WsaBase> maximumOf(DetachedCriteria<WsaBase> criteria) {
        criteria.where { qty == max(qty) }
    }

    static DetachedCriteria<WsaBase> minimumOf(DetachedCriteria<WsaBase> criteria) {
        criteria.where { qty == min(qty) }
    }

    static DetachedCriteria<WsaBase> belowTheSumOf(DetachedCriteria<WsaBase> criteria) {
        criteria.where { qty < sum(qty) }
    }

    static DetachedCriteria<WsaBase> atMostCountOf(DetachedCriteria<WsaBase> criteria) {
        criteria.where { qty <= count(qty) }
    }

    static DetachedCriteria<WsaReading> aboveAverageQuery() {
        WsaReading.where { qty > avg(qty) }
    }

    static DetachedCriteria<WsaReading> atLeastAverageQuery() {
        WsaReading.where { qty >= avg(qty) }
    }

    static DetachedCriteria<WsaReading> maximumQuery() {
        WsaReading.where { qty == max(qty) }
    }

    static DetachedCriteria<WsaReading> minimumQuery() {
        WsaReading.where { qty == min(qty) }
    }

    static DetachedCriteria<WsaReading> belowTheSumQuery() {
        WsaReading.where { qty < sum(qty) }
    }

    static DetachedCriteria<WsaReading> countQuery() {
        WsaReading.where { qty <= count(qty) }
    }

    private static List<WsaReading> listOf(DetachedCriteria<WsaReading> query) {
        WsaReading.withNewTransaction { query.list() } as List<WsaReading>
    }

    private static List<Integer> found(List<WsaReading> readings) {
        readings*.qty.sort()
    }
}

@Entity
class WsaReading {

    Integer qty
}

@Entity
class WsaBase {

    Integer qty
}

@Entity
class WsaSub extends WsaBase {
}

@Service(WsaReading)
interface WsaReadingService {

    @Where({ qty > avg(qty) })
    List<WsaReading> aboveAverage()

    @Where({ qty >= avg(qty) })
    List<WsaReading> atLeastAverage()

    @Where({ qty == max(qty) })
    List<WsaReading> theMaximum()

    @Where({ qty == min(qty) })
    List<WsaReading> theMinimum()

    @Where({ qty < sum(qty) })
    List<WsaReading> belowTheSum()

    @Where({ qty <= count(qty) })
    List<WsaReading> atMostTheCount()
}
