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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class BasicTypeHasManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Workspace])
    }

    @Issue('GRAILS-9876')
    void "Test addTo with locales"() {
        given: "Some locale instances"
        def ws = new Workspace()
        def locales = [Locale.GERMANY, Locale.GERMAN, Locale.ENGLISH, Locale.CHINA] // different language and country base locales

        when: "The locales are added to a domain with the addTo method"
        for (loc in locales) {
            ws.addToLocales(loc)
        }
        ws.save()

        then: "The locales collection contains said locales"
        ws.locales.contains Locale.GERMANY
        ws.locales.contains Locale.GERMAN
        ws.locales.contains Locale.ENGLISH
        ws.locales.contains Locale.CHINA
    }

    @Issue('GRAILS-9876')
    void "Test addTo with dates"() {
        given: "Some date instances"
        def ws = new Workspace()
        def d1 = new Date() + 1
        def d2 = new Date() + 10

        def dates = [d1, d2] // different dates
        when: "The dates are added to a domain with addTo"
        for (date in dates) {
            ws.addToDates(date)
        }
        ws.save()

        then: "The dates are added correctly to the association"
        ws.dates.contains d1
        ws.dates.contains d2
    }
}

@Entity
class Workspace {

    Long id
    Set<Locale> locales
    Set<Date> dates
    static hasMany = [locales: Locale, dates: Date]
}