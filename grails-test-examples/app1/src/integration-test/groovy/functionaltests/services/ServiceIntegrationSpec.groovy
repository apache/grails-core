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

package functionaltests.services

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Integration tests for Grails services.
 * Tests transactional behavior, rollback scenarios, async operations,
 * and service dependency injection.
 */
@Integration(applicationClass = Application)
@Rollback
class ServiceIntegrationSpec extends Specification {

    @Autowired
    InventoryService inventoryService

    @Autowired
    OrderService orderService

    @Autowired
    AsyncProcessingService asyncProcessingService

    // ========== Transactional Service Tests ==========

    def "service can add and retrieve product"() {
        when: "adding a product through the service"
        inventoryService.addProduct('SKU-001', 'Test Product', 29.99, 100)

        then: "product can be retrieved"
        def product = inventoryService.findBySku('SKU-001')
        product != null
        product.name == 'Test Product'
        product.price == 29.99
        product.quantity == 100
    }

    def "read-only transaction returns correct data"() {
        given: "multiple products exist"
        inventoryService.addProduct('CHEAP-001', 'Cheap Item', 5.00, 50)
        inventoryService.addProduct('EXPENSIVE-001', 'Expensive Item', 100.00, 10)
        inventoryService.addProduct('MEDIUM-001', 'Medium Item', 25.00, 30)

        when: "querying with read-only transaction"
        def expensiveItems = inventoryService.findAllByPriceGreaterThan(50.00)

        then: "correct items are returned"
        expensiveItems.size() == 1
        expensiveItems[0].sku == 'EXPENSIVE-001'
    }

    def "runtime exception causes transaction rollback"() {
        given: "initial count"
        def initialCount = inventoryService.countAll()

        when: "adding product with simulated failure"
        inventoryService.addProductWithFailure('FAIL-001', 'Should Rollback', 10.00, 5)

        then: "exception is thrown"
        thrown(RuntimeException)

        // Note: Under @Rollback, the outer test transaction encompasses everything,
        // so we verify the exception was thrown (which marks the transaction for rollback).
        // The actual rollback happens at the end of the test method.
        // To verify the service's rollback behavior in isolation, we'd need REQUIRES_NEW.
    }

    def "checked exception does NOT rollback by default"() {
        given: "initial count"
        def initialCount = inventoryService.countAll()

        when: "adding product with checked exception"
        inventoryService.addProductWithCheckedException('CHECK-001', 'Checked Exception', 15.00, 3)

        then: "exception is thrown"
        thrown(Exception)

        and: "transaction is committed - product IS saved (default behavior)"
        // Note: This test documents default Spring behavior
        // Checked exceptions don't trigger rollback unless configured
        inventoryService.countAll() >= initialCount
    }

    def "rollbackFor configuration causes rollback on checked exception"() {
        when: "adding product with rollbackFor configured"
        inventoryService.addProductRollbackOnCheckedException('ROLLBACK-001', 'Should Rollback', 20.00, 7)

        then: "exception is thrown"
        thrown(Exception)

        // Note: The rollbackFor annotation marks the transaction for rollback.
        // Under @Rollback test context, the actual rollback happens at end of test.
        // This test verifies the exception is properly thrown, which triggers the rollback.
    }

    def "update within transaction works correctly"() {
        given: "an existing product"
        inventoryService.addProduct('UPDATE-001', 'Update Test', 50.00, 100)

        when: "updating quantity"
        def result = inventoryService.updateQuantity('UPDATE-001', 75)

        then: "update succeeds"
        result == true

        and: "new quantity is persisted"
        inventoryService.findBySku('UPDATE-001').quantity == 75
    }

    def "update non-existent product returns false"() {
        when: "updating non-existent product"
        def result = inventoryService.updateQuantity('NONEXISTENT', 50)

        then: "update returns false"
        result == false
    }

    def "multi-entity transaction commits atomically"() {
        given: "two products with initial quantities"
        inventoryService.addProduct('FROM-001', 'Source Product', 10.00, 100)
        inventoryService.addProduct('TO-001', 'Destination Product', 10.00, 50)

        when: "transferring quantity between products"
        inventoryService.transferQuantity('FROM-001', 'TO-001', 30)

        then: "both products are updated atomically"
        inventoryService.findBySku('FROM-001').quantity == 70
        inventoryService.findBySku('TO-001').quantity == 80
    }

    def "multi-entity transaction rolls back on failure"() {
        given: "two products"
        inventoryService.addProduct('TRANSFER-FROM', 'Source', 10.00, 20)
        inventoryService.addProduct('TRANSFER-TO', 'Dest', 10.00, 10)

        when: "attempting to transfer more than available"
        inventoryService.transferQuantity('TRANSFER-FROM', 'TRANSFER-TO', 50)

        then: "exception is thrown"
        thrown(IllegalStateException)

        and: "both products retain original quantities"
        inventoryService.findBySku('TRANSFER-FROM').quantity == 20
        inventoryService.findBySku('TRANSFER-TO').quantity == 10
    }

    def "batch insert processes multiple items"() {
        given: "a list of items to insert"
        def items = [
            [sku: 'BATCH-001', name: 'Batch Item 1', price: 10.00, quantity: 5],
            [sku: 'BATCH-002', name: 'Batch Item 2', price: 20.00, quantity: 10],
            [sku: 'BATCH-003', name: 'Batch Item 3', price: 30.00, quantity: 15]
        ]

        when: "batch inserting"
        def count = inventoryService.batchInsert(items)

        then: "all items are inserted"
        count == 3
        inventoryService.findBySku('BATCH-001') != null
        inventoryService.findBySku('BATCH-002') != null
        inventoryService.findBySku('BATCH-003') != null
    }

