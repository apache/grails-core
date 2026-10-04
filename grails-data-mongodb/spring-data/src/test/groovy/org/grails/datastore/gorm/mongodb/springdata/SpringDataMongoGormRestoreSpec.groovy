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
package org.grails.datastore.gorm.mongodb.springdata

import java.util.function.Supplier

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * A datastore stopped for a CRaC checkpoint closes its client and builds a replacement when it is started again.
 * The Spring Data beans wired on top of it have to follow it to the replacement rather than keep using the client
 * the checkpoint closed.
 */
class SpringDataMongoGormRestoreSpec extends EmbeddedReplicaSetSpec {

    void "test the auto-configured MongoTemplate writes through the replacement client after a restore"() {
        given:
        MongoDatastore datastore = new MongoDatastore(['grails.mongodb.url': mongoUrl])

        expect:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringDataMongoGormAutoConfiguration))
                .withBean(MongoDatastore, { datastore } as Supplier)
                .run { context ->
                    MongoTemplate template = context.getBean(MongoTemplate)
                    template.dropCollection(RestoredSpringDataThing)

                    datastore.stop()
                    datastore.start()

                    template.insert(new RestoredSpringDataThing(name: 'restored'))
                    assert template.count(new Query(), RestoredSpringDataThing) == 1
                }

        cleanup:
        datastore.close()
    }
}

class RestoredSpringDataThing {
    String id
    String name
}
