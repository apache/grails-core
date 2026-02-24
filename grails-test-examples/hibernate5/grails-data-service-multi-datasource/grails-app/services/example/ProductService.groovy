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

import grails.gorm.services.Query
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

/**
 * GORM Data Service for the Product domain, routed to the 'secondary'
 * datasource via @Transactional(connection).
 *
 * All auto-implemented methods (save, get, delete, findByName, count)
 * should route through the connection-aware GormEnhancer APIs rather
 * than falling through to the default datasource.
 */
@Service(Product)
@Transactional(connection = 'secondary')
abstract class ProductService {

    abstract Product get(Serializable id)

    abstract Product save(Product product)

    abstract Product delete(Serializable id)

    abstract Number count()

    abstract Product findByName(String name)

    abstract List<Product> findAllByName(String name)

    @Query("from ${Product p} where $p.name = $name")
    abstract Product findOneByQuery(String name)

    @Query("from ${Product p} where $p.amount >= $minAmount")
    abstract List<Product> findAllByQuery(Integer minAmount)

    @Query("update ${Product p} set $p.amount = $newAmount where $p.name = $name")
    abstract Number updateAmountByName(String name, Integer newAmount)
}
