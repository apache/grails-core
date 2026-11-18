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
import org.grails.forge.fixture.CommandOutputFixture

class MongoDBTestingSupportSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test mongodb-testing-support dependency is present with gorm-mongodb"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-mongodb', 'mongodb-testing-support'])
                .render()

        then:
        template.contains('testImplementation "org.apache.grails.testing:grails-testing-support-mongodb"')
    }

    void "test mongodb-testing-support dependency is present with mongo-sync"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['mongo-sync', 'mongodb-testing-support'])
                .render()

        then:
        template.contains('testImplementation "org.apache.grails.testing:grails-testing-support-mongodb"')
    }

    void "test mongodb-testing-support is not added when only mongodb is selected"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-mongodb'])
                .render()

        then:
        !template.contains('org.apache.grails.testing:grails-testing-support-mongodb')
    }

    void "test mongodb-testing-support requires a mongodb feature"() {
        when:
        generate(['mongodb-testing-support'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('mongodb-testing-support requires mongo-sync or gorm-mongodb')
    }

    void "test readme.md with feature mongodb-testing-support contains docs link"() {
        when:
        def output = generate(['gorm-mongodb', 'mongodb-testing-support'])
        def readme = output['README.md']

        then:
        readme
        readme.contains('https://grails.apache.org/docs/latest/guide/testing.html#testingMongodb')
    }
}
