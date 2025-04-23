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

package grails.gorm.tck


import grails.gorm.tests.GormDatastoreSpec

class WhereLazySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Product]
    }

    void createProducts() {
        new Product(name: 'tshirt', color: 'red').save(flush: true)
        new Product(name: 'tshirt', color: 'orange').save(flush: true)
        new Product(name: 'tshirt', color: 'yellow').save(flush: true)
        new Product(name: 'tshirt', color: 'orange').save(flush: true)
        new Product(name: 'tshirt', color: 'blue').save(flush: true)
    }

    void "test deleteAll with whereLazy"() {
        setup:
        createProducts()

        when:
        Product.removeAllByColor("orange")

        then:
        Product.count() == 3

        cleanup:
        Product.deleteAll()

    }

    void "test updateAll with whereLazy"() {
        setup:
        createProducts()

        when:
        Product.updateAll("orange")

        then:
        Product.countByName('tshirt') == 3
        Product.countByName('t-shirt orange') == 2

        cleanup:
        Product.deleteAll()
    }

}
