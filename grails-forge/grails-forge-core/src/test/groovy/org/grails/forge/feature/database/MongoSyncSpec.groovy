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

package org.grails.forge.feature.database

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class MongoSyncSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test readme.md with feature mongo-sync contains links to micronaut and 3rd party docs'() {
        when:
        def output = generate(['mongo-sync'])
        def readme = output["README.md"]

        then:
        readme
        readme.contains("https://docs.mongodb.com/drivers/java/sync/current/")
        readme.contains("https://docs.mongodb.com")
    }

    void "test mongo sync features"() {
        when:
        Features features = getFeatures(['mongo-sync'])

        then:
        features.contains("mongo-sync")
    }

    void "test mongo sync dependencies are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["mongo-sync"])
                .render()

        then:
        template.contains('implementation "org.mongodb:mongodb-driver-sync"')
        template.contains('testImplementation "org.testcontainers:mongodb"')
    }
}
