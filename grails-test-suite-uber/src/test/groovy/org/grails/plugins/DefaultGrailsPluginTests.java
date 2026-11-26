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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import groovy.lang.GroovyClassLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import grails.plugins.GrailsPlugin;
import grails.plugins.exceptions.PluginException;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.commons.test.AbstractGrailsMockTests;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for the DefaultGrailsPlugin class. The plugin classes are compiled at run time, so that no other test
 * discovers them from the class path.
 *
 * @author Graeme Rocher
 */
public class DefaultGrailsPluginTests extends AbstractGrailsMockTests {

    @TempDir
    Path temp;

    private Class<?> versioned;
    private Class<?> versioned2;
    private Class<?> versioned3;
    private Class<?> unversioned;
    private Class<?> disabled;
    private Class<?> observed;
    private Class<?> camelCased;

    @Override
    protected void onSetUp() {
        GroovyClassLoader versionedLoader = versionedLoader("MyGrailsPlugin", "my", "1.1",
                "CamelCasedGrailsPlugin", "camelCased", "2.1");
        versioned = versionedLoader.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(watchedResources = " +
                "['file:./grails-app/taglib/**/*TagLib.groovy', './grails-app/controller/**/*.groovy', 'file:/absolutePath/**/*.gsp'])\n" +
                "class MyGrailsPlugin extends grails.plugins.Plugin {\n" +
                "Closure doWithSpring() { {->" +
                "classEditor(org.springframework.beans.propertyeditors.ClassEditor, grailsApplication.classLoader)" +
                "}}\n" +
                "void doWithApplicationContext() {" +
                "assert applicationContext != null" +
                "}\n" +
                "void onChange(Map<String, Object> event) {" +
                "assert event != null" +
                "}\n" +
                "}");
        camelCased = versionedLoader.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(enabled = false)\n" +
                "class CamelCasedGrailsPlugin extends grails.plugins.Plugin {}");
        versioned2 = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor\n" +
                "class MyTwoGrailsPlugin extends grails.plugins.Plugin {\n" +
                "Closure doWithSpring() { {->" +
                "classEditor(org.springframework.beans.propertyeditors.ClassEditor, grailsApplication.classLoader)" +
                "}}\n" +
                "}");
        versioned3 = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor\n" +
                "class MyThreeGrailsPlugin extends grails.plugins.Plugin {\n" +
                "Object invokeMethod(String name, Object args) {" +
                "true" +
                "}\n" +
                "Closure doWithSpring() { {->" +
                "classEditor(org.springframework.beans.propertyeditors.ClassEditor, grailsApplication.classLoader)" +
                "}}\n" +
                "}");
        unversioned = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor\n" +
                "class AnotherGrailsPlugin extends grails.plugins.Plugin {\n" +
                "}");
        disabled = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(enabled = false)\n" +
                "class DisabledGrailsPlugin extends grails.plugins.Plugin {}");
        observed = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(observe = ['another'], enabled = false)\n" +
                "class ObservingGrailsPlugin extends grails.plugins.Plugin {}");
    }

    /**
     * A class loader whose class path holds a descriptor recording the version of each given plugin, as a
     * plugin jar does, so that a plugin class it compiles takes its version from it.
     *
     * @param classNameNameVersion triples of plugin class name, plugin name and version
     */
    private GroovyClassLoader versionedLoader(String... classNameNameVersion) {
        StringBuilder imports = new StringBuilder();
        try {
            for (int i = 0; i < classNameNameVersion.length; i += 3) {
                String className = classNameNameVersion[i];
                String name = classNameNameVersion[i + 1];
                imports.append(name).append('\n');
                Path descriptor = temp.resolve(PluginUtils.getPluginDescriptorPath(name));
                Files.createDirectories(descriptor.getParent());
                Files.writeString(descriptor, "format.version=1\nname=" + name + "\nclass=" + className +
                        "\nversion=" + classNameNameVersion[i + 2] + "\n");
            }
            Path importsFile = temp.resolve(PluginUtils.PLUGINS_IMPORTS_PATH);
            Files.createDirectories(importsFile.getParent());
            Files.writeString(importsFile, imports);
            URLClassLoader parent = new URLClassLoader(new URL[]{temp.toUri().toURL()}, getClass().getClassLoader());
            return new GroovyClassLoader(parent);
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testDefaultGrailsPlugin() {
        GrailsPlugin unversionedPlugin = new DefaultGrailsPlugin(unversioned, ga);
        assertEquals("another", unversionedPlugin.getName());
        assertNull(unversionedPlugin.getVersion(), "A plugin registered without a descriptor has no version");

        Class<?> notAPluginClass = gcl.parseClass("class SomeOtherPlugin {}");
        assertThrows(PluginException.class, () -> new DefaultGrailsPlugin(notAPluginClass, ga));

        Class<?> notAnnotated = gcl.parseClass("class NotAnnotatedGrailsPlugin extends grails.plugins.Plugin {}");
        assertThrows(PluginException.class, () -> new DefaultGrailsPlugin(notAnnotated, ga));

        Class<?> notAPluginSubclass = gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor\n" +
                "class NotASubclassGrailsPlugin {}");
        assertThrows(PluginException.class, () -> new DefaultGrailsPlugin(notAPluginSubclass, ga));
    }

    @Test
    public void testGetPluginPath() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("/plugins/my-1.1", versionPlugin.getPluginPath());

        GrailsPlugin camelCasedPlugin = new DefaultGrailsPlugin(camelCased, ga);
        assertEquals("/plugins/camel-cased-2.1", camelCasedPlugin.getPluginPath());
    }

    @Test
    public void testDisabledPlugin() {
        GrailsPlugin disabledPlugin = new DefaultGrailsPlugin(disabled, ga);
        GrailsPlugin enabledPlugin = new DefaultGrailsPlugin(versioned, ga);

        assertFalse(disabledPlugin.isEnabled());
        assertTrue(enabledPlugin.isEnabled());
    }

    @Test
    public void testDoWithApplicationContext() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
        versionPlugin.doWithRuntimeConfiguration(springConfig);

        ApplicationContext ctx = springConfig.getApplicationContext();

        assertTrue(ctx.containsBean("classEditor"));

        versionPlugin.doWithApplicationContext(ctx);
    }

    @Test
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
    }

    /**
     * A closure resolves a call to its owner first, and the bean builder of {@code doWithSpring} does not change that
     * (its delegate-first strategy has been commented out since the builder was restored for GRAILS-5779). A plugin
     * that overrides {@code invokeMethod} therefore answers the {@code classEditor(...)} call of its own closure, and
     * the bean is never defined. This test did not run between the move of the suites to the JUnit Platform and its
     * conversion to JUnit 5, and expected the bean to be defined.
     */
    @Test
    public void testDoWithRuntimeConfigurationOfAPluginThatOverridesInvokeMethod() {
        GrailsPlugin versionPlugin3 = new DefaultGrailsPlugin(versioned3, ga);

        RuntimeSpringConfiguration springConfig3 = new DefaultRuntimeSpringConfiguration();
        versionPlugin3.doWithRuntimeConfiguration(springConfig3);

        ApplicationContext ctx3 = springConfig3.getApplicationContext();

        assertFalse(ctx3.containsBean("classEditor"));
    }

    @Test
    public void testGetName() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("my", versionPlugin.getName());
    }

    @Test
    public void testGetVersion() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals("1.1", versionPlugin.getVersion());
    }

    @Test
    public void testObservers() {

        GrailsPlugin observingPlugin = new DefaultGrailsPlugin(observed, ga);

        assertEquals(1, observingPlugin.getObservedPluginNames().length);
        assertEquals("another", observingPlugin.getObservedPluginNames()[0]);
    }

    @Test
    public void testWatchedResources() {
        GrailsPlugin versionPlugin = new DefaultGrailsPlugin(versioned, ga);
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(0).getDirectory().getPath(), "./grails-app/taglib".replace("/", File.separator));
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(1).getDirectory().getPath(), "./grails-app/controller".replace("/", File.separator));
        assertEquals(versionPlugin.getWatchedResourcePatterns().get(2).getDirectory().getPath(), "/absolutePath".replace("/", File.separator));
    }
}
