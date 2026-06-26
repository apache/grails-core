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

import spock.lang.Issue
import spock.lang.Requires

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.WhereRoutingItem
import org.apache.grails.data.testing.tck.domains.WhereRoutingItemService

@Issue('https://github.com/apache/grails-core/issues/15416')
@Requires({ instance.manager?.supportsMultipleDataSources() })
class WhereQueryConnectionRoutingSpec extends GrailsDataTckSpec {

    WhereRoutingItemService itemService

    void setup() {
        manager.setupMultiDataSource(WhereRoutingItem)
        itemService = manager.getServiceForConnection(WhereRoutingItemService, 'secondary')
    }

    void cleanup() {
        deleteAllFromConnection('secondary')
        deleteAllFromConnection(null)
        manager.cleanupMultiDataSource()
    }

    void '@Where query routes to secondary datasource'() {
        given:
        saveToConnection('secondary', 'Cheap', 10.0)
        saveToConnection('secondary', 'Expensive', 500.0)

        when:
        def results = itemService.findByMinAmount(100.0)

        then:
        results.size() == 1
        results[0].name == 'Expensive'
    }

    void '@Where query does not return data from default datasource'() {
        given: 'an item saved to secondary'
        saveToConnection('secondary', 'OnSecondary', 50.0)

        and: 'a different item saved to default'
        saveToConnection(null, 'OnDefault', 999.0)

        when: 'querying via @Where for amount >= 500 on secondary-bound service'
        def results = itemService.findByMinAmount(500.0)

        then: 'only secondary data is searched'
        results.size() == 0
    }

    void 'count routes to secondary datasource'() {
        given:
        saveToConnection('secondary', 'A', 1.0)
        saveToConnection('secondary', 'B', 2.0)

        and: 'an item on default that should not be counted'
        saveToConnection(null, 'C', 3.0)

        expect:
        itemService.count() == 2
    }

    void 'list routes to secondary datasource'() {
        given:
        saveToConnection('secondary', 'X', 10.0)
        saveToConnection('secondary', 'Y', 20.0)

        and: 'an item on default that should not be listed'
        saveToConnection(null, 'Z', 30.0)

        when:
        def all = itemService.list()

        then:
        all.size() == 2
    }

    void 'findByName routes to secondary datasource'() {
        given:
        saveToConnection('secondary', 'Unique', 77.0)

        when:
        def found = itemService.findByName('Unique')

        then:
        found != null
        found.name == 'Unique'
        found.amount == 77.0
    }

    private void saveToConnection(String connectionName, String name, Double amount) {
        if (connectionName) {
            WhereRoutingItem."${connectionName}".withNewTransaction {
                new WhereRoutingItem(name: name, amount: amount)."${connectionName}".save(flush: true)
            }
        } else {
            WhereRoutingItem.withNewTransaction {
                new WhereRoutingItem(name: name, amount: amount).save(flush: true)
            }
        }
    }

    private void deleteAllFromConnection(String connectionName) {
        if (connectionName) {
            WhereRoutingItem."${connectionName}".withNewTransaction {
                WhereRoutingItem."${connectionName}".list().each { it."${connectionName}".delete(flush: true) }
            }
        } else {
            WhereRoutingItem.withNewTransaction {
                WhereRoutingItem.list().each { it.delete(flush: true) }
            }
        }
    }

}
