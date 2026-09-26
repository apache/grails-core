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
package org.grails.testing

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import spock.lang.Specification
import testing.included.IncludedGreeting

class IncludedPluginBeansSpec extends Specification implements GrailsUnitTest {

    Set<String> getIncludePlugins() {
        GrailsApplicationBuilder.DEFAULT_INCLUDED_PLUGINS + ['includedBeans'] as Set<String>
    }

    void "an included plugin's beans block is registered"() {
        expect:
        applicationContext.getBean('includedGreeting', IncludedGreeting).text == 'hello from an included plugin'
    }
}

class IncludedPluginBackOffSpec extends Specification implements GrailsUnitTest {

    @Configuration
    static class CustomGreetingConfiguration {

        @Bean
        IncludedGreeting customGreeting() {
            new IncludedGreeting(text: 'hello from the test')
        }
    }

    Set<String> getIncludePlugins() {
        GrailsApplicationBuilder.DEFAULT_INCLUDED_PLUGINS + ['includedBeans'] as Set<String>
    }

    void "the test's configuration is registered first, so an included plugin's conditional bean backs off"() {
        expect:
        applicationContext.getBean('customGreeting', IncludedGreeting).text == 'hello from the test'
        !applicationContext.containsBean('includedGreeting')
    }
}

class PluginNotIncludedSpec extends Specification implements GrailsUnitTest {

    void "a plugin that is not included contributes nothing from its beans block"() {
        expect:
        !applicationContext.containsBean('includedGreeting')
    }
}
