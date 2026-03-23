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

package org.grails.forge.feature.other

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class ShadePluginSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test gradle shadow jar feature'() {
        when:
        final Features features = getFeatures(["shade"])

        then:
        features.contains("shade")
    }

    void "test shadow jar gradle configurations"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ["shade"])
        final def buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("id \"com.gradleup.shadow\"")
    }
}
