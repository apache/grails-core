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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Declarative Gradle DSL options for a Grails plugin, configured through the nested
 * {@code grails { plugin { ... } }} block.
 *
 * <p>Allows starter-only (descriptor-less) Grails plugins to declare plugin metadata without writing
 * a {@code *GrailsPlugin} Groovy class, and allows any plugin to supply additional {@code pluginExcludes}
 * patterns at build time.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class GrailsPluginOptions {

    /** Requests a classless starter descriptor when the source set contains no plugin class. */
    @Input
    @Optional
    abstract Property<Boolean> getStarter()

    @Input
    @Optional
    abstract Property<String> getName()

    @Input
    @Optional
    abstract Property<String> getVersion()

    @Input
    @Optional
    abstract Property<String> getGrailsVersion()

    @Input
    @Optional
    abstract ListProperty<String> getLoadAfter()

    @Input
    @Optional
    abstract ListProperty<String> getLoadBefore()

    @Input
    @Optional
    abstract MapProperty<String, String> getDependsOn()

    @Input
    @Optional
    abstract ListProperty<String> getEvict()

    @Input
    @Optional
    abstract ListProperty<String> getObserve()

    @Input
    @Optional
    abstract Property<Boolean> getEnabled()

    @Input
    @Optional
    abstract ListProperty<String> getEnvironmentsIncludes()

    @Input
    @Optional
    abstract ListProperty<String> getEnvironmentsExcludes()

    @Input
    @Optional
    abstract ListProperty<String> getPluginExcludes()
}
