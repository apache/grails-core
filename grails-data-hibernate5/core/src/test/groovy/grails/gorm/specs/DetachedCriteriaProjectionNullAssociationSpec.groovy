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
package grails.gorm.specs

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests that DetachedCriteria projections on nullable association properties
 * correctly include rows where the association is null.
 */
class DetachedCriteriaProjectionNullAssociationSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Shipment, Warehouse)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Transactional
    def setup() {
        Shipment.findAll().each { it.delete() }
        Warehouse.findAll().each { it.delete(flush: true) }
    }

    @Rollback
    def 'distinct projection on nullable association property includes rows with null association'() {
        given: 'shipments with and without a warehouse'
        def warehouse = new Warehouse(name: 'Main').save(flush: true)
        new Shipment(description: 'With warehouse', warehouse: warehouse).save(flush: true)
        new Shipment(description: 'No warehouse', warehouse: null).save(flush: true)

        when: 'projecting distinct warehouse IDs'
        def results = new DetachedCriteria(Shipment).build {
            projections {
                distinct('warehouse.id')
            }
        }.list()

        then: 'both the warehouse ID and null should be returned'
        results.size() == 2
        results.contains(warehouse.id)
        results.contains(null)
    }

    @Rollback
    def 'property projection on nullable association includes rows with null association'() {
        given: 'shipments with and without a warehouse'
        def warehouse = new Warehouse(name: 'Central').save(flush: true)
        new Shipment(description: 'Has warehouse', warehouse: warehouse).save(flush: true)
        new Shipment(description: 'Missing warehouse', warehouse: null).save(flush: true)

        when: 'projecting warehouse IDs without distinct'
        def results = new DetachedCriteria(Shipment).build {
            projections {
                property('warehouse.id')
            }
        }.list()

        then: 'both values should be returned including null'
        results.size() == 2
        results.contains(warehouse.id)
        results.contains(null)
    }

    @Rollback
    def 'distinct id with property projection on nullable association includes null rows'() {
        given: 'shipments with and without a warehouse'
        def warehouse = new Warehouse(name: 'North').save(flush: true)
        new Shipment(description: 'Assigned', warehouse: warehouse).save(flush: true)
        new Shipment(description: 'Unassigned', warehouse: null).save(flush: true)

        when: 'using distinct on id and property on nullable association'
        def results = Shipment.where {}.distinct('id').property('warehouse.id').list()

        then: 'all rows should be returned'
        results.size() == 2
    }

    @Rollback
    def 'multiple projections with nullable association property preserve null rows'() {
        given: 'shipments with and without a warehouse'
        def warehouse = new Warehouse(name: 'South').save(flush: true)
        new Shipment(description: 'Stored', warehouse: warehouse).save(flush: true)
        new Shipment(description: 'In transit', warehouse: null).save(flush: true)

        when: 'projecting both id and nullable warehouse.id'
        def results = new DetachedCriteria(Shipment).build {
            projections {
                property('id')
                property('warehouse.id')
            }
        }.list()

        then: 'both rows should be returned'
        results.size() == 2
        def warehouseIds = results.collect { it[1] }
        warehouseIds.contains(warehouse.id)
        warehouseIds.contains(null)
    }
}

@Entity
class Shipment implements Serializable {

    String description
    Warehouse warehouse

    static constraints = {
        warehouse nullable: true
    }
}

@Entity
class Warehouse implements Serializable {

    String name
}
