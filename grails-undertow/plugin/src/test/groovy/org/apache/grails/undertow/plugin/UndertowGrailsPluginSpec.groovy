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

package org.apache.grails.undertow.plugin

import groovy.transform.CompileStatic

import spock.lang.Specification

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

import grails.plugins.metadata.PluginDefinition
import org.apache.grails.core.plugins.PluginUtils
import org.apache.grails.undertow.core.autoconfigure.servlet.UndertowServletWebServerAutoConfiguration
import org.apache.grails.undertow.core.servlet.UndertowServletWebServerFactory

class UndertowGrailsPluginSpec extends Specification {

    void 'the compiler publishes the plugin descriptor with the build version'() {
        given:
        def resource = UndertowGrailsPlugin.classLoader.getResource(PluginUtils.getPluginDescriptorPath('undertow'))

        when:
        PluginDefinition definition = resource.withReader('UTF-8') { PluginDefinition.read('undertow', it) }

        then:
        definition.pluginName == 'undertow'
        definition.pluginClass == UndertowGrailsPlugin.name
        definition.pluginVersion
        definition.grailsVersionRange == definition.pluginVersion + ' > *'
        PluginUtils.definitionOf(UndertowGrailsPlugin).pluginVersion == definition.pluginVersion
    }

    void 'undertow autoconfiguration provides the servlet web server factory in a servlet web application'() {
        expect:
        factoryBeanCount() == 1
    }

    @CompileStatic
    private static int factoryBeanCount() {
        int count = -1
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(UndertowServletWebServerAutoConfiguration))
                .run { context ->
                    count = context.getBeansOfType(UndertowServletWebServerFactory).size()
                }
        return count
    }

}
