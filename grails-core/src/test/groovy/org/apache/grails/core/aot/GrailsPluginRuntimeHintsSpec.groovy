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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.plugins.PluginUtils
import org.grails.plugins.CoreGrailsPlugin

/**
 * Covers the plugins being found in an image via {@code META-INF/grails/plugins.imports}
 * and per-plugin {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors.
 */
class GrailsPluginRuntimeHintsSpec extends Specification {

    @TempDir
    File classpathRoot

    RuntimeHints hints = new RuntimeHints()

    private ClassLoader withDescriptor(String pluginName, String pluginClass, boolean withConfig = false, boolean withViews = false) {
        def imports = new File(classpathRoot, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = "${pluginName}\n"

        def descriptor = new File(classpathRoot, PluginUtils.getPluginDescriptorPath(pluginName))
        descriptor.parentFile.mkdirs()
        def content = new StringBuilder("format.version=1\nname=${pluginName}\nversion=1.0.0\n")
        if (pluginClass) {
            content.append("class=${pluginClass}\n")
        }
        descriptor.text = content.toString()

        if (withConfig) {
            def config = new File(classpathRoot, PluginUtils.getPluginConfigPath(pluginName, PluginUtils.PLUGIN_YML_CONFIG))
            config.parentFile.mkdirs()
            config.text = 'example:\n  enabled: true\n'
        }
        if (withViews) {
            def views = new File(classpathRoot, PluginUtils.getPluginViewsPath(pluginName))
            views.parentFile.mkdirs()
            views.text = '/index.gsp=example_index_gsp\n'
        }

        new URLClassLoader([classpathRoot.toURI().toURL()] as URL[], getClass().classLoader)
    }

    void 'the plugins.imports index and discovered plugin.properties descriptors are carried'() {
        given:
            def loader = withDescriptor('example', CoreGrailsPlugin.name, true, true)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then:
            RuntimeHintsPredicates.resource().forResource(PluginUtils.PLUGINS_IMPORTS_PATH).test(hints)
            RuntimeHintsPredicates.resource().forResource('META-INF/grails/plugins/example/plugin.properties').test(hints)
            RuntimeHintsPredicates.resource().forResource('META-INF/grails/plugins/example/plugin.yml').test(hints)
            RuntimeHintsPredicates.resource().forResource('META-INF/grails/plugins/example/views.properties').test(hints)
    }

    void 'a plugin class a descriptor names can be created and read'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor('example', CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'finding it creates an instance and reads its version and the Grails versions it supports through getters'
            RuntimeHintsPredicates.reflection().onType(CoreGrailsPlugin)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
                    .test(hints)
    }

    void 'the fields and methods of a descriptor can be reached from its closures'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor('example', CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'a closure in the descriptor reads its constants and calls its methods dynamically'
            RuntimeHintsPredicates.reflection().onType(CoreGrailsPlugin)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS)
                    .test(hints)
    }

    void 'a descriptor-less starter plugin registers its descriptor and config without reflection hints'() {
        given: 'a descriptor-less plugin'
            def loader = withDescriptor('starterOnly', null, true, false)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then:
            RuntimeHintsPredicates.resource().forResource('META-INF/grails/plugins/starterOnly/plugin.properties').test(hints)
            RuntimeHintsPredicates.resource().forResource('META-INF/grails/plugins/starterOnly/plugin.yml').test(hints)
    }

    void 'a plugin class a descriptor names can be located'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor('example', CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'a plugin is located by looking its class file up, which an image carries only when asked'
            RuntimeHintsPredicates.resource().forResource('org/grails/plugins/CoreGrailsPlugin.class').test(hints)
    }

    void 'a plugin class that cannot be loaded is skipped'() {
        given: 'a descriptor naming a plugin that is not on the classpath'
            def loader = withDescriptor('absent', 'com.example.AbsentGrailsPlugin')

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'registering it would fail the image build, which parses what it is asked to keep'
            hints.reflection().typeHints().noneMatch { it.type.name == 'com.example.AbsentGrailsPlugin' }
            !RuntimeHintsPredicates.resource().forResource('com/example/AbsentGrailsPlugin.class').test(hints)
    }

    void 'a legacy META-INF/grails-plugin.xml descriptor is carried when present on the classpath'() {
        given: 'a legacy XML plugin descriptor on the classpath'
            def legacyXml = new File(classpathRoot, 'META-INF/grails-plugin.xml')
            legacyXml.parentFile.mkdirs()
            legacyXml.text = "<plugin name='legacyCore' version='1.0.0'><type>${CoreGrailsPlugin.name}</type></plugin>"
            def loader = new URLClassLoader([classpathRoot.toURI().toURL()] as URL[], getClass().classLoader)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then:
            RuntimeHintsPredicates.resource().forResource('META-INF/grails-plugin.xml').test(hints)
            RuntimeHintsPredicates.reflection().onType(CoreGrailsPlugin)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    .test(hints)

        cleanup:
            loader?.close()
    }
}
