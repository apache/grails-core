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
package org.apache.grails.core.plugins

import groovy.transform.CompileStatic

import org.springframework.core.io.Resource

/**
 * Builds {@link PluginInfo} instances for specs in other packages. {@code PluginInfo} has a package-private
 * constructor, so this factory is the only way to pair a plugin with an arbitrary configuration resource
 * ({@code PluginUtils} always derives the resource from the plugin class).
 */
@CompileStatic
final class TestPluginInfos {

    private TestPluginInfos() {
    }

    static PluginInfo withConfigResource(String name, Class<?> pluginClass, Resource configResource) {
        PluginMetadata metadata = new PluginMetadata(
                name,
                '1.0',
                '*',
                pluginClass,
                new String[0],
                new String[0],
                Collections.emptyMap(),
                new String[0],
                new String[0],
                new String[0],
                Collections.emptyMap(),
                true
        )
        PluginDescriptor descriptor = new PluginDescriptor(null, [], [])
        return new PluginInfo(descriptor, metadata, configResource, false)
    }

}
