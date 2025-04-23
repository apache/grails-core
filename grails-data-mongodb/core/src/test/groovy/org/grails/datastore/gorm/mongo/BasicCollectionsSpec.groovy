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

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Ignore
import spock.lang.IgnoreIf

class BasicCollectionsSpec extends GormDatastoreSpec{
    @Override
    List getDomainClasses() {
        [Linguist, Increment]
    }

    void "Test that a Locale can be used inside a collection"() {
        when:"A locale collection is persisted"
        def p = new Linguist(name:"Bob", attributes: [[hairColor:'black']])
        p.spokenLanguages << Locale.UK << Locale.CANADA_FRENCH << Locale.US
        p.save(flush:true)

        then:"The collection is still ok"
        p.spokenLanguages == [Locale.UK, Locale.CANADA_FRENCH, Locale.US]

        when:"The entity is refetched"
        session.clear()
        p = Linguist.get(p.id)

        then:"The embedded collection and locales can be read back correctly"
        p.name == "Bob"
        p.attributes == [[hairColor:'black']]
        p.spokenLanguages == [Locale.UK, Locale.CANADA_FRENCH, Locale.US]
    }

    void "Test that a map of Currency works."() {
        when:"A currency map is persisted"
        def p = new Linguist(name:"Bob")
        p.currencies.put(Locale.UK.toString(), Currency.getInstance("GBP"))
        p.currencies.put(Locale.US.toString(), Currency.getInstance("USD"))
        p.save(flush:true)

        then:"The collection is still ok"
        p.currencies == [
                (Locale.UK.toString()):Currency.getInstance("GBP"),
                (Locale.US.toString()):Currency.getInstance("USD")
        ]

        when:
        session.clear()
        p = Linguist.get(p.id)

        then:"The embedded collection and locales can be read back correctly"
        p.name == "Bob"
        p.currencies == [
                (Locale.UK.toString()):Currency.getInstance("GBP"),
                (Locale.US.toString()):Currency.getInstance("USD")
        ]
    }

    void "Test that a map of BigDecimal works."() {
        when:"A payRate map is persisted"
        def p = new Linguist(name:"Bob")
        def val = new BigDecimal("1.0")
        p.payRates.put(Locale.UK.toString(), val)
        p.save(flush:true)

        then:"The collection is still ok"
        p.payRates == [
                (Locale.UK.toString()):val
        ]

        when:
        session.clear()
        p = Linguist.get(p.id)

        then:"The embedded collection and payRates can be read back correctly"
        p.name == "Bob"
        p.payRates == [
                (Locale.UK.toString()):val
        ]
    }

    void "Test beforeInsert() and beforeUpdate() methods for collections"() {
        when:"An entity is persisted"
        def p = new Increment()
        p.save(flush:true)
        session.clear()
        p = Increment.get(p.id)

        then:"The collection is updated"
        p.counter == 1
        p.history == [0]

        when:"The entity is updated"
        p.counter = 10
        p.save(flush:true)
        session.clear()
        p = Increment.get(p.id)

        then:"The collection is updated too"
        p.counter == 11
        p.history == [0, 10]
    }
}

@Entity
class Linguist {
    String id
    String name
    List<Locale> spokenLanguages = []
    List<Map> attributes = []
    Map<String, Currency> currencies = [:]
    Map<String, BigDecimal> payRates = [:]

    static constraints = {
    }
}

@Entity
class Increment {
    String id
    Integer counter = 0
    List<Integer> history = []

    def beforeInsert() {
        inc()
    }

    def beforeUpdate() {
        inc()
    }

    def inc() {
        history << counter++
    }
}