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
package org.grails.datastore.gorm.mongo.api

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * {@code MongoStaticApi} inherits {@code multiTenancyMode} and the persistent-entity lookup from
 * {@code GormStaticApi} rather than re-declaring them, so there is exactly one of each per API object.
 * The existing {@code MongoStaticApiMultiTenancySpec} (a real, Docker-backed
 * {@code AutoStartedMongoSpec}) covers the "real MongoDatastore" branch; this spec covers the
 * "not multi-tenant" fallback cheaply with a {@link SimpleMapDatastore}, avoiding the need to spin up a
 * real MongoDB instance just to prove a defaulting branch.
 */
class MongoStaticApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(MongoStaticApiSpecThing)

    void "MongoStaticApi over a datastore with no multi-tenancy reports mode NONE"() {
        when:
        def api = new MongoStaticApi<MongoStaticApiSpecThing>(MongoStaticApiSpecThing, datastore, [], datastore.transactionManager)

        then:
        api.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
    }

    void "MongoStaticApi resolves its persistent entity through the inherited accessor"() {
        when:
        def api = new MongoStaticApi<MongoStaticApiSpecThing>(MongoStaticApiSpecThing, datastore, [], datastore.transactionManager)

        then: "there is no shadowing field — base-class code and MongoStaticApi see the same entity"
        api.gormPersistentEntity != null
        api.gormPersistentEntity.javaClass == MongoStaticApiSpecThing
        !MongoStaticApi.declaredFields.any { it.name in ['persistentEntity', 'multiTenancyMode'] }
    }
}

@Entity
class MongoStaticApiSpecThing {
    String title
}
