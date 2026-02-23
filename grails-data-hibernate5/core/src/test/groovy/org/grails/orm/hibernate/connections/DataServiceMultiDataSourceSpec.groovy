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
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.services.Query
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Integration tests for GORM Data Service auto-implemented CRUD methods
 * routing to a non-default datasource via @Transactional(connection).
 *
 * The Product domain is mapped exclusively to the 'books' datasource.
 * Without the connection-routing fix, auto-implemented save/get/delete
 * would attempt to use the default datasource (where no Product table
 * exists), causing failures.
 *
 * Tests both patterns:
 * - Abstract class implementing interface (ProductService)
 * - Interface-only with @Transactional(connection) (ProductDataService)
 *
 * @see org.grails.datastore.gorm.services.implementers.SaveImplementer
 * @see org.grails.datastore.gorm.services.implementers.DeleteImplementer
 * @see org.grails.datastore.gorm.services.implementers.FindAndDeleteImplementer
 * @see org.grails.datastore.gorm.services.implementers.AbstractDetachedCriteriaServiceImplementor
 */
class DataServiceMultiDataSourceSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.books':[url:"jdbc:h2:mem:booksDB;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(config), Product
    )

    @Shared ProductService productService
    @Shared ProductDataService productDataService

    void setupSpec() {
        productService = datastore
                .getDatastoreForConnection('books')
                .getService(ProductService)
        productDataService = datastore
                .getDatastoreForConnection('books')
                .getService(ProductDataService)
    }

    void setup() {
        def api = GormEnhancer.findStaticApi(Product, 'books')
        api.withNewTransaction {
            api.executeUpdate('delete from Product')
        }
    }

    void "schema is created on the books datasource"() {
        when: 'we query the books datasource for the product table'
        def api = GormEnhancer.findStaticApi(Product, 'books')
        def result = api.withNewTransaction {
            api.executeQuery('SELECT 1 FROM Product p WHERE 1=0')
        }

        then: 'no exception - table exists on books'
        noExceptionThrown()
        result != null
    }

    void "save routes to books datasource"() {
        when: 'a product is saved through the Data Service'
        def saved = productService.save(new Product(name: 'Widget', amount: 42))

        then: 'it is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'Widget'
        saved.amount == 42

        and: 'it exists on the books datasource'
        GormEnhancer.findStaticApi(Product, 'books').withNewTransaction {
            GormEnhancer.findStaticApi(Product, 'books').count()
        } == 1
    }

    void "get by ID routes to books datasource"() {
        given: 'a product saved on books'
        def saved = productService.save(new Product(name: 'Gadget', amount: 99))

        when: 'we retrieve it by ID'
        def found = productService.get(saved.id)

        then: 'the correct entity is returned'
        found != null
        found.id == saved.id
        found.name == 'Gadget'
        found.amount == 99
    }

    void "count routes to books datasource"() {
        given: 'two products saved on books'
        productService.save(new Product(name: 'Alpha', amount: 10))
        productService.save(new Product(name: 'Beta', amount: 20))

        expect: 'count returns 2'
        productService.count() == 2
    }

    void "delete by ID routes to books datasource - FindAndDeleteImplementer"() {
        given: 'a product saved on books'
        def saved = productService.save(new Product(name: 'Ephemeral', amount: 1))

        when: 'we delete it using delete(id) which returns the domain object'
        def deleted = productService.delete(saved.id)

        then: 'the deleted entity is returned and no longer exists'
        deleted != null
        deleted.name == 'Ephemeral'
        productService.get(saved.id) == null
        productService.count() == 0
    }

    void "delete by ID routes to books datasource - DeleteImplementer"() {
        given: 'a product saved on books'
        def saved = productService.save(new Product(name: 'AlsoEphemeral', amount: 2))

        when: 'we delete it using void deleteProduct(id)'
        productService.deleteProduct(saved.id)

        then: 'it no longer exists'
        productService.get(saved.id) == null
        productService.count() == 0
    }

    void "findByName routes to books datasource"() {
        given: "products saved on books"
        productService.save(new Product(name: 'Unique', amount: 77))
        productService.save(new Product(name: 'Other', amount: 88))

        when: "we find by name"
        def found = productService.findByName('Unique')

        then: "the correct entity is returned"
        found != null
        found.name == 'Unique'
        found.amount == 77
    }

    void "findAllByName routes to books datasource"() {
        given: 'products with duplicate names on books'
        productService.save(new Product(name: 'Duplicate', amount: 10))
        productService.save(new Product(name: 'Duplicate', amount: 20))
        productService.save(new Product(name: 'Singleton', amount: 30))

        when: 'we find all by name'
        def found = productService.findAllByName('Duplicate')

        then: 'both matching entities are returned'
        found.size() == 2
        found.every { it.name == 'Duplicate' }
    }

    void "GormEnhancer escape-hatch HQL works on books datasource"() {
        given: 'products saved on books'
        productService.save(new Product(name: 'Foo', amount: 100))
        productService.save(new Product(name: 'Bar', amount: 200))

        when: 'we run aggregate HQL through GormEnhancer'
        def api = GormEnhancer.findStaticApi(Product, 'books')
        def result = api.withNewTransaction {
            api.executeQuery('SELECT SUM(p.amount) FROM Product p')
        }

        then: 'the aggregation reflects books data'
        result[0] == 300
    }

    void "save, get, and find round-trip through Data Service"() {
        when: 'a product is saved, retrieved by ID, and found by name'
        def saved = productService.save(new Product(name: 'RoundTrip', amount: 33))
        def byId = productService.get(saved.id)
        def byName = productService.findByName('RoundTrip')

        then: 'all three references point to the same entity'
        saved.id == byId.id
        saved.id == byName.id
        byId.name == 'RoundTrip'
        byName.amount == 33
    }

    void "save with constructor-style arguments routes to books datasource"() {
        when: 'a product is saved using property arguments'
        def saved = productService.saveProduct('Constructed', 55)

        then: 'it is persisted on books'
        saved != null
        saved.id != null
        saved.name == 'Constructed'
        saved.amount == 55

        and: 'retrievable'
        productService.get(saved.id) != null
    }

    // ---- Interface-pattern Data Service tests ----

    void "interface service: save routes to books datasource"() {
        when: 'a product is saved through the interface Data Service'
        def saved = productDataService.save(new Product(name: 'InterfaceWidget', amount: 42))

        then: 'it is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'InterfaceWidget'
        saved.amount == 42

        and: 'it exists on the books datasource'
        GormEnhancer.findStaticApi(Product, 'books').withNewTransaction {
            GormEnhancer.findStaticApi(Product, 'books').count()
        } == 1
    }

    void "interface service: get by ID routes to books datasource"() {
        given: 'a product saved on books via abstract service'
        def saved = productService.save(new Product(name: 'InterfaceGet', amount: 99))

        when: 'we retrieve it through the interface Data Service'
        def found = productDataService.get(saved.id)

        then: 'the correct entity is returned'
        found != null
        found.id == saved.id
        found.name == 'InterfaceGet'
    }

    void "interface service: delete routes to books datasource"() {
        given: 'a product saved on books'
        def saved = productService.save(new Product(name: 'InterfaceDelete', amount: 1))

        when: 'we delete through the interface Data Service (FindAndDeleteImplementer)'
        def deleted = productDataService.delete(saved.id)

        then: 'the entity is deleted'
        deleted != null
        deleted.name == 'InterfaceDelete'
        productDataService.get(saved.id) == null
    }

    void "interface service: void delete routes to books datasource"() {
        given: 'a product saved on books'
        def saved = productService.save(new Product(name: 'InterfaceVoidDel', amount: 2))

        when: 'we delete through the interface Data Service (DeleteImplementer)'
        productDataService.deleteProduct(saved.id)

        then: 'the entity is deleted'
        productDataService.get(saved.id) == null
    }

    void "interface and abstract services share the same datasource"() {
        given: 'a product saved through the abstract service'
        def saved = productService.save(new Product(name: 'CrossService', amount: 77))

        expect: 'the interface service can find it and vice versa'
        productDataService.findByName('CrossService') != null
        productDataService.findByName('CrossService').id == saved.id

        and: 'counts match across both service patterns'
        productService.count() == productDataService.count()
    }

    void "@Query find-one routes to books datasource - abstract service"() {
        given: 'a product saved on books'
        productService.save(new Product(name: 'QueryOne', amount: 50))

        when: 'we find one by HQL query'
        def found = productService.findOneByQuery('QueryOne')

        then: 'the correct entity is returned from books'
        found != null
        found.name == 'QueryOne'
        found.amount == 50
    }

    void "@Query find-one returns null for non-existent - abstract service"() {
        expect: 'null for non-existent product'
        productService.findOneByQuery('NonExistent') == null
    }

    void "@Query find-all routes to books datasource - abstract service"() {
        given: 'products saved on books with varying amounts'
        productService.save(new Product(name: 'Expensive1', amount: 500))
        productService.save(new Product(name: 'Expensive2', amount: 600))
        productService.save(new Product(name: 'Cheap1', amount: 10))

        when: 'we find all by HQL query with threshold'
        def found = productService.findAllByQuery(400)

        then: 'only matching products from books are returned'
        found.size() == 2
        found*.name.containsAll(['Expensive1', 'Expensive2'])
    }

    void "@Query update routes to books datasource - abstract service"() {
        given: 'a product saved on books'
        productService.save(new Product(name: 'UpdateTarget', amount: 100))

        when: 'we update amount by HQL query'
        def updated = productService.updateAmountByName('UpdateTarget', 999)

        then: 'one row updated'
        updated == 1

        and: 'the change is reflected on books'
        productService.findByName('UpdateTarget').amount == 999
    }

    void "@Query find-one routes to books datasource - interface service"() {
        given: 'a product saved on books'
        productService.save(new Product(name: 'InterfaceQueryOne', amount: 75))

        when: 'we find one by HQL query through the interface service'
        def found = productDataService.findOneByQuery('InterfaceQueryOne')

        then: 'the correct entity is returned from books'
        found != null
        found.name == 'InterfaceQueryOne'
        found.amount == 75
    }

    void "@Query find-all routes to books datasource - interface service"() {
        given: 'products saved on books'
        productService.save(new Product(name: 'IfaceExpensive1', amount: 500))
        productService.save(new Product(name: 'IfaceExpensive2', amount: 600))
        productService.save(new Product(name: 'IfaceCheap1', amount: 10))

        when: 'we find all by HQL query through the interface service'
        def found = productDataService.findAllByQuery(400)

        then: 'only matching products from books are returned'
        found.size() == 2
        found*.name.containsAll(['IfaceExpensive1', 'IfaceExpensive2'])
    }

    void "@Query update routes to books datasource - interface service"() {
        given: 'a product saved on books'
        productService.save(new Product(name: 'InterfaceUpdate', amount: 100))

        when: 'we update amount by HQL query through the interface service'
        def updated = productDataService.updateAmountByName('InterfaceUpdate', 888)

        then: 'one row updated'
        updated == 1

        and: 'the change is reflected on books'
        productDataService.findByName('InterfaceUpdate').amount == 888
    }

}

