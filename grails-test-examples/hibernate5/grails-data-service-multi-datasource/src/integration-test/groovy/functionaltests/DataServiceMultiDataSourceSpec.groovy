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
import example.ProductService

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.Specification

/**
 * Integration test verifying that GORM Data Service auto-implemented
 * CRUD methods (save, get, delete, findByName, count) route correctly
 * to a non-default datasource when @Transactional(connection) is
 * specified on the service.
 *
 * Product is mapped exclusively to the 'secondary' datasource.
 * Without the connection-routing fix, auto-implemented save/get/delete
 * would use the default datasource where no Product table exists.
 *
 * The service is obtained from the secondary child datastore
 * (not auto-wired by Spring) to ensure proper session binding.
 */
@Integration
class DataServiceMultiDataSourceSpec extends Specification {

    @Autowired
    HibernateDatastore hibernateDatastore

    ProductService productService

    void setup() {
        productService = hibernateDatastore
                .getDatastoreForConnection('secondary')
                .getService(ProductService)
    }

    void cleanup() {
        Product.secondary.withTransaction {
            Product.secondary.executeUpdate('delete from Product')
        }
    }

    void "save routes to secondary datasource"() {
        when:
        def saved = productService.save(new Product(name: 'Widget', amount: 42))

        then:
        saved != null
        saved.id != null
        saved.name == 'Widget'
        saved.amount == 42
    }

    void "get by ID routes to secondary datasource"() {
        given:
        def saved = productService.save(new Product(name: 'Gadget', amount: 99))

        when:
        def found = productService.get(saved.id)

        then:
        found != null
        found.id == saved.id
        found.name == 'Gadget'
        found.amount == 99
    }

    void "count routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'Alpha', amount: 10))
        productService.save(new Product(name: 'Beta', amount: 20))

        expect:
        productService.count() == 2
    }

    void "delete routes to secondary datasource"() {
        given:
        def saved = productService.save(new Product(name: 'Ephemeral', amount: 1))

        when:
        productService.delete(saved.id)

        then:
        productService.get(saved.id) == null
    }

    void "findByName routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'Unique', amount: 77))

        when:
        def found = productService.findByName('Unique')

        then:
        found != null
        found.name == 'Unique'
        found.amount == 77
    }

    void "findAllByName routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'Duplicate', amount: 10))
        productService.save(new Product(name: 'Duplicate', amount: 20))
        productService.save(new Product(name: 'Other', amount: 30))

        when:
        def found = productService.findAllByName('Duplicate')

        then:
        found.size() == 2
        found.every { it.name == 'Duplicate' }
    }

    void "@Query find-one routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'QueryOne', amount: 50))

        when:
        def found = productService.findOneByQuery('QueryOne')

        then:
        found != null
        found.name == 'QueryOne'
        found.amount == 50
    }

    void "@Query find-one returns null for non-existent"() {
        expect:
        productService.findOneByQuery('NonExistent') == null
    }

    void "@Query find-all routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'Expensive1', amount: 500))
        productService.save(new Product(name: 'Expensive2', amount: 600))
        productService.save(new Product(name: 'Cheap1', amount: 10))

        when:
        def found = productService.findAllByQuery(400)

        then:
        found.size() == 2
        found*.name.containsAll(['Expensive1', 'Expensive2'])
    }

    void "@Query update routes to secondary datasource"() {
        given:
        productService.save(new Product(name: 'UpdateTarget', amount: 100))

        when:
        def updated = productService.updateAmountByName('UpdateTarget', 999)

        then:
        updated == 1

        and:
        productService.findByName('UpdateTarget').amount == 999
    }
}
