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

import org.grails.plugins.CoreGrailsPlugin

/**
 * Covers the plugins being found in an image.
 *
 * <p>Plugins are found by reading every {@code META-INF/grails-plugin.xml} on the classpath and
 * creating an instance of each class it names. An image carries neither unless asked, so an image
 * built without these hints found no plugin at all and stopped with "Grails was unable to load
 * plugins dynamically".</p>
 */
class GrailsPluginRuntimeHintsSpec extends Specification {

    @TempDir
    File classpathRoot

    RuntimeHints hints = new RuntimeHints()

    private ClassLoader withDescriptor(String pluginClass) {
        def descriptor = new File(classpathRoot, 'META-INF/grails-plugin.xml')
        descriptor.parentFile.mkdirs()
        descriptor.text = "<plugin name='example'><type>${pluginClass}</type></plugin>"
        new URLClassLoader([classpathRoot.toURI().toURL()] as URL[], getClass().classLoader)
    }

    void 'the descriptors that list the plugins are carried'() {
        when:
            new GrailsPluginRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'without them no plugin is found and the application does not start'
            RuntimeHintsPredicates.resource().forResource('META-INF/grails-plugin.xml').test(hints)
    }

    void 'a plugin class a descriptor names can be created and read'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor(CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'finding it creates an instance and reads its version and the Grails versions it supports through getters'
            RuntimeHintsPredicates.reflection().onType(CoreGrailsPlugin)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
                    .test(hints)
    }

    void 'the fields and methods of a descriptor can be reached from its closures'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor(CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'a closure in the descriptor reads its constants and calls its methods dynamically'
            RuntimeHintsPredicates.reflection().onType(CoreGrailsPlugin)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS)
                    .test(hints)
    }

    void 'a plugin class a descriptor names can be located'() {
        given: 'a descriptor naming a plugin'
            def loader = withDescriptor(CoreGrailsPlugin.name)

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'a plugin is located by looking its class file up, which an image carries only when asked'
            RuntimeHintsPredicates.resource().forResource('org/grails/plugins/CoreGrailsPlugin.class').test(hints)
    }

    void 'a plugin class that cannot be loaded is skipped'() {
        given: 'a descriptor naming a plugin that is not on the classpath'
            def loader = withDescriptor('com.example.AbsentGrailsPlugin')

        when:
            new GrailsPluginRuntimeHints().registerHints(hints, loader)

        then: 'registering it would fail the image build, which parses what it is asked to keep'
            hints.reflection().typeHints().noneMatch { it.type.name == 'com.example.AbsentGrailsPlugin' }
            !RuntimeHintsPredicates.resource().forResource('com/example/AbsentGrailsPlugin.class').test(hints)
    }
}
