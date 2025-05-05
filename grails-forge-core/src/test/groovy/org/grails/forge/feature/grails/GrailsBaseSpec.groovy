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

import org.grails.forge.BeanContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsBaseSpec extends BeanContextSpec implements CommandOutputFixture {

    void "test grails base dependencies"() {

        when:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        def buildGradle = output['build.gradle']

        then:
        buildGradle.contains("implementation \"org.apache.grails:grails-core\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-web-boot\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-logging\"")
    }

    void "test src/main directories are present"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))

        expect:
        output.containsKey("src/main/groovy/.gitkeep")
        output.containsKey("src/test/groovy/.gitkeep")
        output.containsKey("src/integration-test/groovy/.gitkeep")
    }
}