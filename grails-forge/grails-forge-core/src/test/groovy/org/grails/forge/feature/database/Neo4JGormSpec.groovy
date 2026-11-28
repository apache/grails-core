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
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.Unroll

class Neo4JGormSpec extends ApplicationContextSpec implements CommandOutputFixture {

    private static final String NEO4J_BOM = 'implementation platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")'

    private static final String NEO4J_BOM_OF_THE_BUILD = 'classpath platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")'

    static final String WITH_HIBERNATE7 = 'gorm-neo4j cannot be combined with gorm-hibernate7: an application with GORM for Neo4j uses grails-neo4j-bom, ' +
            'which manages Hibernate 5, and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Hibernate 7. Select gorm-hibernate5, ' +
            'or no Hibernate implementation, with gorm-neo4j.'

    static final String WITH_MICRONAUT = 'gorm-neo4j cannot be combined with grails-micronaut: an application with GORM for Neo4j uses grails-neo4j-bom, ' +
            'and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Micronaut.'

    void "test Mongo gorm features"() {
        when:
        Features features = getFeatures(['gorm-neo4j'])

        then:
        features.contains("gorm-neo4j")
    }

    void "test there can only be one of either MongoDB or Neo4j feature"() {
        when:
        getFeatures(['gorm-mongodb', 'gorm-neo4j'])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("There can only be one of the following features selected")
    }

    void "test dependencies are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["gorm-neo4j"])
                .render()

        then:
        template.contains("implementation \"org.apache.grails:grails-data-neo4j\"")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-neo4j'])

        then:
        ctx.configuration.containsKey("grails.neo4j.type")
        ctx.configuration.containsKey("grails.neo4j.location")
    }

    @Unroll
    void "an application with GORM for Neo4j imports grails-neo4j-bom, and no other Grails BOM: #description"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(features)
                .gormImpl(gormImpl)
                .render()

        then: 'the BOM of the application and of its build, which holds the Neo4j driver GORM for Neo4j is built with'
        template.contains(NEO4J_BOM)
        template.contains(NEO4J_BOM_OF_THE_BUILD)
        template.count('org.apache.grails:grails-neo4j-bom:') == 2
        !template.contains('org.apache.grails:grails-bom:')
        !template.contains('org.apache.grails:grails-hibernate7-bom:')
        !template.contains('enforcedPlatform')

        and: 'with the Grails Data implementation the application was created with'
        template.contains('implementation "org.apache.grails:grails-data-neo4j"')
        template.contains('implementation "org.apache.grails:grails-data-hibernate5"') == hibernate5

        where:
        description                                 | gormImpl            | features            | hibernate5
        '--data neo4j'                              | GormImpl.NEO4J      | []                  | false
        '--features gorm-neo4j'                     | GormImpl.HIBERNATE5 | ['gorm-neo4j']      | true
        '--data neo4j --features gorm-hibernate5'   | GormImpl.NEO4J      | ['gorm-hibernate5'] | true
    }

    void "the build of buildSrc imports grails-neo4j-bom too"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-neo4j'])
                .renderBuildSrc()

        then:
        template.contains(NEO4J_BOM)
        !template.contains('org.apache.grails:grails-bom:')
    }

    void "an application without GORM for Neo4j does not import grails-neo4j-bom"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-mongodb'])
                .render()

        then:
        template.contains('implementation platform("org.apache.grails:grails-bom:$grailsVersion")')
        !template.contains('grails-neo4j-bom')
    }

    @Unroll
    void "GORM for Neo4j is refused with #other for a #type application, which no Grails BOM covers it with: #selected with #gormImpl"() {
        when:
        beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'),
                new Options().withDevelopmentReloading(DevelopmentReloading.NONE).withGormImpl(gormImpl).withJavaVersion(jdk).withGiven(given),
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, [], [])

        then:
        FeatureSelectionException e = thrown()
        e.message == (other == 'gorm-hibernate7' ? WITH_HIBERNATE7 : WITH_MICRONAUT)

        where:
        [type, gormImpl, given, selected, other, jdk] << ApplicationType.values().toList().collectMany { ApplicationType each ->
            [[each, GormImpl.HIBERNATE7, [OptionName.DATA], ['gorm-neo4j'], 'gorm-hibernate7', JdkVersion.DEFAULT_OPTION],
             [each, GormImpl.NEO4J, [OptionName.DATA], ['gorm-hibernate7'], 'gorm-hibernate7', JdkVersion.DEFAULT_OPTION],
             [each, GormImpl.DEFAULT_OPTION, [], ['gorm-hibernate7', 'gorm-neo4j'], 'gorm-hibernate7', JdkVersion.DEFAULT_OPTION],
             [each, GormImpl.NEO4J, [OptionName.DATA], ['grails-micronaut'], 'grails-micronaut', JdkVersion.JDK_25],
             [each, GormImpl.DEFAULT_OPTION, [], ['grails-micronaut', 'gorm-neo4j'], 'grails-micronaut', JdkVersion.JDK_25],
             [each, GormImpl.HIBERNATE7, [OptionName.DATA], ['grails-micronaut', 'gorm-neo4j'], 'gorm-hibernate7', JdkVersion.JDK_25]]
        }
    }
}
