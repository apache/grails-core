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
package org.grails.compiler.injection

import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class ApplicationClassInjectorSpec extends Specification {

    @Unroll
    def "EXCLUDED_AUTO_CONFIGURE_CLASSES contains expected entry #className"(String className) {
        expect:
        ApplicationClassInjector.EXCLUDED_AUTO_CONFIGURE_CLASSES.contains(className)

        where:
        className << [
            'org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration',
            'org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration',
            'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration'
        ]
    }

    @Unroll
    def "CONDITIONAL_EXCLUSIONS contains expected entry #expected.excludeClass"(Map<String, String> expected) {
        when:
        def exclusion = ApplicationClassInjector.CONDITIONAL_EXCLUSIONS.find {
            it.excludeClass == expected.excludeClass
        }

        then:
        exclusion != null
        exclusion.pluginClass == expected.pluginClass
        exclusion.systemProperty == expected.systemProperty

        where:
        expected << [
            [
                    excludeClass: 'org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration',
                    pluginClass: 'org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin',
                    systemProperty: 'grails.autoconfigure.exclude.liquibase'
            ]
        ]
    }

    def "system property defaults to true (exclusion enabled)"() {
        given:
        def prop = 'grails.autoconfigure.exclude.liquibase'

        when:
        System.clearProperty(prop)

        then:
        Boolean.parseBoolean(System.getProperty(prop, 'true'))
    }

    def "system property set to false disables exclusion"() {
        given:
        def prop = 'grails.autoconfigure.exclude.liquibase'

        when:
        System.setProperty(prop, 'false')

        then:
        !Boolean.parseBoolean(System.getProperty(prop, 'true'))
    }

    def "shouldInject returns false for null URL"() {
        given:
        def injector = new ApplicationClassInjector()

        expect:
        !injector.shouldInject(null)
    }

    def "artefact types contains Application"() {
        given:
        def injector = new ApplicationClassInjector()

        expect:
        injector.artefactTypes.contains('Application')
    }
}
