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
package org.grails.orm.hibernate

import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class HibernateGormApiFactorySpec extends Specification {

    void 'factory creates hibernate static and instance APIs'() {
        given:
        HibernateGormApiFactory factory = new HibernateGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        def staticApi = factory.createStaticApi(TestEntity, mappingContext, resolver, 'default', GormRegistry.instance)
        def instanceApi = factory.createInstanceApi(TestEntity, mappingContext, resolver, GormRegistry.instance, true, false)

        then:
        staticApi instanceof HibernateGormStaticApi
        instanceApi instanceof HibernateGormInstanceApi
    }

    static class TestEntity {
    }
}
