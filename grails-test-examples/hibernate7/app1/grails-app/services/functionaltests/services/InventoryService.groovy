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

import grails.gorm.transactions.Transactional
import grails.gorm.transactions.ReadOnly

/**
 * Service demonstrating transactional behavior with GORM.
 * Tests various @Transactional options and rollback scenarios.
 */
class InventoryService {

    /**
     * Default transactional method - rolls back on RuntimeException
     */
    @Transactional
    void addProduct(String sku, String name, BigDecimal price, Integer quantity) {
        def product = new InventoryItem(
            sku: sku,
            name: name,
            price: price,
            quantity: quantity
        )
        product.save(failOnError: true)
    }

    /**
     * Read-only transaction - optimized for queries
     */
    @ReadOnly
    InventoryItem findBySku(String sku) {
        InventoryItem.findBySku(sku)
    }

    /**
     * Read-only transaction returning list
     */
    @ReadOnly
    List<InventoryItem> findAllByPriceGreaterThan(BigDecimal minPrice) {
        InventoryItem.findAllByPriceGreaterThan(minPrice)
    }

    /**
     * Transactional method that throws exception to test rollback
     */
    @Transactional
    void addProductWithFailure(String sku, String name, BigDecimal price, Integer quantity) {
        def product = new InventoryItem(
            sku: sku,
            name: name,
            price: price,
            quantity: quantity
        )
        product.save(failOnError: true, flush: true)
        
        // Simulate failure after save
        throw new RuntimeException("Simulated failure - should rollback")
    }

    /**
     * Transactional method with checked exception - does NOT rollback by default
     */
    @Transactional
    void addProductWithCheckedException(String sku, String name, BigDecimal price, Integer quantity) throws Exception {
        def product = new InventoryItem(
            sku: sku,
            name: name,
            price: price,
            quantity: quantity
        )
        product.save(failOnError: true, flush: true)
        
        // Checked exception - transaction commits by default
        throw new Exception("Checked exception - should NOT rollback by default")
    }

    /**
     * Transactional method configured to rollback on checked exception
     */
    @Transactional(rollbackFor = Exception)
    void addProductRollbackOnCheckedException(String sku, String name, BigDecimal price, Integer quantity) throws Exception {
        def product = new InventoryItem(
            sku: sku,
            name: name,
            price: price,
            quantity: quantity
        )
        product.save(failOnError: true, flush: true)
        
        throw new Exception("Checked exception - configured to rollback")
    }

    /**
     * Update quantity - demonstrates update in transaction
     */
    @Transactional
    boolean updateQuantity(String sku, Integer newQuantity) {
        def product = InventoryItem.findBySku(sku)
        if (product) {
            product.quantity = newQuantity
            product.save(failOnError: true)
            return true
        }
        return false
    }

    /**
     * Transfer quantity between products - demonstrates multi-entity transaction
     */
    @Transactional
    void transferQuantity(String fromSku, String toSku, Integer amount) {
        def fromProduct = InventoryItem.findBySku(fromSku)
        def toProduct = InventoryItem.findBySku(toSku)
        
        if (!fromProduct || !toProduct) {
            throw new IllegalArgumentException("Products not found")
        }
        
        if (fromProduct.quantity < amount) {
            throw new IllegalStateException("Insufficient quantity")
        }
        
        fromProduct.quantity -= amount
        toProduct.quantity += amount
        
        fromProduct.save(failOnError: true)
        toProduct.save(failOnError: true)
    }

    /**
     * Batch insert - demonstrates bulk operations
     */
    @Transactional
    int batchInsert(List<Map> items) {
        int count = 0
        items.each { item ->
            def product = new InventoryItem(
                sku: item.sku as String,
                name: item.name as String,
                price: item.price as BigDecimal,
                quantity: item.quantity as Integer
            )
            product.save(failOnError: true)
            count++
            
            // Flush periodically for large batches
            if (count % 20 == 0) {
                InventoryItem.withSession { session ->
                    session.flush()
                    session.clear()
                }
            }
        }
        return count
    }

    /**
     * Delete product
     */
    @Transactional
    boolean deleteProduct(String sku) {
        def product = InventoryItem.findBySku(sku)
        if (product) {
            product.delete(flush: true)
            return true
        }
        return false
    }

    /**
     * Count all products - read only
     */
    @ReadOnly
    int countAll() {
        InventoryItem.count()
    }

    /**
     * Get total inventory value
     */
    @ReadOnly
    BigDecimal getTotalInventoryValue() {
        def items = InventoryItem.list()
        items.sum { InventoryItem it -> (it.price ?: 0) * (it.quantity ?: 0) } as BigDecimal ?: 0
    }
}
