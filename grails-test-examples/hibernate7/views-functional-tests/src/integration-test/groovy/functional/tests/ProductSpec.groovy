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

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class ProductSpec extends Specification implements HttpClientSupport {

    void testEmptyProducts() {
        when:
        def response = http('/products')

        then: 'The values returned are there'
        response.assertJsonContains(200, 'Content-Type': 'application/hal+json;charset=UTF-8', [
                count: 0,
                max: 10,
                offset: 0,
                order: null,
                sort: null
        ])

        and: 'the hal _links attribute is present'
        def json = response.json()
        json._links.size() == 1
        json._links.self.href.startsWith("$httpBaseUrl/product")

        and: 'there are no products yet'
        json._embedded.products.size() == 0
    }

    void testSingleProduct() {
        when:
        def createResponse = httpPostJson('/products', [
                name: 'Product 1',
                description: 'product 1 description',
                price: 123.45
        ])

        then:
        createResponse.assertStatus(201)
        def createBody = createResponse.json()

        when: 'We get the products'
        def response = http('/products')

        then: 'The values returned are there'
        response.assertJsonContains(200, 'Content-Type': 'application/hal+json;charset=UTF-8', [
                count: 1,
                max: 10,
                offset: 0,
                sort: null,
                order: null
        ])

        and: 'the hal _links attribute is present'
        def json = response.json()
        json._links.size() == 1
        json._links.self.href.startsWith("$httpBaseUrl/product")

        and: 'the product is present'
        json._embedded.products.size() == 1
        json._embedded.products.first().name == 'Product 1'

        cleanup:
        httpDelete("/products/${createBody.id}")
    }

    void 'test a page worth of products'() {
        given:
        def productsIds = []
        15.times { productNumber ->
            def product = [
                name: "Product $productNumber",
                description: "product ${productNumber} description",
                price: productNumber + (productNumber / 100)
            ]
            def createResponse = httpPostJson('/products', product)
            assert createResponse.statusCode() == 201
            productsIds << createResponse.json().id
        }

        when: 'We get the products'
        def response = http('/products')

        then:
        response.assertHeaders(200, 'Content-Type': 'application/hal+json;charset=UTF-8')

        and: 'The values returned are there'
        def body = response.json()
        with(body) {
            count == 15
            max == 10
            offset == 0
            sort == null
            order == null
        }

        and: 'the hal _links attribute is present'
        body._links.size() == 4
        body._links.self.href.startsWith("$httpBaseUrl/product")
        body._links.first.href.startsWith("$httpBaseUrl/product")
        body._links.next.href.startsWith("$httpBaseUrl/product")
        body._links.last.href.startsWith("$httpBaseUrl/product")

        and: 'the product is present'
        body._embedded.products.size() == 10

        cleanup:
        productsIds.each { id ->
            httpDelete("/products/$id")
        }
    }

    void 'test a middle page worth of products'() {
        given:
        def productsIds = []
        30.times { productNumber ->
            def createResponse = httpPostJson('/products', [
                    name: "Product $productNumber",
                    description: "product ${productNumber} description",
                    price: productNumber + (productNumber / 100)
            ])
            assert createResponse.statusCode() == 201
            productsIds << createResponse.json().id
        }

        when: 'We get the products'
        def response = http('/products?offset=10')

        then: 'The values returned are there'
        response.assertJsonContains(200, 'Content-Type': 'application/hal+json;charset=UTF-8', [
                count: 30,
                max: 10,
                offset: 10,
                sort: null,
                order: null
        ])

        and: 'the hal _links attribute is present'
        def json = response.json()
        json._links.size() == 5
        json._links.self.href.startsWith("$httpBaseUrl/product")
        json._links.first.href.startsWith("$httpBaseUrl/product")
        json._links.prev.href.startsWith("$httpBaseUrl/product")
        json._links.next.href.startsWith("$httpBaseUrl/product")
        json._links.last.href.startsWith("$httpBaseUrl/product")

        and: 'the product is present'
        json._embedded.products.size() == 10

        cleanup:
        productsIds.each { id ->
            httpDelete("/products/$id")
        }
    }
}
