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

package dbcleanup

import grails.gorm.transactions.Transactional
import grails.testing.mixin.integration.Integration

import spock.lang.Specification
import spock.lang.Stepwise

import org.apache.grails.testing.cleanup.core.DatabaseCleanup

/**
 * Integration test that verifies class-level {@link DatabaseCleanup} annotation
 * properly truncates all database tables after each test method.
 *
 * <p>This test uses {@code @Stepwise} to guarantee execution order, so we
 * can verify that data created in one test is removed before the next test runs.</p>
 */
@Integration(applicationClass = Application)
@DatabaseCleanup
@Stepwise
class ClassLevelCleanupSpec extends Specification {

    @Transactional
    void "test 1 - insert data and verify it was persisted"() {
        when: 'data is created within the transaction'
        new Author(name: 'Stephen King').save(flush: true, failOnError: true)
        new Author(name: 'Dean Koontz').save(flush: true, failOnError: true)

        then: 'the data exists in the database'
        Author.count() >= 2
        Author.findByName('Stephen King') != null
        Author.findByName('Dean Koontz') != null
    }

    @Transactional
    void "test 2 - verify database was cleaned up after test 1"() {
        expect: 'no Author records from the previous test exist (tables were truncated)'
        Author.findByName('Stephen King') == null
        Author.findByName('Dean Koontz') == null

        when: 'new data is inserted'
        new Author(name: 'J.K. Rowling').save(flush: true, failOnError: true)

        then: 'only the new data exists'
        Author.findByName('J.K. Rowling') != null
    }

    @Transactional
    void "test 3 - verify database was cleaned up after test 2"() {
        expect: 'no Author records from the previous test exist'
        Author.findByName('J.K. Rowling') == null
        Author.findByName('Stephen King') == null
        Author.findByName('Dean Koontz') == null
    }
}
