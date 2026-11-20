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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import groovy.transform.CompileStatic

/**
 * Service demonstrating service-to-service dependency injection.
 * Tests autowiring patterns and service collaboration.
 */
@CompileStatic
class OrderService {

    @Autowired
    InventoryService inventoryService

    @Autowired
    ApplicationContext applicationContext

    /**
     * Place an order - uses inventory service to check/update stock
     */
    Map<String, Object> placeOrder(String sku, Integer quantity) {
        def item = inventoryService.findBySku(sku)
        
        if (!item) {
            return [success: false, error: 'Product not found', sku: sku]
        }
        
        if (item.quantity < quantity) {
            return [
                success: false, 
                error: 'Insufficient stock', 
                available: item.quantity,
                requested: quantity
            ]
        }
        
        // Update inventory
        def newQuantity = item.quantity - quantity
        inventoryService.updateQuantity(sku, newQuantity)
        
        def totalPrice = item.price * quantity
        
        return [
            success: true,
            sku: sku,
            quantity: quantity,
            unitPrice: item.price,
            totalPrice: totalPrice,
            remainingStock: newQuantity
        ]
    }

    /**
     * Get order quote without modifying inventory
     */
    Map<String, Object> getQuote(String sku, Integer quantity) {
        def item = inventoryService.findBySku(sku)
        
        if (!item) {
            return [available: false, sku: sku]
        }
        
        return [
            available: item.quantity >= quantity,
            sku: sku,
            name: item.name,
            unitPrice: item.price,
            quantity: quantity,
            totalPrice: item.price * quantity,
            inStock: item.quantity
        ]
    }

    /**
     * Check if a service bean exists in context
     */
    boolean isServiceAvailable(String serviceName) {
        applicationContext.containsBean(serviceName)
    }

    /**
     * Get service bean dynamically
     */
    Object getService(String serviceName) {
        if (applicationContext.containsBean(serviceName)) {
            return applicationContext.getBean(serviceName)
        }
        return null
    }

    /**
     * Get inventory value using injected service
     */
    BigDecimal getInventoryValue() {
        inventoryService.getTotalInventoryValue()
    }
}
