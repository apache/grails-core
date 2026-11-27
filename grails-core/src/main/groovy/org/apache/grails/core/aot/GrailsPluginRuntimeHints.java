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
 * Registers what finding the plugins reads: the descriptors that list them, and the classes they name.
 *
 * <p>Plugins are found by reading every {@code META-INF/grails-plugin.xml} on the classpath, loading
 * the plugin classes each one names, creating an instance of each and reading its version, the
 * Grails versions it supports and what it loads after through its getters. An image carries none of
 * that unless asked: without the descriptors no plugin is found and the application fails to start
 * with "Grails was unable to load plugins dynamically", and without the classes every plugin fails
 * to load and the same thing happens. A descriptor is Groovy written largely without static
 * compilation, so its closures reach its own fields and methods dynamically too -- reading a
 * constant it declares stopped the context from starting.</p>
 *
 * <p>Each plugin class's own class file is registered as a resource as well. A plugin is located by
 * looking its class file up -- where it was found is the root its other resources are read relative
 * to -- and an image carries no class files unless asked, so every plugin failed to register with
 * "Root classpath resource not found".</p>
 *
 * <p>The classes are read from the descriptors while the hints are written, on an ordinary JVM with
 * the full classpath, so a plugin in any package is covered -- an application's own plugins are not
 * the framework's and are named in no list here.</p>
 *
 * @since 9.0
 */
public class GrailsPluginRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(PluginUtils.PLUGIN_XML_PATTERN);
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        for (PluginDescriptor descriptor : PluginUtils.scanPluginDescriptorResources(loader)) {
            for (String pluginClass : descriptor.getProvidedPlugins()) {
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
    }

}
