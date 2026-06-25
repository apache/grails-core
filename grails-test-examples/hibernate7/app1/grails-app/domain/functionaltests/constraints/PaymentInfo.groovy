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

package functionaltests.constraints

import java.math.RoundingMode

/**
 * Domain class demonstrating financial constraints:
 * - Credit card validation
 * - Currency amounts
 * - Percentage validation
 * - Complex business rules
 */
class PaymentInfo {

    String cardType
    String cardNumber
    String cardholderName
    Integer expiryMonth
    Integer expiryYear
    String cvv
    BigDecimal amount
    String currency
    BigDecimal taxRate
    BigDecimal taxAmount
    BigDecimal totalAmount
    String billingAddress
    String billingZip
    boolean isRecurring
    Integer recurringIntervalDays

    Date dateCreated
    Date lastUpdated

    static constraints = {
        cardType inList: ['Visa', 'MasterCard', 'Amex', 'Discover']

        // Card number validation (Luhn algorithm check would be in validator)
        cardNumber blank: false, validator: { val, obj ->
            if (!val) return true
            // Remove spaces and dashes
            def cleaned = val.replaceAll(/[\s-]/, '')
            
            // Length validation based on card type
            if (obj.cardType == 'Amex') {
                if (cleaned.length() != 15) {
                    return 'paymentInfo.cardNumber.invalidAmexLength'
                }
                if (!cleaned.startsWith('34') && !cleaned.startsWith('37')) {
                    return 'paymentInfo.cardNumber.invalidAmexPrefix'
                }
            } else {
                if (cleaned.length() != 16) {
                    return 'paymentInfo.cardNumber.invalidLength'
                }
            }
            
            // Basic Luhn check
            if (!luhnCheck(cleaned)) {
                return 'paymentInfo.cardNumber.invalidChecksum'
            }
        }

        cardholderName blank: false, size: 2..100, matches: /^[A-Za-z\s\-']+$/

        expiryMonth range: 1..12

        // Expiry year validation
        expiryYear validator: { val, obj ->
            if (val == null) return true
            def currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (val < currentYear) {
                return 'paymentInfo.expiryYear.expired'
            }
            if (val > currentYear + 20) {
                return 'paymentInfo.expiryYear.tooFarFuture'
            }
            // Check if card is expired (month + year)
            if (val == currentYear && obj.expiryMonth) {
                def currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                if (obj.expiryMonth < currentMonth) {
                    return 'paymentInfo.expiryYear.cardExpired'
                }
            }
        }

        // CVV validation based on card type
        cvv blank: false, validator: { val, obj ->
            if (!val) return true
            def expectedLength = obj.cardType == 'Amex' ? 4 : 3
            if (val.length() != expectedLength) {
                return obj.cardType == 'Amex' ? 
                    'paymentInfo.cvv.invalidAmexLength' : 
                    'paymentInfo.cvv.invalidLength'
            }
            if (!(val =~ /^\d+$/)) {
                return 'paymentInfo.cvv.mustBeNumeric'
            }
        }

        amount min: 0.01, max: 100000.00, scale: 2

        currency inList: ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY']

        // Tax rate percentage validation
        taxRate nullable: true, min: 0.0, max: 30.0, scale: 4

        // Tax amount must match calculation
        taxAmount nullable: true, scale: 2, validator: { val, obj ->
            if (val == null || obj.amount == null || obj.taxRate == null) return true
            def expectedTax = (obj.amount * obj.taxRate / 100).setScale(2, RoundingMode.HALF_UP)
            if (val.setScale(2, RoundingMode.HALF_UP) != expectedTax) {
                return 'paymentInfo.taxAmount.doesNotMatchCalculation'
            }
        }

        // Total must equal amount + tax
        totalAmount scale: 2, validator: { val, obj ->
            if (val == null || obj.amount == null) return true
            def expectedTotal = obj.amount + (obj.taxAmount ?: 0) as BigDecimal
            if (val.setScale(2, RoundingMode.HALF_UP) != expectedTotal.setScale(2, RoundingMode.HALF_UP)) {
                return 'paymentInfo.totalAmount.doesNotMatchSum'
            }
        }

        billingAddress blank: false, size: 10..200

        // Zip code validation based on implicit US
        billingZip blank: false, matches: /^\d{5}(-\d{4})?$/

        // Recurring interval required only if isRecurring is true
        recurringIntervalDays nullable: true, min: 1, max: 365, validator: { val, obj ->
            if (obj.isRecurring && val == null) {
                return 'paymentInfo.recurringIntervalDays.required'
            }
            if (!obj.isRecurring && val != null) {
                return 'paymentInfo.recurringIntervalDays.notAllowed'
            }
        }
    }

    /**
     * Luhn algorithm check for credit card validation
     */
    static boolean luhnCheck(String number) {
        int sum = 0
        boolean alternate = false
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1))
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = (n % 10) + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }
}
