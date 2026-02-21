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

import org.grails.datastore.gorm.GormEnhancer

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProduct

@Requires({ instance.manager?.supportsMultipleDataSources() })
class DomainMultiDataSourceSpec extends GrailsDataTckSpec {

    void setup() {
        manager.setupMultiDataSource(DataServiceRoutingProduct)
    }

    void cleanup() {
        deleteAllFromConnection('secondary')
        deleteAllFromConnection(null)
        manager.cleanupMultiDataSource()
    }

    void "save to secondary datasource via domain API"() {
        given: 'a secondary connection API'
        def staticApi = GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary')
        def instanceApi = GormEnhancer.findInstanceApi(DataServiceRoutingProduct, 'secondary')

        when: 'a product is saved through the instance API'
        staticApi.withNewTransaction {
            instanceApi.save(new DataServiceRoutingProduct(name: 'Widget', amount: 42), [flush: true])
        }

        then: 'the secondary datasource records the entity'
        countOnConnection('secondary') == 1
    }

    void "get by ID from secondary datasource via domain API"() {
        given: 'a product saved on secondary'
        def staticApi = GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary')
        def instanceApi = GormEnhancer.findInstanceApi(DataServiceRoutingProduct, 'secondary')
        def id = staticApi.withNewTransaction {
            def saved = new DataServiceRoutingProduct(name: 'Gadget', amount: 99)
            instanceApi.save(saved, [flush: true])
            saved.id
        }

        when: 'the product is retrieved by ID'
        def found = staticApi.withNewTransaction {
            staticApi.get(id)
        }

        then: 'the correct entity is returned'
        found != null
        found.id == id
        found.name == 'Gadget'
    }

    void "count on secondary datasource via domain API"() {
        given: 'two products saved on secondary'
        saveToConnection('secondary', 'Alpha', 10)
        saveToConnection('secondary', 'Beta', 20)

        and: 'a product saved on default'
        saveToConnection(null, 'DefaultOnly', 99)

        expect: 'only secondary records are counted'
        countOnConnection('secondary') == 2
    }

    void "list on secondary datasource via domain API"() {
        given: 'three products on secondary'
        saveToConnection('secondary', 'One', 1)
        saveToConnection('secondary', 'Two', 2)
        saveToConnection('secondary', 'Three', 3)

        when: 'listing through the secondary static API'
        def items = GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary').withNewTransaction {
            GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary').list()
        }

        then: 'all secondary items are returned'
        items.size() == 3
    }

    void "criteria query on secondary datasource via domain API"() {
        given: 'two products with different names'
        saveToConnection('secondary', 'Match', 1)
        saveToConnection('secondary', 'Other', 2)

        when: 'querying by name'
        def results = GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary').withNewTransaction {
            GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary').withCriteria {
                eq 'name', 'Match'
            }
        }

        then: 'only matching entities are returned'
        results.size() == 1
        results.first().name == 'Match'
    }

    void "delete from secondary datasource via domain API"() {
        given: 'a product saved on secondary'
        def staticApi = GormEnhancer.findStaticApi(DataServiceRoutingProduct, 'secondary')
        def instanceApi = GormEnhancer.findInstanceApi(DataServiceRoutingProduct, 'secondary')
        def saved = staticApi.withNewTransaction {
            def item = new DataServiceRoutingProduct(name: 'Disposable', amount: 5)
            instanceApi.save(item, [flush: true])
            item
        }

        when: 'the product is deleted'
        staticApi.withNewTransaction {
            instanceApi.delete(saved, [flush: true])
        }

        then: 'the secondary datasource is empty'
        countOnConnection('secondary') == 0
    }

    void "secondary data not visible on default via domain API"() {
        given: 'a product saved on secondary'
        saveToConnection('secondary', 'SecondaryOnly', 42)

        expect: 'the default connection sees no records'
        countOnConnection(null) == 0
    }

    void "default data not visible on secondary via domain API"() {
        given: 'a product saved on default'
        saveToConnection(null, 'DefaultOnly', 42)

        expect: 'secondary does not see default data'
        countOnConnection('secondary') == 0
    }

    private void saveToConnection(String connectionName, String name, Integer amount) {
        def staticApi = connectionName
                ? GormEnhancer.findStaticApi(DataServiceRoutingProduct, connectionName)
                : GormEnhancer.findStaticApi(DataServiceRoutingProduct)
        def instanceApi = connectionName
                ? GormEnhancer.findInstanceApi(DataServiceRoutingProduct, connectionName)
                : GormEnhancer.findInstanceApi(DataServiceRoutingProduct)
        staticApi.withNewTransaction {
            instanceApi.save(new DataServiceRoutingProduct(name: name, amount: amount), [flush: true])
        }
    }

    private long countOnConnection(String connectionName) {
        def staticApi = connectionName
                ? GormEnhancer.findStaticApi(DataServiceRoutingProduct, connectionName)
                : GormEnhancer.findStaticApi(DataServiceRoutingProduct)
        staticApi.withNewTransaction {
            staticApi.count()
        }
    }

    private void deleteAllFromConnection(String connectionName) {
        def staticApi = connectionName
                ? GormEnhancer.findStaticApi(DataServiceRoutingProduct, connectionName)
                : GormEnhancer.findStaticApi(DataServiceRoutingProduct)
        def instanceApi = connectionName
                ? GormEnhancer.findInstanceApi(DataServiceRoutingProduct, connectionName)
                : GormEnhancer.findInstanceApi(DataServiceRoutingProduct)
        staticApi.withNewTransaction {
            for (item in staticApi.list()) {
                instanceApi.delete(item, [flush: true])
            }
        }
    }
}
