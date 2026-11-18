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
package testing.included

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.env.Environment

import grails.compiler.beans.GrailsBeans
import grails.plugins.Plugin

/**
 * A plugin whose {@code beans} block reaches a unit test through its generated
 * {@code IncludedBeansAutoConfiguration} or not at all, and whose {@code beanRegistrar()} bean is
 * applied as a loaded plugin's is. Deliberately outside {@code org.grails}, whose auto-configurations
 * the test harness registers regardless.
 */
// @AutoConfiguration is required, not decoration: a Plugin using @GrailsBeans must carry it, and the
// transform moves it onto the generated IncludedBeansAutoConfiguration
@GrailsBeans
@AutoConfiguration
class IncludedBeansGrailsPlugin extends Plugin {

    String version = '1.0'

    def beans = {
        bean(IncludedGreeting).conditionalOnMissingBean()

        // Backs off from registeredGreeting below, which is registered first, in a unit test as in an
        // application (IncludedPluginConditionalBeansSpec)
        bean('fallbackGreeting', RegisteredGreeting).conditionalOnMissingBean() {
            new RegisteredGreeting(text: 'fallback')
        }
    }

    // Reads the test's doWithConfig, which has to be applied before plugin beans are registered
    @Override
    Closure doWithSpring() {
        { ->
            configuredGreeting(String, config.getProperty('included.greeting', String, 'unset'))
        }
    }

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('registeredGreeting', RegisteredGreeting) { BeanRegistry.Spec<RegisteredGreeting> spec ->
                spec.supplier { new RegisteredGreeting(text: 'from the plugin') }
            }
            // A name the harness has a default for, which the plugin's bean wins over (ReplacingBeansSpec)
            registry.registerBean('messageSource', IncludedMessageSource)
        } as BeanRegistrar
    }
}
