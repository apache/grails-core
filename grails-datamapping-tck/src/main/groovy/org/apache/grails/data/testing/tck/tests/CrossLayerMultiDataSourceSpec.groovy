/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import spock.lang.Requires

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProduct
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProductDataService
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProductService

@Requires({ instance.manager?.supportsMultipleDataSources() })
class CrossLayerMultiDataSourceSpec extends GrailsDataTckSpec {

    DataServiceRoutingProductService productService
    DataServiceRoutingProductDataService productDataService

    void setup() {
        manager.setupMultiDataSource(DataServiceRoutingProduct)
        productService = manager.getServiceForConnection(DataServiceRoutingProductService, 'secondary')
        productDataService = manager.getServiceForConnection(DataServiceRoutingProductDataService, 'secondary')
    }

    void cleanup() {
        deleteAllFromConnection('secondary')
        deleteAllFromConnection(null)
        manager.cleanupMultiDataSource()
    }

    void "domain save visible through data service"() {
        given: 'a product saved via domain API'
        def saved = saveDomainProduct('DomainVisible', 10)

        when: 'it is retrieved through the data service'
        def found = productDataService.findByName('DomainVisible')

        then: 'the service sees the domain data'
        found != null
        found.id == saved.id
    }

    void "data service save visible through domain API"() {
        given: 'a product saved via data service'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'ServiceVisible', amount: 20))

        when: 'it is retrieved through the domain API'
        def found = DataServiceRoutingProduct.secondary.withNewTransaction {
            DataServiceRoutingProduct.secondary.get(saved.id)
        }

        then: 'the domain API sees the service data'
        found != null
        found.id == saved.id
        found.name == 'ServiceVisible'
    }

    void "domain delete reflected in data service count"() {
        given: 'two products saved via data service'
        def first = productService.save(new DataServiceRoutingProduct(name: 'First', amount: 1))
        productService.save(new DataServiceRoutingProduct(name: 'Second', amount: 2))

        when: 'one product is deleted via domain API'
        deleteDomainProduct(first)

        then: 'service count reflects deletion'
        productDataService.count() == 1
    }

    void "data service delete reflected in domain API count"() {
        given: 'two products saved via domain API'
        def first = saveDomainProduct('Primary', 1)
        saveDomainProduct('Secondary', 2)

        when: 'one product is deleted via data service'
        productService.delete(first.id)

        then: 'domain count reflects deletion'
        countOnConnection('secondary') == 1
    }

    void "domain and service counts match on secondary"() {
        given: 'products saved across domain and service layers'
        saveDomainProduct('Mixed1', 5)
        productService.save(new DataServiceRoutingProduct(name: 'Mixed2', amount: 6))
        productDataService.save(new DataServiceRoutingProduct(name: 'Mixed3', amount: 7))

        when: 'counting via both layers'
        def domainCount = countOnConnection('secondary')
        def serviceCount = productService.count()

        then: 'counts match on secondary'
        domainCount == serviceCount
    }

    private DataServiceRoutingProduct saveDomainProduct(String name, Integer amount) {
        DataServiceRoutingProduct.secondary.withNewTransaction {
            def item = new DataServiceRoutingProduct(name: name, amount: amount)
            item.secondary.save(flush: true)
            item
        }
    }

    private void deleteDomainProduct(DataServiceRoutingProduct product) {
        DataServiceRoutingProduct.secondary.withNewTransaction {
            product.secondary.delete(flush: true)
        }
    }

    private long countOnConnection(String connectionName) {
        if (connectionName) {
            DataServiceRoutingProduct."${connectionName}".withNewTransaction {
                DataServiceRoutingProduct."${connectionName}".count()
            }
        } else {
            DataServiceRoutingProduct.withNewTransaction {
                DataServiceRoutingProduct.count()
            }
        }
    }

    private void deleteAllFromConnection(String connectionName) {
        if (connectionName) {
            DataServiceRoutingProduct."${connectionName}".withNewTransaction {
                DataServiceRoutingProduct."${connectionName}".list().each { it."${connectionName}".delete(flush: true) }
            }
        } else {
            DataServiceRoutingProduct.withNewTransaction {
                DataServiceRoutingProduct.list().each { it.delete(flush: true) }
            }
        }
    }
}
