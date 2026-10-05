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

import spock.lang.Specification
import testing.included.RegisteredGreeting

/**
 * An included plugin's {@code doWithSpring} and {@code beanRegistrar} beans are registered before the
 * configuration classes are read, as an application's early phase registers them, so a
 * {@code @ConditionalOnMissingBean} bean backs off from them in a unit test as it does in an
 * application.
 */
class IncludedPluginConditionalBeansSpec extends Specification implements GrailsUnitTest {

    Set<String> getIncludePlugins() {
        GrailsApplicationBuilder.DEFAULT_INCLUDED_PLUGINS + ['includedBeans'] as Set<String>
    }

    Closure doWithConfig() {
        { config -> config.'included.greeting' = 'configured by the test' }
    }

    void "a plugin's conditional bean backs off from the plugin's own registered bean, as in an application"() {
        expect:
        applicationContext.getBeansOfType(RegisteredGreeting).keySet() == ['registeredGreeting'] as Set
    }

    void "the test's doWithConfig is applied before the plugin registers its beans"() {
        expect:
        applicationContext.getBean('configuredGreeting') == 'configured by the test'
    }
}

class TestConditionalBeanOverIncludedPluginSpec extends Specification implements GrailsUnitTest {

    Set<String> getIncludePlugins() {
        GrailsApplicationBuilder.DEFAULT_INCLUDED_PLUGINS + ['includedBeans'] as Set<String>
    }

    def beans = {
        bean('testGreeting', RegisteredGreeting).conditionalOnMissingBean() {
            new RegisteredGreeting(text: 'from the test')
        }
    }

    void "a test's conditional bean backs off from an included plugin's registered bean, as an application's does"() {
        expect:
        applicationContext.getBeansOfType(RegisteredGreeting).keySet() == ['registeredGreeting'] as Set
    }
}
