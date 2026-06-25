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

package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Integration test that verifies {@code @ConditionalOnProperty} works correctly
 * with properties defined in plugin {@code plugin.yml} files.
 *
 * <p>This test proves that the {@code grails.boot.config.GrailsEnvironmentPostProcessor} loads
 * plugin configuration files early enough in the Spring Boot lifecycle for
 * {@code @ConditionalOnProperty} annotations on {@code @AutoConfiguration} classes
 * to evaluate correctly.</p>
 *
 * <p>Each test plugin (loadfirst, loadsecond, loadafter) defines a feature toggle
 * property in its {@code plugin.yml} (e.g., {@code loadfirst.feature.enabled: true})
 * and an {@code @AutoConfiguration} class gated by {@code @ConditionalOnProperty}.
 * If the properties are loaded early enough, the conditional beans will be present
 * in the application context.</p>
 */
@Integration(applicationClass = Application)
class ConditionalOnPropertyFromPluginYmlSpec extends ContainerGebSpec {

    void "conditional beans from plugin.yml properties are all present"() {
        when: "navigating to the conditional beans inspection page"
        go('/inspectConfig/showConditionalBeans')

        then: "all three plugin conditional beans were created"
        $('#loadfirst-present').text() == 'true'
        $('#loadsecond-present').text() == 'true'
        $('#loadafter-present').text() == 'true'
    }

    void "conditional bean messages confirm they were loaded via @ConditionalOnProperty"() {
        when: "navigating to the conditional beans inspection page"
        go('/inspectConfig/showConditionalBeans')

        then: "all three beans report the expected message"
        $('#loadfirst-message').text() == 'Feature enabled via plugin.yml @ConditionalOnProperty'
        $('#loadsecond-message').text() == 'Feature enabled via plugin.yml @ConditionalOnProperty'
        $('#loadafter-message').text() == 'Feature enabled via plugin.yml @ConditionalOnProperty'
    }
}
