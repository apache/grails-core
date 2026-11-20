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
import org.grails.forge.options.Options
import spock.lang.Unroll

class GrailsProjectSettingsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void "the build of a #applicationType application has no profile"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        output.containsKey('build.gradle')
        !output.get('build.gradle').contains('org.apache.grails.profiles')
        !(output.get('build.gradle') =~ /(?m)^\s*profile\b/).find()

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "the configuration of a #applicationType application has its default package and no profile"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        String configuration = output.get('grails-app/conf/application.yml')
        // the writer puts a key that is alone on its path on one line, which is what it is in a plugin
        configuration.contains('\n  codegen:\n    defaultPackage: example.grails\n') ||
                configuration.contains('grails.codegen.defaultPackage: example.grails\n')
        !(configuration =~ /(?m)^\s*profile:/).find()
        !configuration.contains('grails.profile')

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "a #applicationType application has no record but the state under .grails"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        !output.containsKey('grails-forge-cli.yml')
        output.containsKey('.grails/project.yml')
        output.get('.grails/forge.lock.yml').contains('- name: grails-project-settings\n')
        !output.get('.grails/forge.lock.yml').contains('grails-profiles')

        where:
        applicationType << ApplicationType.values().toList()
    }
}
