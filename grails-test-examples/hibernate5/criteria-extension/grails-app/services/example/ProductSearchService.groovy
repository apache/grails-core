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

package example

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.ReadOnly

/**
 * Demonstrates the two Groovy extension modules via {@link grails.gorm.DetachedCriteria}.
 *
 * <ul>
 *   <li>{@code CriteriaBuilderExtensions.eqIf}: conditional equality added to
 *       {@link grails.gorm.CriteriaBuilder}. Called inside {@code DetachedCriteria.build}.</li>
 *   <li>{@code HibernateCriteriaBuilderExtensions.numberLike}: number-to-string LIKE added to
 *       {@link org.grails.orm.hibernate.query.AbstractHibernateCriteriaBuilder}.
 *       Called inside a detached criteria builder closure.</li>
 * </ul>
 */
class ProductSearchService {

    @ReadOnly
    List<Product> findByName(String name) {
        new DetachedCriteria(Product).build {
            eqIf 'name', name
        }.list() as List<Product>
    }

    @ReadOnly
    List<Product> findByNameUntrimmed(String name) {
        new DetachedCriteria(Product).build {
            eqIf 'name', name, false
        }.list() as List<Product>
    }

    /**
     * Uses an explicit detached criteria with {@code numberLike} to match products
     * by a price string pattern (e.g. {@code '4%'} matches 4.99 and 49.99).
     *
     * On H2 falls back to {@code cast(col as varchar) like ?}; on Oracle/PostgreSQL uses
     * {@code trim(to_char(trunc(...))) like ?}.
     */
    List<Product> findByPriceLike(String pricePattern) {
        new DetachedCriteria(Product).build {
            numberLike 'price', pricePattern
        }.list() as List<Product>
    }
}
