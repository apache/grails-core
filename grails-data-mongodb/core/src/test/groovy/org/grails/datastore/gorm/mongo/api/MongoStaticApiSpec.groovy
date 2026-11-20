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
 * The diff added two new constructor-time field assignments: {@code persistentEntity} (resolved
 * eagerly from the datastore's mapping context) and {@code multiTenancyMode} (read from the
 * datastore when it's a real {@code MongoDatastore}, else defaulting to {@code NONE}). The
 * existing {@code MongoStaticApiMultiTenancySpec} (a real, Docker-backed
 * {@code AutoStartedMongoSpec}) already covers the "real MongoDatastore" branch; this spec covers
 * the "not a MongoDatastore" fallback cheaply with a {@link SimpleMapDatastore}, avoiding the need
 * to spin up a real MongoDB instance just to prove a defaulting branch.
 */
class MongoStaticApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(MongoStaticApiSpecThing)

    void "constructing MongoStaticApi with a non-MongoDatastore defaults multiTenancyMode to NONE"() {
        when:
        def api = new MongoStaticApi<MongoStaticApiSpecThing>(MongoStaticApiSpecThing, datastore, [], datastore.transactionManager)

        then:
        api.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        api.persistentEntity != null
        api.persistentEntity.javaClass == MongoStaticApiSpecThing
    }
}

@Entity
class MongoStaticApiSpecThing {
    String title
}
