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

import spock.lang.Specification

import grails.plugins.metadata.PluginDefinition
import grails.util.GrailsUtil
import org.apache.grails.core.plugins.PluginUtils

/**
 * grails-core is compiled before the compiler that writes plugin descriptors, so the descriptor of
 * {@link CoreGrailsPlugin} is checked in. It must say what the class's {@code @GrailsPluginDescriptor} says,
 * with the version the build fills in.
 */
class CoreGrailsPluginDescriptorSpec extends Specification {

    void 'the checked-in descriptor of the core plugin agrees with its annotation'() {
        given:
        PluginDefinition declared = PluginUtils.definitionOf(CoreGrailsPlugin)
        PluginDefinition published = getClass().classLoader.getResource(PluginUtils.getPluginDescriptorPath('core')).withReader('UTF-8') {
            Reader reader -> PluginDefinition.read('core', reader)
        }

        expect:
        published.pluginName == declared.pluginName
        published.pluginClass == declared.pluginClass
        published.grailsVersionRange == declared.grailsVersionRange
        published.dependsOn == declared.dependsOn
        published.loadAfter == declared.loadAfter
        published.loadBefore == declared.loadBefore
        published.observe == declared.observe
        published.evict == declared.evict
        published.environmentsIncludes == declared.environmentsIncludes
        published.environmentsExcludes == declared.environmentsExcludes
        published.watchedResources == declared.watchedResources
        published.pluginExcludes == declared.pluginExcludes
        published.providedArtefacts == declared.providedArtefacts
    }

    void 'the checked-in descriptor records the version grails-core is built at'() {
        expect:
        PluginUtils.definitionOf(CoreGrailsPlugin).pluginVersion == GrailsUtil.grailsVersion
    }
}
