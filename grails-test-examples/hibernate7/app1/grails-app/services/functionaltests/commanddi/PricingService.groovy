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

package functionaltests.commanddi

/**
 * Service for pricing calculations that can be injected into command objects.
 */
class PricingService {

    /**
     * Calculate discount based on quantity.
     */
    BigDecimal calculateDiscount(Integer quantity) {
        if (!quantity || quantity <= 0) return 0.0
        if (quantity >= 100) return 0.25  // 25% discount
        if (quantity >= 50) return 0.15   // 15% discount
        if (quantity >= 10) return 0.10   // 10% discount
        return 0.0
    }

    /**
     * Calculate total price with discount.
     */
    BigDecimal calculateTotalPrice(BigDecimal unitPrice, Integer quantity) {
        if (!unitPrice || !quantity) return 0.0
        def subtotal = unitPrice * quantity
        def discount = calculateDiscount(quantity)
        return subtotal * (1 - discount)
    }

    /**
     * Validate if price is within acceptable range.
     */
    boolean isPriceValid(BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice) {
        if (!price) return false
        return price >= (minPrice ?: 0) && price <= (maxPrice ?: BigDecimal.valueOf(Long.MAX_VALUE))
    }

    /**
     * Get current tax rate.
     */
    BigDecimal getTaxRate() {
        return 0.08  // 8% tax
    }

    /**
     * Calculate price with tax.
     */
    BigDecimal calculatePriceWithTax(BigDecimal price) {
        if (!price) return 0.0
        return price * (1 + getTaxRate())
    }

    /**
     * Get service identifier for testing.
     */
    String getServiceId() {
        return 'PricingService-v1'
    }
}
