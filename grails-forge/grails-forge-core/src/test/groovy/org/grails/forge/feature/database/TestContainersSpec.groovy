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
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.TestFramework

import java.util.stream.Collectors

class TestContainersSpec extends ApplicationContextSpec {

    void "test mysql dependency is present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers', 'mysql'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:mysql"')
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "test postgres dependency is present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers', 'postgres'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:postgresql"')
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "test sqlserver dependency is present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers', 'sqlserver'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:mssqlserver"')
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "test mongo-sync dependency is present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers', 'mongo-sync'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:mongodb"')
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "test mongo-gorm dependency is present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers', 'gorm-mongodb'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:mongodb"')
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "test testcontainers core is present when no testcontainer modules are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['testcontainers'])
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:testcontainers"')
    }

    void "testframework dependency is present for gradle for feature #feature and spock framework"() {
        when:
        def template = new BuildBuilder(beanContext)
                .features([feature])
                .testFramework(TestFramework.SPOCK)
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:spock"')

        where:
        feature << ["mongo-sync", "mysql", "postgres", "sqlserver"]
    }

    void "testframework dependency is present for gradle for feature #feature and junit framework"() {

        when:
        def template = new BuildBuilder(beanContext)
                .features([feature])
                .testFramework(TestFramework.JUNIT)
                .render()

        then:
        template.contains('testImplementation "org.testcontainers:junit-jupiter"')

        where:
        feature << ["mongo-sync", "mysql", "postgres", "sqlserver"]
    }

    void "test there is a dependency for every non embedded driver feature"() {
        when:
        String gradleTemplate = new BuildBuilder(beanContext)
                .features(['testcontainers', driverFeature.getName()])
                .render()

        then:
        gradleTemplate.contains("org.testcontainers")

        where:
        driverFeature <<  beanContext.streamOfType(DatabaseDriverFeature)
                .filter({ f ->  !f.embedded() })
                .collect(Collectors.toList())
    }
}
