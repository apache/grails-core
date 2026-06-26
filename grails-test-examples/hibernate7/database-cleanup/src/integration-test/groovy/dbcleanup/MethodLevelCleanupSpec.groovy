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
 * Integration test that verifies method-level {@link DatabaseCleanup} annotation
 * only triggers cleanup after annotated test methods.
 *
 * <p>This test uses {@code @Stepwise} to guarantee execution order. The annotation
 * is placed on individual methods rather than the class, so cleanup only occurs
 * after the annotated methods.</p>
 */
@Integration(applicationClass = Application)
@Stepwise
class MethodLevelCleanupSpec extends Specification {

    @DatabaseCleanup(['dataSource:h2'])
    @Transactional
    void "test 1 - insert data with @DatabaseCleanup and explicit type on method"() {
        when: 'data is created within the transaction'
        new Author(name: 'Isaac Asimov').save(flush: true, failOnError: true)

        then: 'the data exists in the database'
        Author.findByName('Isaac Asimov') != null
    }

    @Transactional
    void "test 2 - verify database was cleaned up after annotated test 1"() {
        expect: 'no data from the previous test exists because it had @DatabaseCleanup'
        Author.findByName('Isaac Asimov') == null

        when: 'new data is inserted without @DatabaseCleanup on this method'
        new Author(name: 'Arthur Clarke').save(flush: true, failOnError: true)

        then: 'the data exists'
        Author.findByName('Arthur Clarke') != null
    }

    @Transactional
    void "test 3 - verify data from non-annotated test 2 persists"() {
        expect: 'data from test 2 still exists because it had no @DatabaseCleanup'
        Author.findByName('Arthur Clarke') != null
    }

    @DatabaseCleanup(['dataSource:h2'])
    @Transactional
    void "test 4 - insert more data with @DatabaseCleanup and explicit type"() {
        when: 'data is created'
        new Author(name: 'Ray Bradbury').save(flush: true, failOnError: true)

        then: 'the data exists along with leftover from test 2'
        Author.findByName('Ray Bradbury') != null
        Author.findByName('Arthur Clarke') != null
    }

    @Transactional
    void "test 5 - verify cleanup ran after annotated test 4"() {
        expect: 'all data was cleaned up because test 4 had @DatabaseCleanup'
        Author.findByName('Ray Bradbury') == null
        Author.findByName('Arthur Clarke') == null
    }
}
