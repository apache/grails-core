/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.connections

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

class DataServiceDatasourceInheritanceSpec extends Specification {

    @Shared Map config = [
            'dataSource.url': 'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.warehouse': [
                    url: 'jdbc:h2:mem:warehouseDB;LOCK_TIMEOUT=10000'
            ],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(config), Inventory
    )

    @Shared InventoryService inventoryService
    @Shared InventoryService defaultDatastoreInventoryService
    @Shared InventoryDataService inventoryDataService

    void setupSpec() {
        inventoryService = datastore
                .getDatastoreForConnection('warehouse')
                .getService(InventoryService)
        defaultDatastoreInventoryService = datastore
                .getService(InventoryService)
        inventoryDataService = datastore
                .getDatastoreForConnection('warehouse')
                .getService(InventoryDataService)
    }

    void setup() {
        Inventory.warehouse.withNewTransaction {
            Inventory.warehouse.executeUpdate('delete from Inventory')
        }
    }

    void 'abstract service without @Transactional(connection) inherits from domain'() {
        when: 'saving through a service that has no @Transactional(connection)'
        def saved = inventoryService.save(new Inventory(sku: 'ABC-001', quantity: 50))

        then: 'the entity is persisted on the warehouse datasource'
        saved != null
        saved.id != null
        saved.sku == 'ABC-001'

        and: 'it exists on the warehouse datasource'
        Inventory.warehouse.withNewTransaction {
            Inventory.warehouse.count()
        } == 1
    }

    void 'service obtained from default datastore still routes to inherited datasource'() {
        when: 'saving through a service obtained from the default datastore'
        def saved = defaultDatastoreInventoryService.save(new Inventory(sku: 'DEFAULT-001', quantity: 33))

        then: 'the entity is persisted'
        saved != null
        saved.id != null

        and: 'it exists on the warehouse datasource'
        Inventory.warehouse.withNewTransaction {
            Inventory.warehouse.count()
        } == 1

        and: 'retrievable through the same service'
        defaultDatastoreInventoryService.get(saved.id) != null
    }

    void 'get by ID routes to inherited datasource'() {
        given: 'an inventory item saved on warehouse'
        def saved = inventoryService.save(new Inventory(sku: 'GET-001', quantity: 10))

        when: 'retrieving by ID'
        def found = inventoryService.get(saved.id)

        then: 'the correct entity is returned'
        found != null
        found.id == saved.id
        found.sku == 'GET-001'
    }

    void 'delete routes to inherited datasource'() {
        given: 'an inventory item saved on warehouse'
        def saved = inventoryService.save(new Inventory(sku: 'DEL-001', quantity: 5))

        when: 'deleting by ID'
        def deleted = inventoryService.delete(saved.id)

        then: 'the entity is deleted'
        deleted != null
        deleted.sku == 'DEL-001'
        inventoryService.get(saved.id) == null
    }

    void 'count routes to inherited datasource'() {
        given: 'items saved on warehouse'
        inventoryService.save(new Inventory(sku: 'CNT-001', quantity: 1))
        inventoryService.save(new Inventory(sku: 'CNT-002', quantity: 2))

        expect: 'count returns 2'
        inventoryService.count() == 2
    }

    void 'findBySku routes to inherited datasource'() {
        given: 'items saved on warehouse'
        inventoryService.save(new Inventory(sku: 'FIND-001', quantity: 100))

        when: 'finding by sku'
        def found = inventoryService.findBySku('FIND-001')

        then: 'the correct entity is returned'
        found != null
        found.sku == 'FIND-001'
        found.quantity == 100
    }

    void 'interface service inherits datasource from domain'() {
        when: 'saving through an interface service with no @Transactional(connection)'
        def saved = inventoryDataService.save(new Inventory(sku: 'IFACE-001', quantity: 25))

        then: 'the entity is persisted on warehouse'
        saved != null
        saved.id != null

        and: 'retrievable through the same service'
        inventoryDataService.get(saved.id) != null
    }

    void 'explicit @Transactional(connection) is preserved and not overwritten by domain datasource'() {
        when: "checking the annotation on a service with explicit @Transactional(connection='archive')"
        def transactionalAnn = ExplicitArchiveInventoryService.getAnnotation(Transactional)

        then: "the explicit connection value 'archive' is preserved, not overwritten with domain's 'warehouse'"
        transactionalAnn != null
        transactionalAnn.connection() == 'archive'

        and: "the inherited service uses the domain's 'warehouse' connection"
        def inheritedAnn = InventoryService.getAnnotation(Transactional)
        inheritedAnn != null
        inheritedAnn.connection() == 'warehouse'
    }

    void 'abstract and interface services share the same inherited datasource'() {
        given: 'an item saved through the abstract service'
        def saved = inventoryService.save(new Inventory(sku: 'CROSS-001', quantity: 42))

        expect: 'the interface service can find it'
        inventoryDataService.findBySku('CROSS-001') != null
        inventoryDataService.findBySku('CROSS-001').id == saved.id
    }

}

@Entity
class Inventory {

    Long id
    Long version
    String sku
    Integer quantity

    static mapping = {
        datasource('warehouse')
    }
    static constraints = {
        sku(blank: false)
    }
}

@Service(Inventory)
abstract class InventoryService {

    abstract Inventory get(Serializable id)

    abstract Inventory save(Inventory item)

    abstract Inventory delete(Serializable id)

    abstract Number count()

    abstract Inventory findBySku(String sku)
}

@Service(Inventory)
interface InventoryDataService {

    Inventory get(Serializable id)

    Inventory save(Inventory item)

    Inventory delete(Serializable id)

    Inventory findBySku(String sku)
}

@Service(Inventory)
@Transactional(connection = 'archive')
abstract class ExplicitArchiveInventoryService {

    abstract Inventory get(Serializable id)

    abstract Inventory save(Inventory item)
}
