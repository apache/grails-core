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
package org.grails.plugins;

import java.util.List;

import org.springframework.context.ApplicationContext;

import grails.core.GrailsApplication;
import grails.core.support.ParentApplicationContextAware;
import org.apache.grails.core.plugins.ClasspathPluginFinder;
import org.apache.grails.core.plugins.PluginInfo;
import org.apache.grails.core.plugins.PluginUtils;

/**
 * @deprecated Plugin discovery is now handled by {@link org.apache.grails.core.plugins.ClasspathPluginFinder}.
 * This compatibility stub will be removed in Grails 8.0.0.
 */
@Deprecated(forRemoval = true, since = "7.1")
public class CorePluginFinder extends ClasspathPluginFinder implements ParentApplicationContextAware {

    /**
     * @deprecated Use {@link PluginUtils#PLUGIN_XML_PATTERN} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public static final String CORE_PLUGIN_PATTERN = PluginUtils.PLUGIN_XML_PATTERN;

    private final GrailsApplication application;

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.ClasspathPluginFinder} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public CorePluginFinder(GrailsApplication application) {
        this.application = application;
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.ClasspathPluginFinder#findClasspathPlugins(ClassLoader, String)} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public Class<?>[] getPluginClasses() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        List<PluginInfo> classpathPlugins = findClasspathPlugins(loader, application.getMetadata().getGrailsVersion());
        return classpathPlugins.stream().map(PluginInfo::getPluginClass).toArray(Class<?>[]::new);
    }

    @Override
    public void setParentApplicationContext(ApplicationContext parent) {
        // not used
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.ClasspathPluginFinder} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public BinaryGrailsPluginDescriptor getBinaryDescriptor(Class<?> pluginClass) {
        List<PluginInfo> classpathPlugins = findClasspathPlugins(Thread.currentThread().getContextClassLoader(), application.getMetadata().getGrailsVersion());
        for (PluginInfo classpathPlugin : classpathPlugins) {
            if (classpathPlugin.getPluginClass().equals(pluginClass)) {
                return new BinaryGrailsPluginDescriptor(classpathPlugin.getPluginDescriptor());
            }
        }
        return null;
    }
}
