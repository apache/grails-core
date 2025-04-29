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
import org.grails.plugins.DefaultGrailsPlugin;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;
import org.grails.commons.test.AbstractGrailsMockTests;
import grails.plugins.exceptions.PluginException;
import org.springframework.context.ApplicationContext;

import java.io.File;

/**
 * Test for the DefaultGrailsPlugin class
 *
 * @author Graeme Rocher
 */
public class DefaultGrailsPluginTests extends AbstractGrailsMockTests {

    private Class<?> versioned;
    private Class<?> versioned2;
    private Class<?> versioned3;
    private Class<?> notVersion;
    private Class<?> notPluginClass;
    private Class<?> disabled;
    private Class<?> observed;
    private Class<?> camelCased;

    @Override
    protected void onSetUp() {
        versioned = gcl.parseClass("class MyGrailsPlugin {\n" +
                        "def version = 1.1;" +
                        "def watchedResources = [\"file:./grails-app/taglib/**/*TagLib.groovy\", \"./grails-app/controller/**/*.groovy\", \"file:/absolutePath/**/*.gsp\" ]\n" +
                        "def doWithSpring = {" +
                        "classEditor(org.springframework.beans.propertyeditors.ClassEditor,application.classLoader)" +
                        "}\n" +
                        "def doWithApplicationContext = { ctx ->" +
                        "assert ctx != null" +
                        "}\n" +
                        "def onChange = { event ->" +
                        "assert event != null" +
                        "}" +
                        "}");
        versioned2 = gcl.parseClass("class MyTwoGrailsPlugin extends grails.plugins.Plugin {\n" +
                "def version = 1.1;" +
                "Closure doWithSpring() { {->" +
                "classEditor(org.springframework.beans.propertyeditors.ClassEditor,application.classLoader)" +
                "}}\n" +
                "}");
        versioned3 = gcl.parseClass("class MyThreeGrailsPlugin extends grails.plugins.Plugin {\n" +
                "def version = 1.1;" +
                "Object invokeMethod(String name, Object args) {" +
                "true" +
                "}\n" +
                "Closure doWithSpring() { {->" +
                "classEditor(org.springframework.beans.propertyeditors.ClassEditor,application.classLoader)" +
                "}}\n" +
                "}");
        notVersion = gcl.parseClass("class AnotherGrailsPlugin {\n" +
                        "}");
        notPluginClass = gcl.parseClass("class SomeOtherPlugin {\n" +
                                        "def version = 1.4;" +
                                        "}");

        disabled = gcl.parseClass("class DisabledGrailsPlugin {" +
                                  "def version = 1.1; " +
                                  "def status = 'disabled'; }");

        observed = gcl.parseClass("class ObservingGrailsPlugin {" +
                                  "def observe = ['another'];" +
                                  "def version = 1.1; " +
                                  "def status = 'disabled'; }");

        camelCased = gcl.parseClass("class CamelCasedGrailsPlugin {" +
                                    "def version = 2.1; " +
                                    "def status = 'disabled'; }");
    }

    public void testDefaultGrailsPlugin() {
        @SuppressWarnings("unused")
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);

        try {
            @SuppressWarnings("unused")
            GrailsPlugin notVersionPlugin = new DefaultGrailsPlugin(notVersion, ga);
            fail("Should have thrown IllegalArgumentException for unversioned plugin");
        }
        catch (PluginException e) {
            // expected
        }

        try {
            @SuppressWarnings("unused")
            GrailsPlugin notPlugin = new DefaultGrailsPlugin(notPluginClass, ga);
            fail("Should have thrown an exception for invalid plugin");
        }
        catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetPluginPath() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("/plugins/my-1.1", versionPlugin.getPluginPath());

        GrailsPlugin camelCasedPlugin = new DefaultGrailsPlugin(camelCased, ga);
        assertEquals("/plugins/camel-cased-2.1", camelCasedPlugin.getPluginPath());
    }

    public void testDisabledPlugin() {
        GrailsPlugin disabledPlugin = new DefaultGrailsPlugin(disabled, ga);
        GrailsPlugin enabledPlugin = new DefaultGrailsPlugin(versioned, ga);

        assertFalse(disabledPlugin.isEnabled());
        assertTrue(enabledPlugin.isEnabled());
    }

    public void testDoWithApplicationContext() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
        versionPlugin.doWithRuntimeConfiguration(springConfig);

        ApplicationContext ctx = springConfig.getApplicationContext();

        assertTrue(ctx.containsBean("classEditor"));

        versionPlugin.doWithApplicationContext(ctx);
    }

    public void testDoWithRuntimeConfiguration() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
        versionPlugin.doWithRuntimeConfiguration(springConfig);

        ApplicationContext ctx = springConfig.getApplicationContext();

        assertTrue(ctx.containsBean("classEditor"));

        // Version 2
        GrailsPlugin versionPlugin2 = new DefaultGrailsPlugin(versioned2, ga);

        RuntimeSpringConfiguration springConfig2 = new DefaultRuntimeSpringConfiguration();
        versionPlugin2.doWithRuntimeConfiguration(springConfig2);

        ApplicationContext ctx2 = springConfig2.getApplicationContext();

        assertTrue(ctx2.containsBean("classEditor"));

        // Version 3
        GrailsPlugin versionPlugin3 = new DefaultGrailsPlugin(versioned3, ga);

        RuntimeSpringConfiguration springConfig3 = new DefaultRuntimeSpringConfiguration();
        versionPlugin3.doWithRuntimeConfiguration(springConfig3);

        ApplicationContext ctx3 = springConfig3.getApplicationContext();

        assertTrue(ctx3.containsBean("classEditor"));
    }

    public void testGetName() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("my", versionPlugin.getName());
    }

    public void testGetVersion() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("1.1", versionPlugin.getVersion());
    }

    public void testObservers() {

        GrailsPlugin observingPlugin = new DefaultGrailsPlugin(observed, ga);

        assertEquals(1, observingPlugin.getObservedPluginNames().length);
        assertEquals("another", observingPlugin.getObservedPluginNames()[0]);
    }

    public void testWatchedResources() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(0).getDirectory().getPath(), "./grails-app/taglib".replace("/", File.separator));
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(1).getDirectory().getPath(), "./grails-app/controller".replace("/", File.separator));
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(2).getDirectory().getPath(), "/absolutePath".replace("/", File.separator));
    }
}
