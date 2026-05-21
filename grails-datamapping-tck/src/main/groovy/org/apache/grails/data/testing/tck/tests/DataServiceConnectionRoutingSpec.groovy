/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
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
class DataServiceConnectionRoutingSpec extends GrailsDataTckSpec {

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

    // ---- Abstract class service tests ----

    void 'save routes to secondary datasource'() {

        when: 'a product is saved through the abstract Data Service'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'Widget', amount: 42))

        then: 'it is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'Widget'
        saved.amount == 42

        and: 'it exists on the secondary datasource'
        countOnConnection('secondary') == 1
    }

    void 'get by ID routes to secondary datasource'() {
        given: 'a product saved on secondary'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'Gadget', amount: 99))

        when: 'we retrieve it by ID'
        def found = productService.get(saved.id)

        then: 'the correct entity is returned'
        found != null
        found.id == saved.id
        found.name == 'Gadget'
        found.amount == 99
    }

    void 'count routes to secondary datasource'() {
        given: 'two products saved on secondary'
        productService.save(new DataServiceRoutingProduct(name: 'Alpha', amount: 10))
        productService.save(new DataServiceRoutingProduct(name: 'Beta', amount: 20))

        and: 'a product saved on default that should not be counted'
        saveToConnection(null, 'DefaultOnly', 99)

        expect: 'count returns only secondary items'
        productService.count() == 2
    }

    void 'delete by ID routes to secondary datasource - FindAndDeleteImplementer'() {
        given: 'a product saved on secondary'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'Ephemeral', amount: 1))

        when: 'we delete it using delete(id) which returns the domain object'
        def deleted = productService.delete(saved.id)

        then: 'the deleted entity is returned and no longer exists'
        deleted != null
        deleted.name == 'Ephemeral'
        productService.get(saved.id) == null
        productService.count() == 0
    }

    void 'delete by ID routes to secondary datasource - DeleteImplementer'() {
        given: 'a product saved on secondary'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'AlsoEphemeral', amount: 2))

        when: 'we delete it using void deleteProduct(id)'
        productService.deleteProduct(saved.id)

        then: 'it no longer exists'
        productService.get(saved.id) == null
        productService.count() == 0
    }

    void 'findByName routes to secondary datasource'() {
        given: 'products saved on secondary'
        productService.save(new DataServiceRoutingProduct(name: 'Unique', amount: 77))
        productService.save(new DataServiceRoutingProduct(name: 'Other', amount: 88))

        when: 'we find by name'
        def found = productService.findByName('Unique')

        then: 'the correct entity is returned'
        found != null
        found.name == 'Unique'
        found.amount == 77
    }

    void 'findAllByName routes to secondary datasource'() {
        given: 'products with duplicate names on secondary'
        productService.save(new DataServiceRoutingProduct(name: 'Duplicate', amount: 10))
        productService.save(new DataServiceRoutingProduct(name: 'Duplicate', amount: 20))
        productService.save(new DataServiceRoutingProduct(name: 'Singleton', amount: 30))

        when: 'we find all by name'
        def found = productService.findAllByName('Duplicate')

        then: 'both matching entities are returned'
        found.size() == 2
        found.every { it.name == 'Duplicate' }
    }

    void 'constructor-style save routes to secondary datasource'() {
        when: 'a product is saved using property arguments'
        def saved = productService.saveProduct('Constructed', 55)

        then: 'it is persisted on secondary'
        saved != null
        saved.id != null
        saved.name == 'Constructed'
        saved.amount == 55

        and: 'retrievable'
        productService.get(saved.id) != null
    }

    void 'save, get, and find round-trip through Data Service'() {
        when: 'a product is saved, retrieved by ID, and found by name'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'RoundTrip', amount: 33))
        def byId = productService.get(saved.id)
        def byName = productService.findByName('RoundTrip')

        then: 'all three references point to the same entity'
        saved.id == byId.id
        saved.id == byName.id
        byId.name == 'RoundTrip'
        byName.amount == 33
    }

    // ---- Interface service tests ----

    void 'interface service: save routes to secondary datasource'() {
        when: 'a product is saved through the interface Data Service'
        def saved = productDataService.save(new DataServiceRoutingProduct(name: 'InterfaceWidget', amount: 42))

        then: 'it is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'InterfaceWidget'
        saved.amount == 42

        and: 'it exists on the secondary datasource'
        countOnConnection('secondary') == 1
    }

    void 'interface service: get by ID routes to secondary datasource'() {
        given: 'a product saved on secondary via abstract service'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'InterfaceGet', amount: 99))

        when: 'we retrieve it through the interface Data Service'
        def found = productDataService.get(saved.id)

        then: 'the correct entity is returned'
        found != null
        found.id == saved.id
        found.name == 'InterfaceGet'
    }

    void 'interface service: delete routes to secondary datasource'() {
        given: 'a product saved on secondary'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'InterfaceDelete', amount: 1))

        when: 'we delete through the interface Data Service (FindAndDeleteImplementer)'
        def deleted = productDataService.delete(saved.id)

        then: 'the entity is deleted'
        deleted != null
        deleted.name == 'InterfaceDelete'
        productDataService.get(saved.id) == null
    }

    void 'interface service: void delete routes to secondary datasource'() {
        given: 'a product saved on secondary'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'InterfaceVoidDel', amount: 2))

        when: 'we delete through the interface Data Service (DeleteImplementer)'
        productDataService.deleteProduct(saved.id)

        then: 'the entity is deleted'
        productDataService.get(saved.id) == null
    }

    void 'interface and abstract services share the same datasource'() {
        given: 'a product saved through the abstract service'
        def saved = productService.save(new DataServiceRoutingProduct(name: 'CrossService', amount: 77))

        expect: 'the interface service can find it'
        productDataService.findByName('CrossService') != null
        productDataService.findByName('CrossService').id == saved.id

        and: 'counts match across both service patterns'
        productService.count() == productDataService.count()
    }

    void 'secondary data is not visible on default datasource'() {
        given: 'a product saved on secondary'
        productService.save(new DataServiceRoutingProduct(name: 'SecondaryOnly', amount: 42))

        expect: 'it is not visible on the default datasource'
        countOnConnection(null) == 0
    }

    void 'default data is not visible on secondary datasource'() {
        given: 'a product saved on default'
        saveToConnection(null, 'DefaultOnly', 42)

        expect: 'it is not visible through the secondary-bound service'
        productService.count() == 0
        productService.findByName('DefaultOnly') == null
    }

    // ---- Helper methods ----

    private void saveToConnection(String connectionName, String name, Integer amount) {
        if (connectionName) {
            DataServiceRoutingProduct."${connectionName}".withNewTransaction {
                new DataServiceRoutingProduct(name: name, amount: amount)."${connectionName}".save(flush: true)
            }
        } else {
            DataServiceRoutingProduct.withNewTransaction {
                new DataServiceRoutingProduct(name: name, amount: amount).save(flush: true)
            }
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
