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

package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GrailsBaseSpec extends ApplicationContextSpec implements CommandOutputFixture {

    private static final List<String> GRAILS_BOMS = [
            'grails-bom',
            'grails-hibernate7-bom',
            'grails-neo4j-bom',
            'grails-micronaut-bom',
            'grails-hibernate7-micronaut-bom',
    ]

    void "an application with #description imports #bom as its one Grails BOM"() {
        given:
        Options options = new Options(DevelopmentReloading.DEFAULT_OPTION, gormImpl, ServletImpl.DEFAULT_OPTION, jdkVersion)

        when:
        String buildGradle = generate(ApplicationType.WEB, options, features)['build.gradle']
        String buildSrc = generate(ApplicationType.WEB, options, features + ['gradle-build-src'])['buildSrc/build.gradle']

        then: 'the dependencies import the BOM, as an enforced platform with Micronaut'
        buildGradle.contains("implementation ${enforced ? 'enforcedPlatform' : 'platform'}(\"org.apache.grails:${bom}:\$grailsVersion\")")

        and: 'the buildscript classpath and buildSrc import the same BOM'
        buildGradle.contains("classpath platform(\"org.apache.grails:${bom}:\$grailsVersion\")")
        buildSrc.contains("implementation platform(\"org.apache.grails:${bom}:\$grailsVersion\")")

        and: 'no other Grails BOM is imported anywhere'
        (GRAILS_BOMS - bom).every { String other ->
            !buildGradle.contains("org.apache.grails:${other}:") && !buildSrc.contains("org.apache.grails:${other}:")
        }

        where:
        description                 | gormImpl            | features             | jdkVersion        || bom                              | enforced
        'Hibernate 5'               | GormImpl.HIBERNATE5 | []                   | JdkVersion.JDK_21 || 'grails-bom'                     | false
        'Hibernate 7'               | GormImpl.HIBERNATE7 | []                   | JdkVersion.JDK_21 || 'grails-hibernate7-bom'          | false
        'MongoDB'                   | GormImpl.MONGODB    | []                   | JdkVersion.JDK_21 || 'grails-bom'                     | false
        'Neo4j'                     | GormImpl.NEO4J      | []                   | JdkVersion.JDK_21 || 'grails-neo4j-bom'               | false
        'Neo4j and Hibernate 5'     | GormImpl.HIBERNATE5 | ['gorm-neo4j']       | JdkVersion.JDK_21 || 'grails-neo4j-bom'               | false
        'Micronaut and Hibernate 5' | GormImpl.HIBERNATE5 | ['grails-micronaut'] | JdkVersion.JDK_25 || 'grails-micronaut-bom'           | true
        'Micronaut and Hibernate 7' | GormImpl.HIBERNATE7 | ['grails-micronaut'] | JdkVersion.JDK_25 || 'grails-hibernate7-micronaut-bom' | true
        'Micronaut and MongoDB'     | GormImpl.MONGODB    | ['grails-micronaut'] | JdkVersion.JDK_25 || 'grails-micronaut-bom'           | true
    }

    void "test grails base dependencies"() {

        when:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        def buildGradle = output['build.gradle']

        then:
        buildGradle.contains("implementation \"org.apache.grails:grails-core\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-web-boot\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-logging\"")
    }

    void "test src/main directories are present"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))

        expect:
        output.containsKey("src/main/groovy/.gitkeep")
        output.containsKey("src/test/groovy/.gitkeep")
        output.containsKey("src/integration-test/groovy/.gitkeep")
    }

    @Unroll
    void "test domain, services and taglib directories are present for ApplicationTypes other than REST_API and REST_API_PLUGIN"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:

        output.containsKey("grails-app/services/.gitkeep")
        output.containsKey("grails-app/domain/.gitkeep")
        output.containsKey("grails-app/taglib/.gitkeep")

        where:
        applicationType << ApplicationType.values() - ApplicationType.REST_API - ApplicationType.REST_API_PLUGIN
    }

    @Unroll
    void "test domain and services directories are present for #applicationType, which has no tag libraries"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:

        output.containsKey("grails-app/services/.gitkeep")
        output.containsKey("grails-app/domain/.gitkeep")
        !output.containsKey("grails-app/taglib/.gitkeep")

        where:
        applicationType << [ApplicationType.REST_API, ApplicationType.REST_API_PLUGIN]
    }
}
