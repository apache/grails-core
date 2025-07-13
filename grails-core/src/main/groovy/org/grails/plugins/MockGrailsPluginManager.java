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

import grails.plugins.GrailsPlugin;
import groovy.lang.GroovyClassLoader;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import grails.plugins.exceptions.PluginException;

/**
 * @author Graeme Rocher
 * @since 0.4
 */
public class MockGrailsPluginManager extends AbstractGrailsPluginManager {
    public MockGrailsPluginManager(GrailsApplication application) {
        super(application);
        loadPlugins();
    }

    public MockGrailsPluginManager() {
        this(new DefaultGrailsApplication(new Class[0], new GroovyClassLoader()));
    }

    @Override
    public GrailsPlugin getGrailsPlugin(String name) {
        return plugins.get(name);
    }

    public GrailsPlugin getGrailsPlugin(String name, BigDecimal version) {
        return plugins.get(name);
    }

    @Override
    public boolean hasGrailsPlugin(String name) {
        return plugins.containsKey(name);
    }

    public void registerMockPlugin(GrailsPlugin plugin) {
        plugins.put(plugin.getName(), plugin);
        pluginList.add(plugin);
    }

    public GrailsPlugin[] getUserPlugins() {
        return getAllPlugins();
    }

    public void loadPlugins() throws PluginException {
        initialised = true;
    }

    @Override
    public boolean isInitialised() {
        return true;
    }

    public void refreshPlugin(String name) {
        GrailsPlugin plugin = plugins.get(name);
        if (plugin != null) {
            plugin.refresh();
        }
    }

    public Collection<?> getPluginObservers(GrailsPlugin plugin) {
        throw new UnsupportedOperationException(
                "The class [MockGrailsPluginManager] doesn't support the method getPluginObservers");
    }

    @SuppressWarnings("rawtypes")
    public void informObservers(String pluginName, Map event) {
        // do nothing
    }

}
