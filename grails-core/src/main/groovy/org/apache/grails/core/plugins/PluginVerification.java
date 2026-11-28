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
package org.apache.grails.core.plugins;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import grails.plugins.exceptions.PluginException;
import grails.util.Environment;
import org.grails.config.yaml.YamlPropertySourceLoader;
import org.grails.core.cfg.GroovyConfigPropertySourceLoader;

/**
 * Checks the plugins of an application the way it checks them when it starts, so a build can fail on what
 * would stop the application: a plugin built for an earlier Grails, a broken descriptor, an unmet
 * {@code dependsOn}, a plugin order that is circular.
 *
 * <p>Each environment is checked as the application starts in it: the plugins that support it, filtered by
 * the {@code grails.plugin.includes} or {@code grails.plugin.excludes} of the application's
 * {@code application.yml} and {@code application.groovy}, with their settings for that environment. The
 * Grails Gradle plugin runs it, in a JVM of the project's toolchain, with the application's runtime class
 * path.</p>
 *
 * @since 9.0
 */
public final class PluginVerification {

    private static final String[] CONFIGURATION_FILES = {"application.yml", "application.groovy"};

    private PluginVerification() {
    }

    /**
     * Checks the plugins on a class path in one environment.
     *
     * @param classLoader the class loader of the application's class path
     * @param environmentName the Grails environment, such as {@code production}
     * @return the names of the plugins the application loads in that environment, in plugin order
     * @throws PluginException if the application would not start because of its plugins
     */
    public static List<String> verify(ClassLoader classLoader, String environmentName) {
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        String previousEnvironment = System.getProperty(Environment.KEY);
        thread.setContextClassLoader(classLoader);
        System.setProperty(Environment.KEY, environmentName);
        try {
            DefaultPluginDiscovery discovery = new DefaultPluginDiscovery();
            discovery.init(configuration(classLoader));
            return discovery.getPluginsInTopologicalOrder().stream().map(PluginInfo::getName).toList();
        }
        finally {
            if (previousEnvironment == null) {
                System.clearProperty(Environment.KEY);
            }
            else {
                System.setProperty(Environment.KEY, previousEnvironment);
            }
            thread.setContextClassLoader(previousLoader);
        }
    }

    /**
     * Checks the plugins on this JVM's class path in each of the given environments.
     *
     * @param classLoader the class loader of the application's class path
     * @param environmentNames the Grails environments to check
     * @return the plugin order of each environment, or {@code null} for one whose plugins stop the application
     * @throws PluginException naming every environment and why its plugins stop the application, if any does
     */
    public static Map<String, List<String>> verify(ClassLoader classLoader, List<String> environmentNames) {
        Map<String, List<String>> orders = new LinkedHashMap<>();
        Map<String, List<String>> environmentsByFailure = new LinkedHashMap<>();
        for (String environmentName : environmentNames) {
            try {
                orders.put(environmentName, verify(classLoader, environmentName));
            }
            catch (PluginException e) {
                environmentsByFailure.computeIfAbsent(e.getMessage(), message -> new ArrayList<>()).add(environmentName);
            }
        }
        if (!environmentsByFailure.isEmpty()) {
            StringBuilder message = new StringBuilder();
            environmentsByFailure.forEach((failure, environments) -> {
                if (!message.isEmpty()) {
                    message.append(System.lineSeparator()).append(System.lineSeparator());
                }
                message.append("In the ").append(String.join(", ", environments))
                        .append(environments.size() == 1 ? " environment: " : " environments: ")
                        .append(failure);
            });
            throw new PluginException(message.toString());
        }
        return orders;
    }

    /**
     * Run by the Grails Gradle plugin: {@code <report file> <failure file> <environment>...}. Checks the class
     * path of the thread's context class loader, the application class path of the JVM it starts, and writes the
     * plugin order of each environment to the report, or why the plugins stop the application to the failure
     * file.
     *
     * @param args the report file, the failure file and the environments to check
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: PluginVerification <report file> <failure file> <environment>...");
        }
        Path report = Path.of(args[0]);
        Path failure = Path.of(args[1]);
        List<String> environments = List.of(args).subList(2, args.length);
        try {
            Files.deleteIfExists(failure);
            StringBuilder text = new StringBuilder();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            verify(classLoader != null ? classLoader : PluginVerification.class.getClassLoader(), environments).forEach((environment, order) ->
                    text.append(environment).append(": ").append(String.join(", ", order)).append('\n'));
            Files.writeString(report, text, StandardCharsets.UTF_8);
        }
        catch (PluginException e) {
            write(failure, e.getMessage());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static StandardEnvironment configuration(ClassLoader classLoader) {
        StandardEnvironment environment = new StandardEnvironment();
        for (String file : CONFIGURATION_FILES) {
            Resource resource = new ClassPathResource(file, classLoader);
            if (!resource.exists()) {
                continue;
            }
            PropertySourceLoader loader = file.endsWith(".yml") ? new YamlPropertySourceLoader() :
                    new GroovyConfigPropertySourceLoader();
            try {
                for (PropertySource<?> source : loader.load(file, resource)) {
                    environment.getPropertySources().addLast(source);
                }
            }
            catch (IOException e) {
                throw new PluginException("Could not read the application configuration " + file, e);
            }
        }
        return environment;
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