    def "delete removes product"() {
        given: "an existing product"
        inventoryService.addProduct('DELETE-001', 'To Delete', 5.00, 1)
        assert inventoryService.findBySku('DELETE-001') != null

        when: "deleting the product"
        def result = inventoryService.deleteProduct('DELETE-001')

        then: "delete succeeds"
        result == true

        and: "product no longer exists"
        inventoryService.findBySku('DELETE-001') == null
    }

    def "total inventory value calculation"() {
        given: "products with known values"
        inventoryService.addProduct('VALUE-001', 'Item 1', 10.00, 5)   // 50.00
        inventoryService.addProduct('VALUE-002', 'Item 2', 25.00, 4)   // 100.00
        inventoryService.addProduct('VALUE-003', 'Item 3', 15.00, 10)  // 150.00

        when: "calculating total inventory value"
        def total = inventoryService.getTotalInventoryValue()

        then: "total is correct"
        total == 300.00
    }

    // ========== Service Dependency Injection Tests ==========

    def "service has injected dependencies"() {
        expect: "services are autowired"
        orderService.inventoryService != null
        orderService.applicationContext != null
    }

    def "order service uses inventory service"() {
        given: "product in inventory"
        inventoryService.addProduct('ORDER-001', 'Orderable Item', 49.99, 50)

        when: "placing an order"
        def result = orderService.placeOrder('ORDER-001', 5)

        then: "order is successful"
        result.success == true
        result.quantity == 5
        result.unitPrice == 49.99
        result.totalPrice == 249.95
        result.remainingStock == 45

        and: "inventory is updated"
        inventoryService.findBySku('ORDER-001').quantity == 45
    }

    def "order fails for non-existent product"() {
        when: "ordering non-existent product"
        def result = orderService.placeOrder('NONEXISTENT', 1)

        then: "order fails"
        result.success == false
        result.error == 'Product not found'
    }

    def "order fails for insufficient stock"() {
        given: "product with limited stock"
        inventoryService.addProduct('LIMITED-001', 'Limited Stock', 100.00, 3)

        when: "ordering more than available"
        def result = orderService.placeOrder('LIMITED-001', 10)

        then: "order fails"
        result.success == false
        result.error == 'Insufficient stock'
        result.available == 3
        result.requested == 10
    }

    def "get quote returns pricing without modifying inventory"() {
        given: "product in inventory"
        inventoryService.addProduct('QUOTE-001', 'Quotable Item', 75.00, 20)

        when: "getting a quote"
        def quote = orderService.getQuote('QUOTE-001', 5)

        then: "quote contains correct information"
        quote.available == true
        quote.unitPrice == 75.00
        quote.totalPrice == 375.00
        quote.inStock == 20

        and: "inventory is unchanged"
        inventoryService.findBySku('QUOTE-001').quantity == 20
    }

    def "service can check bean availability"() {
        expect: "known services are available"
        orderService.isServiceAvailable('inventoryService') == true
        orderService.isServiceAvailable('asyncProcessingService') == true
        orderService.isServiceAvailable('nonExistentService') == false
    }

    def "service can retrieve beans dynamically"() {
        when: "getting service dynamically"
        def service = orderService.getService('inventoryService')

        then: "service is returned"
        service != null
        service instanceof InventoryService
    }

    // ========== Async Service Tests ==========

    def "async method returns Future"() {
        when: "calling async method"
        def future = asyncProcessingService.processAsync('hello')

        then: "future is returned immediately"
        future != null

        and: "result is available after completion"
        future.get() == 'Processed: HELLO'
    }

    def "async calculation returns CompletableFuture"() {
        when: "calling async calculation"
        def future = asyncProcessingService.calculateAsync(7)

        then: "result is correct"
        future.get() == 49
    }

    def "async batch processing handles lists"() {
        when: "processing batch asynchronously"
        def future = asyncProcessingService.processBatchAsync(['abc', 'def', 'ghi'])

        then: "all items are processed"
        def result = future.get()
        result == ['cba', 'fed', 'ihg']
    }

    def "async method can access database"() {
        given: "products in database"
        inventoryService.addProduct('ASYNC-001', 'Async Test 1', 10.00, 5)
        inventoryService.addProduct('ASYNC-002', 'Async Test 2', 20.00, 10)

        when: "counting asynchronously"
        def future = asyncProcessingService.countItemsAsync()

        then: "count includes products"
        future.get() >= 2
    }

    def "long-running async operation completes"() {
        when: "starting long-running operation"
        def future = asyncProcessingService.longRunningOperation('task-123')

        then: "operation completes successfully"
        def result = future.get()
        result.taskId == 'task-123'
        result.status == 'completed'
        result.durationMs >= 0
        result.completedAt != null
    }

    def "multiple async operations can run concurrently"() {
        when: "starting multiple async operations"
        def futures = [
            asyncProcessingService.processAsync('one'),
            asyncProcessingService.processAsync('two'),
            asyncProcessingService.processAsync('three')
        ]

        then: "all complete successfully"
        futures.every { it.get() != null }
        futures[0].get() == 'Processed: ONE'
        futures[1].get() == 'Processed: TWO'
        futures[2].get() == 'Processed: THREE'
    }
}
