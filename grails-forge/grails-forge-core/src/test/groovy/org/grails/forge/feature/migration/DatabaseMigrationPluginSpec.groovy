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

package org.grails.forge.feature.migration

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class DatabaseMigrationPluginSpec extends ApplicationContextSpec implements CommandOutputFixture{

    void "test dependencies are present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["database-migration"])
                .render()

        then:
        template.contains('classpath "org.apache.grails:grails-data-hibernate5-dbmigration"')
        template.contains('implementation "org.apache.grails:grails-data-hibernate5-dbmigration"')
    }

    void "test dependencies are present for buildSrc"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["database-migration"])
                .renderBuildSrc()

        then:
        template.contains('implementation "org.apache.grails:grails-data-hibernate5-dbmigration"')
    }

    void "test dependencies are present for buildscript "() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ['database-migration'])
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle != null
        buildGradle.contains('classpath "org.apache.grails:grails-data-hibernate5-dbmigration"')
    }

    @Unroll
    void "test migrations directory is present"() {
        when:
        final def output = generate(applicationType, new Options(TestFramework.SPOCK), ['database-migration'])

        then:
        output.containsKey("grails-app/migrations/.gitkeep")

        where:
        applicationType << ApplicationType.values()
    }
}
