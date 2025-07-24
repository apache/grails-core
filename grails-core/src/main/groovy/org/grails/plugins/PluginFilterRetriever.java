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

import java.util.Collection;
import java.util.HashSet;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import grails.config.Config;
import grails.config.Settings;
import grails.plugins.PluginFilter;

/**
 * Implements mechanism for figuring out what <code>PluginFilter</code>
 * implementation to use based on a set of provided configuration properties.
 *
 * @author Phil Zoio
 * @author Graeme Rocher
 */
public class PluginFilterRetriever {


    @SuppressWarnings("rawtypes")
    public PluginFilter getPluginFilter(Config config) {

        Assert.notNull(config, "Config should not be null");
        Object includes = config.getProperty(Settings.PLUGIN_INCLUDES, Object.class, null);
        Object excludes = config.getProperty(Settings.PLUGIN_EXCLUDES, Object.class, null);

        return getPluginFilter(includes, excludes);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    PluginFilter getPluginFilter(Object includes, Object excludes) {
        PluginFilter pluginFilter = null;

        if (includes != null) {
            if (includes instanceof Collection) {
                pluginFilter = new IncludingPluginFilter(new HashSet((Collection)includes));
            }
            else {
                String[] includesArray = StringUtils.commaDelimitedListToStringArray(includes.toString());
                pluginFilter = new IncludingPluginFilter(includesArray);
            }
        }
        else if (excludes != null) {
            if (excludes instanceof Collection) {
                pluginFilter = new ExcludingPluginFilter(new HashSet((Collection)excludes));
            }
            else {
                String[] excludesArray = StringUtils.commaDelimitedListToStringArray(excludes.toString());
                pluginFilter = new ExcludingPluginFilter(excludesArray);
            }
        }
        else {
            pluginFilter = new IdentityPluginFilter();
        }
        return pluginFilter;
    }
}
