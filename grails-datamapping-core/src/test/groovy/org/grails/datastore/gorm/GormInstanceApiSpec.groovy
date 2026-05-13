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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class GormInstanceApiSpec extends Specification {

    void "save validate false skips validation during persist and restores flag"() {
        given:
        Datastore datastore = mockDatastore()
        Session session = Mock(Session)
        List<TestValidateableEntity> cleared = []
        def testValidationApi = new GormValidationApi<TestValidateableEntity>(TestValidateableEntity.class, datastore)
        testValidationApi.metaClass.clearErrors = { Object entityArg ->
            Object obj = entityArg
            if (obj instanceof List) {
                obj = ((List) obj).get(0)
            }
            cleared << (TestValidateableEntity) obj
        }
        
        def registry = new GormRegistry() {
            @Override
            GormValidationApi getValidationApi(String className) {
                return testValidationApi
            }
        }
        
        def api = new TestGormInstanceApi(datastore, session, registry)
        def entity = new TestValidateableEntity()

        when:
        def result = api.save(entity, [validate: false, flush: true])

        then:
        1 * session.persist(_ as TestValidateableEntity) >> { Object[] args ->
            Object persisted = args[0]
            if (persisted instanceof List) {
                persisted = ((List) persisted).get(0)
            }
            assert ((TestValidateableEntity) persisted).shouldSkipValidation()
        }
        1 * session.flush()
        result.is(entity)
        cleared == [entity]
        !entity.shouldSkipValidation()
    }

    void "save validate false preserves preexisting skipValidation state"() {
        given:
        Datastore datastore = mockDatastore()
        Session session = Mock(Session)
        List<TestValidateableEntity> cleared = []
        def testValidationApi = new GormValidationApi<TestValidateableEntity>(TestValidateableEntity.class, datastore)
        testValidationApi.metaClass.clearErrors = { Object entityArg ->
            Object obj = entityArg
            if (obj instanceof List) {
                obj = ((List) obj).get(0)
            }
            cleared << (TestValidateableEntity) obj
        }
        
        def registry = new GormRegistry() {
            @Override
            GormValidationApi getValidationApi(String className) {
                return testValidationApi
            }
        }
        
        def api = new TestGormInstanceApi(datastore, session, registry)
        def entity = new TestValidateableEntity()
        entity.skipValidation(true)

        when:
        def result = api.save(entity, [validate: false])

        then:
        1 * session.persist(_ as TestValidateableEntity) >> { Object[] args ->
            Object persisted = args[0]
            if (persisted instanceof List) {
                persisted = ((List) persisted).get(0)
            }
            assert ((TestValidateableEntity) persisted).shouldSkipValidation()
        }
        0 * session.flush()
        result.is(entity)
        cleared == [entity]
        entity.shouldSkipValidation()
    }

    private Datastore mockDatastore() {
        Mock(Datastore) {
            getMappingContext() >> Mock(MappingContext) {
                getMappingFactory() >> null
            }
        }
    }

    private static class TestGormInstanceApi extends GormInstanceApi<TestValidateableEntity> {
        private final Session session

        TestGormInstanceApi(Datastore datastore, Session session) {
            super(TestValidateableEntity.class, datastore)
            this.session = session
        }

        TestGormInstanceApi(Datastore datastore, Session session, GormRegistry registry) {
            super(TestValidateableEntity.class, datastore, registry)
            this.session = session
        }

        @Override
        protected <T> T execute(SessionCallback<T> callback) {
            return callback.call(session)
        }
    }

    private static class TestValidateableEntity implements GormValidateable {
    }
}
