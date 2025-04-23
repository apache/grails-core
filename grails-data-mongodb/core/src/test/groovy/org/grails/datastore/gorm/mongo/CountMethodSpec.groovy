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

package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import static com.mongodb.client.model.Filters.*

/**
 * Created by graemerocher on 29/11/2016.
 */
class CountMethodSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore mongoDatastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        mongoDatastore = new MongoDatastore([
                (MongoSettings.SETTING_HOST): mongoHost,
                (MongoSettings.SETTING_PORT): mongoPort,
        ], CountTest)
    }

    void "test count method"() {
        given: "some test data "
        CountTest.DB.drop()
        CountTest.withNewSession {
            new CountTest(name: "foo").save()
            new CountTest(name: "bar").save(flush: true)
        }

        expect:
        CountTest.find(eq("name", "foo"))
        CountTest.count(eq("name", "foo")) == 1
    }
}

@Entity
class CountTest implements MongoEntity<CountTest> {
    String name

}
