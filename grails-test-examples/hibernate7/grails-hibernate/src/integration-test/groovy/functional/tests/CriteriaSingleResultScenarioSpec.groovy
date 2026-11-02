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

package functional.tests

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.hibernate.NonUniqueResultException
import spock.lang.Specification

/**
 * What a query that expects one row does when several, one or none match. Criteria get is
 * documented as returning "a unique result" and "not to be confused with a limit to just the
 * first row", so it fails when several rows match; find, findBy, findWhere and the get and
 * find of a where query return the first row.
 */
@Integration(applicationClass = Application)
@Rollback
class CriteriaSingleResultScenarioSpec extends Specification {

    private void seed() {
        (1..3).each {
            new ScenarioItem(name: "n${it}", qty: it, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
        }
        ScenarioItem.withSession { it.clear() }
    }

    void "criteria get fails when several rows match"() {
        given:
        seed()

        when:
        ScenarioItem.createCriteria().get {
            gt 'qty', 0
            order 'qty', 'asc'
        }

        then:
        thrown(NonUniqueResultException)
    }

    void "criteria get returns the row when exactly one matches"() {
        given:
        seed()

        when:
        ScenarioItem item = ScenarioItem.createCriteria().get { eq 'qty', 2 }

        then:
        item.name == 'n2'
    }

    void "criteria get returns null when nothing matches"() {
        given:
        seed()

        when:
        ScenarioItem item = ScenarioItem.createCriteria().get { gt 'qty', 99 }

        then:
        item == null
    }

    void "where get returns the first row in the requested order when several match"() {
        given:
        seed()

        when:
        ScenarioItem item = ScenarioItem.where { qty > 0 }.order('qty', 'desc').get()

        then:
        item.name == 'n3'
    }

    void "where find returns the first row in the requested order when several match"() {
        given:
        seed()

        when:
        ScenarioItem item = ScenarioItem.where { qty > 0 }.order('qty', 'asc').find()

        then:
        item.name == 'n1'
    }

    void "find with a closure returns the first row when several match"() {
        given:
        seed()

        when:
        ScenarioItem item = ScenarioItem.find { qty > 0 }

        then:
        item.name == 'n1'
    }

    void "dynamic finder and findWhere return the first row when several match"() {
        given:
        seed()

        when:
        ScenarioItem byFinder = ScenarioItem.findByStatus(ScenarioStatus.NEW)
        ScenarioItem byWhere = ScenarioItem.findWhere(status: ScenarioStatus.NEW)

        then:
        byFinder.name == 'n1'
        byWhere.name == 'n1'
    }
}
