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

import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class DefaultGormApiFactorySpec extends Specification {

    void 'createInstanceApi applies failOnError and markDirty configuration'() {
        given:
        DefaultGormApiFactory factory = new DefaultGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        GormInstanceApi<TestFactoryEntity> instanceApi = factory.createInstanceApi(
            TestFactoryEntity,
            mappingContext,
            resolver,
            GormRegistry.instance,
            true,
            false
        )

        then:
        instanceApi != null
        instanceApi.failOnError
        !instanceApi.markDirty
    }

    void 'createDynamicFinders returns default finder set'() {
        given:
        DefaultGormApiFactory factory = new DefaultGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        def finders = factory.createDynamicFinders(resolver, mappingContext)

        then:
        finders.size() == 8
    }

    static class TestFactoryEntity {
    }
}
