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
package org.grails.scaffolding.model.property

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.scaffolding.model.MocksDomain
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by jameskleeh on 5/25/17.
 */
class EntityValidatorDomainPropertySpec extends Specification implements MocksDomain {

    @Shared
    MappingContext mappingContext

    @Shared
    PersistentEntity domainClass

    @Shared
    PersistentProperty address

    @Shared
    PersistentProperty name

    @Shared
    PersistentProperty foos

    @Shared
    Embedded props

    void setup() {
        mappingContext = new KeyValueMappingContext("test")
        domainClass = mockDomainClassEntityValidator(mappingContext, ScaffoldedDomain)
        address = domainClass.getPropertyByName("address")
        props = (Embedded)domainClass.getPropertyByName("props")
        name = props.associatedEntity.getPropertyByName("name")
        foos = domainClass.getPropertyByName("foos")
    }

    @Unroll
    void "test isRequired #propertyName is required: #expected"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(domainClass.getPropertyByName(propertyName), mappingContext)
        property.convertEmptyStringsToNull = convertEmpty
        property.trimStrings = trimStrings

        then:
        property.isRequired() == expected

        where:
        propertyName    | convertEmpty | trimStrings | expected
        "testRequired1" | true         | true        | true
        "testRequired1" | false        | true        | true
        "testRequired1" | true         | false       | true
        "testRequired2" | true         | true        | false
        "testRequired2" | false        | true        | false
        "testRequired2" | true         | false       | false
        "testRequired3" | true         | true        | false
        "testRequired3" | false        | true        | false
        "testRequired3" | true         | false       | false
        "testRequired4" | true         | true        | true
        "testRequired4" | false        | true        | false
        "testRequired4" | true         | false       | false
    }

    class ScaffoldedDomain {
        Long id
        Long version
        String address
        EmbeddedClass props

        String testRequired1
        String testRequired2
        String testRequired3
        String testRequired4

        Set<String> foos
        static hasMany = [foos: String]

        static embedded = ['props']

        static constraints = {
            testRequired1(nullable: false, blank: false)
            testRequired2(nullable: false, blank: true)
            testRequired3(nullable: true, blank: false)
        }
    }


    class EmbeddedClass {
        String name
    }


}
