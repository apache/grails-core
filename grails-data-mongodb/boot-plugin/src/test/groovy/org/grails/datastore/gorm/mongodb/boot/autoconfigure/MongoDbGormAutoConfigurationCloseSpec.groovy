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
package org.grails.datastore.gorm.mongodb.boot.autoconfigure

import com.mongodb.client.MongoClient
import spock.lang.Specification

class MongoDbGormAutoConfigurationCloseSpec extends Specification {

    void 'a MongoClient created by the auto-configuration is closed on shutdown'() {
        given: 'an auto-configuration that created its own client'
        def mongoClient = Mock(MongoClient)
        def configuration = new MongoDbGormAutoConfiguration()
        configuration.mongo = mongoClient
        configuration.@mongoClientCreatedInternally = true

        when: 'the context is shut down'
        configuration.destroy()

        then: 'the client it created is closed'
        1 * mongoClient.close()
    }

    void 'an externally supplied MongoClient bean is not closed on shutdown'() {
        given: 'an auto-configuration handed an existing client bean'
        def mongoClient = Mock(MongoClient)
        def configuration = new MongoDbGormAutoConfiguration()
        configuration.mongo = mongoClient

        when: 'the context is shut down'
        configuration.destroy()

        then: 'the externally owned client is left open'
        0 * mongoClient.close()
    }
}
