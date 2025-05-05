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
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsDefaultPluginsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test that default grails plugins are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("implementation \"org.apache.grails:grails-rest-transforms\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-databinding\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-i18n\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-services\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-interceptors\"")
    }

    void "test i18n message properties files are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))

        expect:
        output.containsKey("grails-app/i18n/messages.properties")
        !(Arrays.asList("cs", "da", "de", "es", "fr", "it", "ja", "nb", "nl", "pl", "pt_BR", "pt_PT", "ru", "sk", "sv", "th", "zh_CN")
                .findAll {prop -> { !output.containsKey("grails-app/i18n/messages_" + prop + ".properties") }})
    }

}
