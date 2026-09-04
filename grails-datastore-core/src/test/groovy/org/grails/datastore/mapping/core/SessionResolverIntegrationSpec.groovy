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

package org.grails.datastore.mapping.core

import org.springframework.core.env.PropertyResolver

import spock.lang.Specification

import org.grails.datastore.mapping.model.MappingContext

class SessionResolverIntegrationSpec extends Specification {

    void "test session resolution through datastore"() {
        given:
        def datastore = new TestDatastore(Mock(MappingContext))
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> true

        def resolver = datastore.getSessionResolver()

        when:
        resolver.bind(session)

        then:
        resolver.resolve() == session

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
    }

    static class TestDatastore extends AbstractDatastore {
        TestDatastore(MappingContext mappingContext) {
            super(mappingContext)
        }

        @Override
        protected Session createSession(PropertyResolver connectionDetails) {
            return null
        }
    }
}
