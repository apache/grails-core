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

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.Named
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * An application that {@code forgePackTest} has Forge generate with the feature pack of the plugin,
 * and then builds: a fixture of the nested {@code grails { forgePackTest { } }} block.
 *
 * <pre><code>grails {
 *     forgePackTest {
 *         fixture('mongo') {
 *             applicationType = 'rest-api'
 *             options = [data: 'mongodb']
 *             features = ['views-markup']
 *         }
 *     }
 * }</code></pre>
 *
 * @since 9.0
 */
@CompileStatic
abstract class ForgePackFixture implements Named {

    private final String name

    @Inject
    ForgePackFixture(String name) {
        this.name = name
    }

    /**
     * The name of the fixture, which names its directory and its log: lower case letters, digits and
     * single hyphens, starting with a letter.
     */
    @Override
    String getName() {
        return name
    }

    /** The type of application: {@code web}, the default, {@code rest-api}, {@code web-plugin}, {@code plugin} or {@code rest-api-plugin}. */
    abstract Property<String> getApplicationType()

    /**
     * The options of the application, as the commands that create one take them: any of {@code data},
     * {@code servlet}, {@code reloading} and {@code jdk}. One that is left out has its default.
     */
    abstract MapProperty<String, String> getOptions()

    /** The features of the application besides that of the pack, by name. */
    abstract ListProperty<String> getFeatures()
}
