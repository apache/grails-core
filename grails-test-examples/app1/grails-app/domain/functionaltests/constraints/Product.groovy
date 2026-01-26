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

/**
 * Domain class demonstrating various GORM constraints:
 * - nullable, blank, size, min, max, range
 * - unique, email, url, matches
 * - inList, notEqual, scale
 * - custom validator closures
 */
class Product {

    String sku              // unique product identifier
    String name             // product name
    String description      // optional description
    String category         // must be from a list
    BigDecimal price        // min/max validation
    Integer stockQuantity   // range validation
    String email            // email format
    String website          // url format
    String productCode      // matches pattern
    BigDecimal discount     // scale validation

    Date dateCreated
    Date lastUpdated

    static constraints = {
        // unique constraint - SKU must be unique
        sku unique: true, blank: false, size: 5..20

        // basic string constraints
        name blank: false, size: 2..100

        // nullable constraint
        description nullable: true, maxSize: 500

        // inList constraint
        category inList: ['Electronics', 'Books', 'Clothing', 'Food', 'Toys']

        // min/max constraints
        price min: 0.01, max: 999999.99

        // range constraint
        stockQuantity range: 0..10000

        // email constraint
        email email: true, nullable: true

        // url constraint
        website url: true, nullable: true

        // matches constraint (regex pattern)
        productCode matches: /^[A-Z]{3}-[0-9]{4}$/, nullable: true

        // scale constraint for decimal places
        discount scale: 2, nullable: true, min: 0.0, max: 100.0
    }
}
