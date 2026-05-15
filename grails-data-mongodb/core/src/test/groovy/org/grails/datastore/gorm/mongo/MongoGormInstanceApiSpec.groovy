/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.datastore.gorm.mongo

import groovy.transform.CompileDynamic
import org.grails.datastore.gorm.mongo.api.MongoGormInstanceApi
import org.grails.datastore.gorm.mongo.transactions.MongoTransactionContext
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import spock.lang.Specification

/**
 * Specification for MongoGormInstanceApi
 *
 * @author Graeme Rocher
 * @since 8.0
 */
@CompileDynamic
class MongoGormInstanceApiSpec extends Specification {

    private MongoDatastore datastore

    void 'auto-flush gate defaults to enabled outside rollback-aware context'() {
        given:
        def api = newApi()

        expect:
        api.exposedShouldAutoFlushByDefault()
    }

    void 'auto-flush gate is disabled inside rollback-aware context only'() {
        given:
        def api = newApi()

        expect:
        api.exposedShouldAutoFlushByDefault()

        when:
        def insideGate = MongoTransactionContext.withRollbackAware {
            api.exposedShouldAutoFlushByDefault()
        }

        then:
        !insideGate
        api.exposedShouldAutoFlushByDefault()
    }

    void 'auto-flush gate handles nested rollback-aware contexts and restores state'() {
        given:
        def api = newApi()

        when:
        def outer = MongoTransactionContext.withRollbackAware {
            def inner = MongoTransactionContext.withRollbackAware {
                api.exposedShouldAutoFlushByDefault()
            }
            [api.exposedShouldAutoFlushByDefault(), inner]
        }

        then:
        !outer[0]
        !outer[1]
        api.exposedShouldAutoFlushByDefault()
    }

    private TestableMongoGormInstanceApi newApi() {
        datastore = new MongoDatastore(new MongoMappingContext('GateEntity'))
        new TestableMongoGormInstanceApi(datastore)
    }

    void cleanup() {
        datastore?.close()
    }

    @CompileDynamic
    private static class TestableMongoGormInstanceApi extends MongoGormInstanceApi<Object> {

        TestableMongoGormInstanceApi(MongoDatastore datastore) {
            super(Object, datastore)
        }

        boolean exposedShouldAutoFlushByDefault() {
            shouldAutoFlushByDefault()
        }
    }
}
