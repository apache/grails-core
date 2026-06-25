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

package example

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Integration tests for the criteria Groovy extension modules.
 *
 * <ul>
 *   <li>{@link example.extensions.CriteriaBuilderExtensions}: adds {@code eqIf} to
 *       {@link grails.gorm.CriteriaBuilder}. Exercised via
 *       {@code new DetachedCriteria(Product).build { eqIf ... }}.</li>
 *   <li>{@link org.grails.orm.hibernate.query.HibernateCriteriaBuilderExtensions}: adds {@code numberLike}
 *       to {@code AbstractHibernateCriteriaBuilder}. Exercised via
 *       {@code new DetachedCriteria(Product).build { numberLike ... }}.</li>
 * </ul>
 */
@Integration
@Rollback
class CriteriaExtensionSpec extends Specification {

    ProductSearchService productSearchService

    private void createProducts() {
        new Product(name: 'Widget', price: 9.99).save(flush: true)
        new Product(name: 'Gadget', price: 49.99).save(flush: true)
        new Product(name: 'Doohickey', price: 4.99).save(flush: true)
    }

    void "eqIf adds equality restriction and returns the matching product"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', 'Widget'
        }.list()

        then:
        results.size() == 1
        results[0].name == 'Widget'
    }

    void "eqIf skips restriction and returns all products when value is null"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', null
        }.list()

        then:
        results.size() == 3
    }

    void "eqIf skips restriction and returns all products when value is empty string"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', ''
        }.list()

        then:
        results.size() == 3
    }

    void "eqIf skips restriction and returns all products when value is blank"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', '   '
        }.list()

        then:
        results.size() == 3
    }

    void "eqIf trims padded string value and still returns the matching product"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', '  Widget  '
        }.list()

        then:
        results.size() == 1
        results[0].name == 'Widget'
    }

    void "eqIf with trim=false treats padded value as a literal and returns no match"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'name', '  Widget  ', false
        }.list()

        then:
        results.empty
    }

    void "eqIf passes non-String values through and returns the matching product by price"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            eqIf 'price', 49.99G
        }.list()

        then:
        results.size() == 1
        results[0].name == 'Gadget'
    }

    void "ProductSearchService.findByName returns the matching product via eqIf"() {
        given:
        createProducts()

        when:
        List<Product> results = productSearchService.findByName('Gadget')

        then:
        results.size() == 1
        results[0].name == 'Gadget'
    }

    void "ProductSearchService.findByName returns all products when name is null"() {
        given:
        createProducts()

        when:
        List<Product> results = productSearchService.findByName(null)

        then:
        results.size() == 3
    }

    void "ProductSearchService.findByName returns all products when name is blank"() {
        given:
        createProducts()

        when:
        List<Product> results = productSearchService.findByName('  ')

        then:
        results.size() == 3
    }

    void "numberLike matches products whose price starts with a given prefix"() {
        given:
        createProducts()

        when: 'prices: 9.99, 49.99, 4.99 — 4.99 and 49.99 start with 4 when cast to varchar'
        List<Product> results = new DetachedCriteria(Product).build {
            numberLike 'price', '4%'
        }.list()

        then:
        results.size() == 2
        results*.name.sort() == ['Doohickey', 'Gadget']
    }

    void "numberLike with exact value returns the single matching product"() {
        given:
        createProducts()

        when:
        List<Product> results = new DetachedCriteria(Product).build {
            numberLike 'price', '9.99'
        }.list()

        then:
        results.size() == 1
        results[0].name == 'Widget'
    }

    void "numberLike with wildcard matches all products containing the digit"() {
        given:
        createProducts()

        when: 'all three prices (9.99, 49.99, 4.99) contain the digit 9'
        List<Product> results = new DetachedCriteria(Product).build {
            numberLike 'price', '%9%'
        }.list()

        then:
        results.size() == 3
    }

    void "numberLike combined with eqIf narrows results"() {
        given:
        createProducts()
        new Product(name: 'Sprocket', price: 9.50).save(flush: true)

        when: 'price starts with 9 and name is Widget'
        List<Product> results = new DetachedCriteria(Product).build {
            numberLike 'price', '9%'
            eqIf 'name', 'Widget'
        }.list()

        then:
        results.size() == 1
        results[0].name == 'Widget'
    }

    void "ProductSearchService.findByPriceLike returns matching products via numberLike"() {
        given:
        createProducts()

        when:
        List<Product> results = productSearchService.findByPriceLike('4%')

        then:
        results.size() == 2
        results*.name.sort() == ['Doohickey', 'Gadget']
    }
}
