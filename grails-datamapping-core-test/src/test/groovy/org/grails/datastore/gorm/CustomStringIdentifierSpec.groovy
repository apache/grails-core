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

class CustomStringIdentifierSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Product, Description])
    }

    void "test basic crud operations with string id"() {
        when: "A product is saved with an assigned id"
        createProducts()
        def p = Product.get("MacBook")

        then: "The product is not null"
        p != null

        when: "A product is retrieved by id"
        manager.session.clear()
        p = Product.get("MacBook")

        then: "The product is not null"
        p != null
    }

    void "Test dynamic finders with string id"() {
        when: "A product with a string id is query via a dynamic finder"
        createProducts()
        def p = Product.findByName("MacBook")

        then: "The product is not null"
        p != null

    }

    void "Test integer based id"() {
        when: "An object has an id that is an integer"
        def d = new Description(name: "Blah").save(flush: true)

        then: "The object is successfully saved"
        d != null

        when: "The object is queried"
        manager.session.clear()
        d = Description.get(1)

        then: "The object is returned"
        d != null

    }

    protected def createProducts() {
        new Product(name: "MacBook").save()
        new Product(name: "iPhone").save()
        new Product(name: "iMac").save(flush: true)

    }
}

@Entity
class Description {
    Integer id
    String name
}

@Entity
class Product {
    String name
    Date dateCreated

    static mapping = {
        id generator: 'assigned', name: "name"
    }
}
