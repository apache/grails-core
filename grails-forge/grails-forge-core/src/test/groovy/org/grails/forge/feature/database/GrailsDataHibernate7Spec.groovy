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

import groovy.yaml.YamlSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

class GrailsDataHibernate7Spec extends ApplicationContextSpec implements CommandOutputFixture {

    private static Options hibernate7Options() {
        new Options(DevelopmentReloading.DEVTOOLS, GormImpl.HIBERNATE7, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)
    }

    void "test hibernate 7 gorm features"() {
        when:
        Features features = getFeatures(['gorm-hibernate7'])

        then:
        features.contains("h2")
        features.contains("gorm-hibernate7")
        !features.contains("gorm-hibernate5")
    }

    void "test a database driver feature does not add hibernate 5 when hibernate 7 is selected"() {
        when:
        Features features = getFeatures(['gorm-hibernate7', 'postgres'])

        then:
        features.contains("postgres")
        features.contains("gorm-hibernate7")
        !features.contains("gorm-hibernate5")
    }

    void "test a database driver feature dependencies with hibernate 7 for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7", "postgres"])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-data-hibernate7"')
        !template.contains('grails-data-hibernate5')
    }

    void "test dependencies are present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7"])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-data-hibernate7"')
        template.contains('runtimeOnly "com.zaxxer:HikariCP"')
        template.contains('runtimeOnly "com.h2database:h2"')
        !template.contains('grails-data-hibernate5')
    }

    void "test hibernate 7 bom is used for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7"])
                .render()

        then:
        template.contains('org.apache.grails:grails-hibernate7-bom')
        !template.contains('org.apache.grails:grails-bom')
    }

    void "test hibernate 7 micronaut bom is used when micronaut is added"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7", "grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_25)
                .render()

        then: 'the application consumes the Hibernate 7 Micronaut BOM variant as an enforced platform'
        template.contains('implementation enforcedPlatform("org.apache.grails:grails-hibernate7-micronaut-bom:$grailsVersion")')
        !template.contains('org.apache.grails:grails-micronaut-bom')

        and: 'the buildscript classpath uses the Hibernate 7 Micronaut BOM variant'
        template.contains('classpath platform("org.apache.grails:grails-hibernate7-micronaut-bom:$grailsVersion")')
        !template.contains('org.apache.grails:grails-hibernate7-bom:')
    }

    void "test buildSrc uses the hibernate 7 micronaut bom when micronaut is added"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7", "grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_25)
                .renderBuildSrc()

        then:
        template.contains('implementation platform("org.apache.grails:grails-hibernate7-micronaut-bom:$grailsVersion")')
        !template.contains('org.apache.grails:grails-hibernate7-bom:')
        !template.contains('org.apache.grails:grails-bom:')
    }

    void "test buildSrc uses the micronaut bom when micronaut is added without hibernate 7"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate5", "grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_25)
                .renderBuildSrc()

        then:
        template.contains('implementation platform("org.apache.grails:grails-micronaut-bom:$grailsVersion")')
        !template.contains('grails-hibernate7-micronaut-bom')
        !template.contains('org.apache.grails:grails-bom:')
    }

    void "test hibernate 5 micronaut bom is used when micronaut is added without hibernate 7"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate5", "grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_25)
                .render()

        then:
        template.contains('org.apache.grails:grails-micronaut-bom')
        !template.contains('grails-hibernate7-micronaut-bom')
    }

    void "test dependencies are present for buildSrc"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7"])
                .renderBuildSrc()

        then:
        template.contains('implementation platform("org.apache.grails:grails-hibernate7-bom:$grailsVersion")')
        template.contains('implementation "org.apache.grails:grails-data-hibernate7"')
        !template.contains('org.apache.grails:grails-bom')
        !template.contains('grails-data-hibernate5')
    }

    void "test buildSrc is present for buildscript dependencies"() {
        given:
        final def output = generate(ApplicationType.WEB, hibernate7Options())
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle != null
        buildGradle.contains("classpath \"org.apache.grails:grails-data-hibernate7\"")
        !buildGradle.contains("grails-data-hibernate5")
    }

    void "test database migration uses the hibernate 7 artifact"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["gorm-hibernate7", "database-migration"])
                .render()

        then:
        template.contains('grails-data-hibernate7-dbmigration')
        !template.contains('grails-data-hibernate5-dbmigration')
    }

    void "test selecting both hibernate implementations is rejected"() {
        when:
        getFeatures(['gorm-hibernate5', 'gorm-hibernate7'])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('Only one Grails Data for Hibernate implementation can be selected')
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-hibernate7'])

        then:
        ctx.configuration.containsKey("dataSource.pooled")
        ctx.configuration.containsKey("dataSource.jmxExport")
        ctx.configuration.containsKey("hibernate.cache.queries")
        ctx.configuration.containsKey("hibernate.cache.use_second_level_cache")
        ctx.configuration.containsKey("hibernate.cache.use_query_cache")
    }

    void "test match values of datasource config"() {
        when:
        final def output = generate(ApplicationType.WEB, hibernate7Options())
        final String applicationYaml = output["grails-app/conf/application.yml"]
        def config = new YamlSlurper().parseText(applicationYaml)

        then:
        config.environments.development.dataSource.dbCreate == 'create-drop'
        config.environments.test.dataSource.dbCreate == 'update'
        config.environments.production.dataSource.dbCreate == 'none'
        config.environments.development.dataSource.url == 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        config.environments.test.dataSource.url == 'jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        config.environments.production.dataSource.url == 'jdbc:h2:./prodDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
    }
}
