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

import org.grails.datastore.gorm.GormEntity

/**
 * Domain class mapped exclusively to the 'secondary' datasource.
 * Used to test that GORM Data Service auto-implemented CRUD methods
 * route correctly when @Transactional(connection) is specified.
 */
class Product implements GormEntity<Product> {

    String name
    Integer amount

    static mapping = {
        datasource 'secondary'
    }

    static constraints = {
        name blank: false
    }
}
