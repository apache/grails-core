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
package org.grails.plugins

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.env.StandardEnvironment
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginInfo

/**
 * The auto-configurations the framework plugins' beans blocks compile to (their siblings) are ordered as the
 * plugins themselves are: Spring Boot runs them in an order the plugin order agrees with.
 */
class PluginAutoConfigurationOrderParitySpec extends Specification {

    @Shared
    List<PluginInfo> plugins

    @Shared
    Map<String, String> pluginBySibling

    void setupSpec() {
        def discovery = new DefaultPluginDiscovery()
        discovery.init(new StandardEnvironment())
        plugins = discovery.pluginsInTopologicalOrder
        pluginBySibling = plugins.findAll { it.definition.autoConfiguration }
                .collectEntries { [(it.definition.autoConfiguration): it.name] }
    }

    void 'the framework plugins on this class path have generated auto-configurations to compare'() {
        expect:
        pluginBySibling.values().containsAll(['dataBinding', 'i18n', 'urlMappings', 'domainClass'])
    }

    void 'no generated auto-configuration is ordered against another one the opposite way to the plugins'() {
        given:
        List<String> order = plugins*.name

        when:
        List<String> contradictions = []
        pluginBySibling.each { String sibling, String plugin ->
            def annotation = getClass().classLoader.loadClass(sibling).getAnnotation(AutoConfiguration)
            (annotation.afterName() + annotation.after()*.name).each { String other ->
                if (pluginBySibling[other] && order.indexOf(pluginBySibling[other]) > order.indexOf(plugin)) {
                    contradictions << "${sibling} comes after ${other}, but ${plugin} comes before ${pluginBySibling[other]}"
                }
            }
            (annotation.beforeName() + annotation.before()*.name).each { String other ->
                if (pluginBySibling[other] && order.indexOf(pluginBySibling[other]) < order.indexOf(plugin)) {
                    contradictions << "${sibling} comes before ${other}, but ${plugin} comes after ${pluginBySibling[other]}"
                }
            }
        }

        then:
        contradictions.empty
    }

    void 'every ordering between two plugins with generated auto-configurations orders the auto-configurations too'() {
        when:
        Map<String, PluginInfo> byName = plugins.collectEntries { [(it.name): it] }
        List<String> missing = []
        plugins.each { PluginInfo plugin ->
            String sibling = plugin.definition.autoConfiguration
            if (!sibling) {
                return
            }
            plugin.definition.orderedAfter.each { String name ->
                String other = byName[name]?.definition?.autoConfiguration
                if (other && !orders(other, sibling)) {
                    missing << "${plugin.name} comes after ${name}, but ${sibling} is not ordered after ${other}"
                }
            }
            plugin.definition.before.each { String name ->
                String other = byName[name]?.definition?.autoConfiguration
                if (other && !orders(sibling, other)) {
                    missing << "${plugin.name} comes before ${name}, but ${sibling} is not ordered before ${other}"
                }
            }
        }

        then:
        missing.empty
    }

    /** Whether the auto-configuration first is declared, by either one, to run before second. */
    private boolean orders(String first, String second) {
        def firstAnnotation = getClass().classLoader.loadClass(first).getAnnotation(AutoConfiguration)
        def secondAnnotation = getClass().classLoader.loadClass(second).getAnnotation(AutoConfiguration)
        second in (firstAnnotation.beforeName() as List) || second in firstAnnotation.before()*.name ||
                first in (secondAnnotation.afterName() as List) || first in secondAnnotation.after()*.name
    }
}
