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
package grails.gorm.specs.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.apache.grails.data.hibernate6.core.GrailsDataHibernate6TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

/**
 * Created by francoiskha on 19/04/18.
 */
class DeepValidationSpec extends GrailsDataTckSpec<GrailsDataHibernate6TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([AnotherCity, Market, Address])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/1033')
    void "performs deep validation correctly"() {

        when: "save market with failing custom validator on child"
        Address address = new Address(streetName: "Main St.", landmark: "The Golder Gate Bridge", postalCode: "11").save(validate: false)
        new Market(name: "Main", address: address).save(deepValidate: false)

        then: "market is saved, no validation error"
        Market.count() == 1

        when: "save market with nullable on child"
        address = new Address(landmark: "1B, Main St.", postalCode: "121001").save(validate: false)
        new Market(name: "NIT", address: address).save(deepValidate: false)

        then:
        thrown(DataIntegrityViolationException)

        when: "nested validation fails"
        address = new Address(streetName: "1B, Main St.", landmark: "V2", postalCode: "11").save(validate: false)
        new AnotherCity(name: "Faridabad").addToMarkets(name: "NIT 1", address: address).save(deepValidate: false)

        then: "market is saved, no validation error"
        AnotherCity.count() == 1
        Market.count() == 2
        Address.count() == 2

        when: "invalid embedded object"
        new AnotherCity(name: "St. Louis", country: new AnotherCountry()).save(deepValidate: false)

        then: "should save the city"
        AnotherCity.count() == 2
        AnotherCountry.count() == 0
    }
}

@Entity
class AnotherCity {

    String name
    AnotherCountry country

    static hasMany = [markets: Market]
    static embedded = ['country']
    static constraints = {
        country nullable: true
    }

}


@Entity
class Market {

    String name
    Address address

}

@Entity
class Address {

    String streetName
    String landmark
    String postalCode

    private static final POSTAL_CODE_PATTERN = /^(\d{5}-\d{4})|(\d{5})|(\d{9})$/

    static constraints = {
        streetName nullable: false
        landmark nullable: true
        postalCode validator: { value -> value ==~ POSTAL_CODE_PATTERN }
    }
}

@Entity
class AnotherCountry {
    String name
}