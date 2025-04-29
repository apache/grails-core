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
package grails.mongodb.bootstrap

import grails.gorm.tests.Plant
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 16/12/16.
 */
class FailOnErrorSetupSpec extends AutoStartedMongoSpec {

    @AutoCleanup
    @Shared
    MongoDatastore mongoDatastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        mongoDatastore = new MongoDatastore(['grails.mongodb.host':mongoHost, 'grails.mongodb.port': mongoPort, (Settings.SETTING_FAIL_ON_ERROR):true], Plant)
    }

    void "test fail on error was configured correctly"() {

        when:
        def plant = new Plant()
        plant.save()

        then:
        plant.errors.hasErrors()
        thrown grails.validation.ValidationException
    }

}