@Entity
class Product {
    Long id
    Long version
    String name
    Integer amount

    static mapping = {
        datasource 'books'
    }
    static constraints = {
        name blank: false
    }
}

@Service(Product)
@Transactional(connection = 'books')
abstract class ProductService {

    abstract Product get(Serializable id)

    abstract Product save(Product product)

    abstract Product delete(Serializable id)

    abstract void deleteProduct(Serializable id)

    abstract Number count()

    abstract Product findByName(String name)

    abstract List<Product> findAllByName(String name)

    /**
     * Constructor-style save - GORM creates the entity from parameters.
     * Tests that SaveImplementer routes multi-arg saves through connection-aware API.
     */
    abstract Product saveProduct(String name, Integer amount)

    @Query("from ${Product p} where $p.name = $name")
    abstract Product findOneByQuery(String name)


    @Query("from ${Product p} where $p.amount >= $minAmount")
    abstract List<Product> findAllByQuery(Integer minAmount)

    @Query("update ${Product p} set $p.amount = $newAmount where $p.name = $name")
    abstract Number updateAmountByName(String name, Integer newAmount)
}

/**
 * Interface-only Data Service pattern.
 * Verifies that connection routing works identically whether the service
 * is declared as an interface or an abstract class.
 */
@Service(Product)
@Transactional(connection = 'books')
interface ProductDataService {

    Product get(Serializable id)

    Product save(Product product)

    Product delete(Serializable id)

    void deleteProduct(Serializable id)

    Number count()

    Product findByName(String name)

    List<Product> findAllByName(String name)

    @Query("from ${Product p} where $p.name = $name")
    Product findOneByQuery(String name)

    @Query("from ${Product p} where $p.amount >= $minAmount")
    List<Product> findAllByQuery(Integer minAmount)

    @Query("update ${Product p} set $p.amount = $newAmount where $p.name = $name")
    Number updateAmountByName(String name, Integer newAmount)
}
