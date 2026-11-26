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
package org.apache.grails.core.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

import org.apache.grails.common.aot.RegistrableTypes;
import org.apache.grails.core.plugins.PluginDescriptor;
import org.apache.grails.core.plugins.PluginUtils;

/**
 * Registers what finding and initializing plugins reads: the {@code META-INF/grails/plugins.imports}
 * index, each plugin's {@code META-INF/grails/plugins/<name>/plugin.properties} descriptor, its
 * namespaced configuration and view manifest when present, and reflection hints for class-backed plugins.
 *
 * @since 9.0
 */
public class GrailsPluginRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(PluginUtils.PLUGINS_IMPORTS_PATH);
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        if (loader == null) {
            return;
        }
        for (PluginDescriptor descriptor : PluginUtils.scanPluginDescriptorResources(loader)) {
            registerPluginResources(hints, loader, descriptor.getPluginName());
            String pluginClass = descriptor.getPluginClassName();
            if (RegistrableTypes.loads(pluginClass, loader)) {
                hints.reflection().registerTypeIfPresent(loader, pluginClass,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
                hints.resources().registerPattern(ClassUtils.convertClassNameToResourcePath(pluginClass) +
                        ClassUtils.CLASS_FILE_SUFFIX);
            }
        }
    }

    private static void registerPluginResources(RuntimeHints hints, ClassLoader loader, String pluginName) {
        registerResourceIfPresent(hints, loader, PluginUtils.getPluginDescriptorPath(pluginName));
        registerResourceIfPresent(hints, loader, PluginUtils.getPluginConfigPath(pluginName, PluginUtils.PLUGIN_YML_CONFIG));
        registerResourceIfPresent(hints, loader, PluginUtils.getPluginConfigPath(pluginName, PluginUtils.PLUGIN_GROOVY_CONFIG));
        registerResourceIfPresent(hints, loader, PluginUtils.getPluginViewsPath(pluginName));
    }

    private static void registerResourceIfPresent(RuntimeHints hints, ClassLoader loader, String resourcePath) {
        if (loader.getResource(resourcePath) != null) {
            hints.resources().registerPattern(resourcePath);
        }
    }

}
