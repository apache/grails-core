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
package functionaltests

import example.Product
import example.InheritedProductService

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class DataServiceDatasourceInheritanceSpec extends Specification {

    @Autowired
    InheritedProductService inheritedProductService

    void cleanup() {
        Product.secondary.withTransaction {
            Product.secondary.executeUpdate('delete from Product')
        }
    }

    void "save routes to secondary datasource via inherited connection"() {
        when:
        def saved = inheritedProductService.save(new Product(name: 'InheritedWidget', amount: 42))

        then:
        saved != null
        saved.id != null
        saved.name == 'InheritedWidget'
        saved.amount == 42
    }

    void "get by ID routes to secondary datasource via inherited connection"() {
        given:
        def saved = inheritedProductService.save(new Product(name: 'InheritedGadget', amount: 99))

        when:
        def found = inheritedProductService.get(saved.id)

        then:
        found != null
        found.id == saved.id
        found.name == 'InheritedGadget'
        found.amount == 99
    }

    void "count routes to secondary datasource via inherited connection"() {
        given:
        inheritedProductService.save(new Product(name: 'Alpha', amount: 10))
        inheritedProductService.save(new Product(name: 'Beta', amount: 20))

        expect:
        inheritedProductService.count() == 2
    }

    void "delete routes to secondary datasource via inherited connection"() {
        given:
        def saved = inheritedProductService.save(new Product(name: 'Ephemeral', amount: 1))

        when:
        inheritedProductService.delete(saved.id)

        then:
        inheritedProductService.get(saved.id) == null
    }

    void "findByName routes to secondary datasource via inherited connection"() {
        given:
        inheritedProductService.save(new Product(name: 'Unique', amount: 77))

        when:
        def found = inheritedProductService.findByName('Unique')

        then:
        found != null
        found.name == 'Unique'
        found.amount == 77
    }

    void "findAllByName routes to secondary datasource via inherited connection"() {
        given:
        inheritedProductService.save(new Product(name: 'Duplicate', amount: 10))
        inheritedProductService.save(new Product(name: 'Duplicate', amount: 20))
        inheritedProductService.save(new Product(name: 'Other', amount: 30))

        when:
        def found = inheritedProductService.findAllByName('Duplicate')

        then:
        found.size() == 2
        found.every { it.name == 'Duplicate' }
    }
}
