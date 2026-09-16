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
package grails.gorm.rx

import org.grails.datastore.mapping.config.Entity as MappedEntity
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormStaticApi
import rx.Observable
import spock.lang.Specification

class RxEntitySpec extends Specification {

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "exists() emits true when get() resolves a matching instance"() {
        given: "the static API for the entity resolves an instance for the given id"
        registerStaticApi(Stub(RxGormStaticApi) {
            get(_, _) >> Observable.just(new ExistsFixture())
        })

        expect:
        ExistsFixture.exists(1L).toBlocking().first()
    }

    void "exists() emits false when get() resolves nothing"() {
        given: "the static API for the entity resolves no result for the given id"
        registerStaticApi(Stub(RxGormStaticApi) {
            get(_, _) >> Observable.empty()
        })

        expect:
        !ExistsFixture.exists(1L).toBlocking().first()
    }

    /**
     * Registers the given static API as the RxGORM static API for {@link ExistsFixture},
     * mirroring how a real {@code RxDatastoreClient} implementation bootstraps RxGORM entities.
     */
    private void registerStaticApi(RxGormStaticApi staticApi) {
        def mappedForm = new MappedEntity()
        mappedForm.datasources = [ConnectionSource.DEFAULT]
        def classMapping = Stub(ClassMapping) {
            getMappedForm() >> mappedForm
        }
        def persistentEntity = Stub(PersistentEntity) {
            getJavaClass() >> ExistsFixture
            getName() >> ExistsFixture.name
            getMapping() >> classMapping
        }
        def client = Stub(RxDatastoreClientImplementor) {
            createStaticApi(_, _) >> staticApi
        }
        RxGormEnhancer.registerEntity(persistentEntity, client)
    }
}

class ExistsFixture implements RxEntity<ExistsFixture> {
    Long id
}
