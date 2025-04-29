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
package grails.plugins;

import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import groovy.lang.GroovyClassLoader;
import org.grails.plugins.IncludingPluginFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultGrailsPluginManagerTests {

    private Class<?> first;
    private Class<?> second;
    private Class<?> third;
    private Class<?> fourth;

    @Test
    @SuppressWarnings("rawtypes")
    public void testLoadPlugins() {

        GroovyClassLoader gcl = new GroovyClassLoader();

        first = gcl.parseClass("class FirstGrailsPlugin {\n" +
            "def version = 1.0\n" +
            "}");
        second = gcl.parseClass("class SecondGrailsPlugin {\n" +
            "def version = 1.0\n" +
            "def dependsOn = [first:version]\n" +
            "}");
        third = gcl.parseClass("import grails.util.GrailsUtil\n" +
                "class ThirdGrailsPlugin {\n" +
            "def version = GrailsUtil.getGrailsVersion()\n" +
            "def dependsOn = [i18n:version]\n" +
            "}");
        fourth = gcl.parseClass("class FourthGrailsPlugin {\n" +
            "def version = 1.0\n" +
            "def dependsOn = [second:version, third:version]\n" +
            "}");

        GrailsApplication app = new DefaultGrailsApplication(new Class[]{}, gcl);
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app);

        DefaultGrailsPluginManager manager = new DefaultGrailsPluginManager(new Class[]{first, second, third, fourth}, app);
        manager.setParentApplicationContext(parent);
        manager.setPluginFilter(new IncludingPluginFilter("dataSource", "first", "third"));

        manager.loadPlugins();

        List pluginList = manager.getPluginList();

        assertNotNull(manager.getGrailsPlugin("dataSource"));
        assertNotNull(manager.getGrailsPlugin("first"));
        assertNotNull(manager.getGrailsPlugin("third"));
        //dataSource depends on core
        assertNotNull(manager.getGrailsPlugin("core"));
        //third depends on i18n
        assertNotNull(manager.getGrailsPlugin("third"));

        assertEquals(5, pluginList.size(), "Expected plugins not loaded. Expected " + 5 + " but got " + pluginList);
    }

    /**
     * Test the known 1.0.2 failure where:
     *
     * mail 0.3 = has no deps
     * quartz 0.3-SNAPSHOT: loadAfter = ['core', 'hibernate']
     * emailconfirmation 0.4: dependsOn = [quartz:'0.3 > *', mail: '0.2 > *']
     *
     * ...and emailconfirmation is NOT loaded first.
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testDependenciesWithDelayedLoadingWithVersionRangeStrings() {
        GroovyClassLoader gcl = new GroovyClassLoader();

        // These are defined in a specific order so that the one with the range dependencies
        // is the first in the list, and its dependencies load after
        first = gcl.parseClass("class FirstGrailsPlugin {\n" +
            "def version = \"0.4\"\n" +
            "def dependsOn = [second:'0.3 > *', third:'0.2 > *']\n" +
            "}");
        second = gcl.parseClass("class SecondGrailsPlugin {\n" +
            "def version = \"0.3\"\n" +
            "def dependsOn = [:]\n" +
            "}");
        third = gcl.parseClass("class ThirdGrailsPlugin {\n" +
            "def version = \"0.3-SNAPSHOT\"\n" +
            "def loadAfter = ['core', 'hibernate']\n" +
            "}");

        GrailsApplication app = new DefaultGrailsApplication(new Class[]{}, gcl);
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app);

        DefaultGrailsPluginManager manager = new DefaultGrailsPluginManager(new Class[]{first, second, third}, app);
        manager.setParentApplicationContext(parent);
        manager.setPluginFilter(new IncludingPluginFilter("dataSource", "first", "second", "third"));

        manager.loadPlugins();

        List pluginList = manager.getPluginList();

        assertNotNull(manager.getGrailsPlugin("first"));
        assertNotNull(manager.getGrailsPlugin("second"));
        //dataSource depends on core
        assertNotNull(manager.getGrailsPlugin("core"));
        //third depends on i18n
        assertNotNull(manager.getGrailsPlugin("third"));

        assertEquals(5, pluginList.size(), "Expected plugins not loaded. Expected " + 5 + " but got " + pluginList);
    }

    @Test
    public void testLoadingOrderGRAILS9426() {
        // GRAILS-9426
        DefaultGrailsPluginManager manager = loadPlugins("class FirstGrailsPlugin {\n" +
                    "def version = '1.0'\n" +
                    "}", "class SecondGrailsPlugin {\n" +
                    "def version = '1.0'\n" +
                    "}", "import grails.util.GrailsUtil\n" +
                                    "class ThirdGrailsPlugin {\n" +
                                "def version = '1.0'\n" +
                                "}", "class FourthGrailsPlugin {\n" +
                                "def version = '1.0'\n" +
                                "def loadBefore = ['first', 'second']\n" +
                                "}");

        List<GrailsPlugin> pluginList = manager.getPluginList();

        assertNotNull(manager.getGrailsPlugin("first"));
        assertNotNull(manager.getGrailsPlugin("second"));
        assertNotNull(manager.getGrailsPlugin("third"));
        assertNotNull(manager.getGrailsPlugin("fourth"));

        List<GrailsPlugin> expectedOrder = new ArrayList<GrailsPlugin>();
        expectedOrder.add(manager.getGrailsPlugin("fourth"));
        expectedOrder.add(manager.getGrailsPlugin("first"));
        expectedOrder.add(manager.getGrailsPlugin("second"));
        expectedOrder.add(manager.getGrailsPlugin("third"));

        assertEquals(expectedOrder, pluginList, "Expected plugin order");

        assertEquals(4, pluginList.size(), "Expected plugins not loaded. Expected " + 4 + " but got " + pluginList);
    }

    DefaultGrailsPluginManager loadPlugins(String firstClassString, String secondClassString, String thirdClassString, String fourthClassString) {
        GroovyClassLoader gcl = new GroovyClassLoader();

        first = gcl.parseClass(firstClassString);
        second = gcl.parseClass(secondClassString);
        third = gcl.parseClass(thirdClassString);
        fourth = gcl.parseClass(fourthClassString);

        GrailsApplication app = new DefaultGrailsApplication(new Class[]{}, gcl);
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app);

        DefaultGrailsPluginManager manager = new DefaultGrailsPluginManager(new Class[]{first, second, third, fourth}, app);
        manager.setParentApplicationContext(parent);
        manager.setPluginFilter(new IncludingPluginFilter("first", "second", "third", "fourth"));

        manager.loadPlugins();
        return manager;
    }

    @Test
    public void testLoadingOrderLoadBeforeAndLoadAfter() {
        DefaultGrailsPluginManager manager = loadPlugins("class FirstGrailsPlugin {\n" +
                    "def version = '1.0'\n" +
                    "def loadAfter = ['second', 'third']\n" +
                    "}", "class SecondGrailsPlugin {\n" +
                    "def version = '1.0'\n" +
                    "}", "import grails.util.GrailsUtil\n" +
                                    "class ThirdGrailsPlugin {\n" +
                                "def version = '1.0'\n" +
                                "def loadBefore = ['fourth']\n" +
                                "}", "class FourthGrailsPlugin {\n" +
                                "def version = '1.0'\n" +
                                "def loadBefore = ['first', 'second']\n" +
                                "}");

        List<GrailsPlugin> pluginList = manager.getPluginList();

        List<GrailsPlugin> expectedOrder = new ArrayList<GrailsPlugin>();
        expectedOrder.add(manager.getGrailsPlugin("third"));
        expectedOrder.add(manager.getGrailsPlugin("fourth"));
        expectedOrder.add(manager.getGrailsPlugin("second"));
        expectedOrder.add(manager.getGrailsPlugin("first"));

        assertEquals(expectedOrder, pluginList, "Expected plugin order");
    }
}
