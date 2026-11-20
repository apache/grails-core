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
package org.grails.orm.hibernate.connections

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

@Issue("https://github.com/apache/grails-core/issues/15416")
class WhereQueryMultiDataSourceSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:defaultDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:secondaryDB;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(config), Item
    )

    @Shared ItemQueryService itemQueryService

    void setupSpec() {
        itemQueryService = datastore
                .getDatastoreForConnection('secondary')
                .getService(ItemQueryService)
    }

    void cleanup() {
        Item.secondary.withNewTransaction {
            Item.secondary.deleteAll(Item.secondary.list())
        }
        Item.withNewTransaction {
            Item.deleteAll(Item.list())
        }
    }

    void "@Where query routes to secondary datasource"() {
        given:
        saveToSecondary('Cheap', 10.0)
        saveToSecondary('Expensive', 500.0)

        when:
        def results = itemQueryService.findByMinAmount(100.0)

        then:
        results.size() == 1
        results[0].name == 'Expensive'
    }

    void "@Where query does not return data from default datasource"() {
        given: 'an item saved to secondary'
        saveToSecondary('OnSecondary', 50.0)

        and: 'a different item saved directly to default'
        saveToDefault('OnDefault', 999.0)

        when: 'querying via @Where for amount >= 500 on secondary-bound service'
        def results = itemQueryService.findByMinAmount(500.0)

        then: 'only secondary data is searched - default item is NOT found'
        results.size() == 0
    }

    void "count routes to secondary datasource"() {
        given:
        saveToSecondary('A', 1.0)
        saveToSecondary('B', 2.0)

        and: 'an item on default that should not be counted'
        saveToDefault('C', 3.0)

        expect:
        itemQueryService.count() == 2
    }

    void "list routes to secondary datasource"() {
        given:
        saveToSecondary('X', 10.0)
        saveToSecondary('Y', 20.0)

        and: 'an item on default that should not be listed'
        saveToDefault('Z', 30.0)

        when:
        def all = itemQueryService.list()

        then:
        all.size() == 2
    }

    void "findByName routes to secondary datasource"() {
        given:
        saveToSecondary('Unique', 77.0)

        when:
        def found = itemQueryService.findByName('Unique')

        then:
        found != null
        found.name == 'Unique'
        found.amount == 77.0
    }

    private void saveToSecondary(String name, Double amount) {
        Item.secondary.withNewTransaction {
            new Item(name: name, amount: amount).secondary.save(flush: true)
        }
    }

    private void saveToDefault(String name, Double amount) {
        Item.withNewTransaction {
            new Item(name: name, amount: amount).save(flush: true)
        }
    }
}

@Entity
class Item implements GormEntity<Item> {
    Long id
    Long version
    String name
    Double amount

    static mapping = {
        datasource 'ALL'
    }

    static constraints = {
        name blank: false
        amount nullable: false
    }
}

@Service(Item)
@Transactional(connection = 'secondary')
interface ItemQueryService {

    Item findByName(String name)

    Number count()

    List<Item> list()

    @Where({ amount >= minAmount })
    List<Item> findByMinAmount(Double minAmount)
}
