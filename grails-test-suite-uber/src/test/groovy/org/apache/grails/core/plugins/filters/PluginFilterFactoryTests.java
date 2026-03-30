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
package org.apache.grails.core.plugins.filters;

import grails.config.Settings;
import grails.plugins.PluginFilter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginFilterFactoryTests {

    MockEnvironment createEnvironment(String includes, String excludes) {
        var env = new MockEnvironment();
        if(includes != null) {
            env.setProperty(Settings.PLUGIN_INCLUDES, includes);
        }
        if(excludes != null) {
            env.setProperty(Settings.PLUGIN_EXCLUDES, excludes);
        }
        return env;
    }

    @Test
    public void testIncludeFilterOne() {
        var fb = new PluginFilterRetriever();
        var bean = fb.getPluginFilter(createEnvironment("one", null));
        assertInstanceOf(IncludingPluginFilter.class, bean);

        var filter = (IncludingPluginFilter)bean;
        var suppliedNames = filter.getSuppliedNames();
        assertEquals(1, suppliedNames.size());
        assertTrue(suppliedNames.contains("one"));
    }

    @Test
    public void testIncludeFilter() {
        var fb = new PluginFilterRetriever();
        var bean = fb.getPluginFilter(createEnvironment("one, two", " three , four "));
        assertInstanceOf(IncludingPluginFilter.class, bean);

        var filter = (IncludingPluginFilter)bean;
        var suppliedNames = filter.getSuppliedNames();
        assertEquals(2, suppliedNames.size());
        assertTrue(suppliedNames.contains("two"));
    }

    @Test
    public void testExcludeFilter() {
        var fb = new PluginFilterRetriever();
        var bean = fb.getPluginFilter(createEnvironment(null, " three , four "));
        assertInstanceOf(ExcludingPluginFilter.class, bean);

        var filter = (ExcludingPluginFilter)bean;
        var suppliedNames = filter.getSuppliedNames();
        assertEquals(2, suppliedNames.size());
        assertTrue(suppliedNames.contains("four"));
    }

    @Test
    public void testDefaultFilter() {
        var fb = new PluginFilterRetriever();
        var bean = fb.getPluginFilter(createEnvironment(null, null));
        assertInstanceOf(NoOpPluginFilter.class, bean);
    }
}
