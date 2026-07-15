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

import org.apache.grails.undertow.core.autoconfigure.servlet.UndertowServletWebServerAutoConfiguration
import org.apache.grails.undertow.core.servlet.UndertowServletWebServerFactory

class UndertowGrailsPluginSpec extends Specification {

    void 'plugin descriptor declares expected metadata'() {
        given:
        def plugin = new UndertowGrailsPlugin()

        expect:
        plugin.title == 'Undertow for Grails'
        plugin.author == 'Grails Core Team'
        plugin.description
        plugin.documentation
        plugin.grailsVersion
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
