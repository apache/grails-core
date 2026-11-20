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

package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.Specification

@Integration(applicationClass = Application)
@Rollback
@Issue('https://github.com/apache/grails-core/issues/14334')
class ExistsSpec extends Specification {

    void "exists returns true for persisted entity"() {
        given:
        Product p = new Product(isbn: '9780451524935').save(flush: true)

        expect:
        Product.exists(p.id)
    }

    void "exists returns false for non-existent id"() {
        expect:
        !Product.exists(99999)
    }

    void "exists returns correct result with multiple rows in table"() {
        given:
        new Product(isbn: '1000000000001').save(flush: true)
        new Product(isbn: '1000000000002').save(flush: true)
        new Product(isbn: '1000000000003').save(flush: true)
        Product target = new Product(isbn: '1000000000004').save(flush: true)
        new Product(isbn: '1000000000005').save(flush: true)

        expect:
        Product.exists(target.id)
        !Product.exists(99999)
    }
}
