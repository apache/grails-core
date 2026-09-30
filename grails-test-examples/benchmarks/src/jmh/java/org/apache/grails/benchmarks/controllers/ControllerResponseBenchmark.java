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
package org.apache.grails.benchmarks.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import groovy.lang.GroovyClassLoader;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;

import grails.artefact.Controller;
import grails.core.GrailsApplication;
import grails.util.GrailsWebMockUtil;
import grails.web.mapping.LinkGenerator;
import org.apache.grails.benchmarks.web.WebContextFixture;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.web.servlet.view.CompositeViewResolver;
import org.grails.web.util.GrailsApplicationAttributes;

/**
 * Measures the two controller response paths that resolve an application-scoped collaborator on
 * every call: {@code redirect(...)}, which needs the controller's declared namespace and a
 * {@link grails.web.mapping.ResponseRedirector}, and {@code render(template: ...)}, which needs the
 * {@link CompositeViewResolver}.
 *
 * <p>Three shapes are measured:</p>
 * <ul>
 *   <li>{@link #redirectWithoutNamespace()} - a controller declaring no {@code namespace}. Resolving
 *   the namespace reflectively is at its most expensive here, because the field is never found and
 *   the whole class hierarchy is walked.</li>
 *   <li>{@link #redirectWithNamespace()} - a controller declaring {@code static namespace}, where
 *   the reflective lookup terminates on the controller class itself.</li>
 *   <li>{@link #renderTemplate()} - {@code render(template: 'summary')} against a view that renders
 *   nothing, so the measurement is the framework's path to the view rather than the cost of
 *   producing markup.</li>
 * </ul>
 *
 * <p>Both redirect benchmarks allocate a fresh argument map per invocation, exactly as a controller
 * writing {@code redirect(action: 'show')} does. That is not cosmetic: {@code redirect} writes the
 * resolved namespace back into the map it is given, so a shared map would skip namespace resolution
 * on every invocation after the first and measure nothing. They also clear the
 * "redirect already issued" request attribute, since a second redirect on one request is an error.
 * Both costs are constant across the states being compared.</p>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class ControllerResponseBenchmark {

    private static final String PLAIN_CONTROLLER_SOURCE = """
            @grails.artefact.Artefact('Controller')
            class BenchmarkPlainRedirectController {
                def index() {
                    'plain'
                }
            }
            """;

    private static final String NAMESPACED_CONTROLLER_SOURCE = """
            @grails.artefact.Artefact('Controller')
            class BenchmarkNamespacedRedirectController {
                static namespace = 'admin'
                def index() {
                    'namespaced'
                }
            }
            """;

    private static final String TEMPLATE_CONTROLLER_SOURCE = """
            @grails.artefact.Artefact('Controller')
            class BenchmarkTemplateController {
                def index() {
                    'template'
                }
            }
            """;

    private Controller plainController;

    private Controller namespacedController;

    private Controller templateController;

    private MockHttpServletRequest request;

    private CountingView countingView;

    @Setup
    public void setup() throws Exception {
        MockServletContext servletContext = WebContextFixture.createServletContext();
        WebApplicationContext applicationContext = WebContextFixture.applicationContext(servletContext);

        this.countingView = ControllerResponseFixture.createCountingView();
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        beanFactory.registerSingleton(LinkGenerator.BEAN_NAME, ControllerResponseFixture.createLinkGenerator());
        beanFactory.registerSingleton(CompositeViewResolver.BEAN_NAME, ControllerResponseFixture.createViewResolver(this.countingView));

        // The controllers have to be registered as artefacts, exactly as an application's controllers are. A
        // controller that is not in the registry is a shape only a hand-built unit test produces, and measuring it
        // would measure the framework's fallback rather than a request.
        GrailsApplication grailsApplication = applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
        grailsApplication.setApplicationContext(applicationContext);
        grailsApplication.initialise();

        GroovyClassLoader classLoader = ControllerFixture.createTransformingClassLoader();
        this.plainController = newController(grailsApplication, classLoader, PLAIN_CONTROLLER_SOURCE, "BenchmarkPlainRedirectController");
        this.namespacedController = newController(grailsApplication, classLoader, NAMESPACED_CONTROLLER_SOURCE, "BenchmarkNamespacedRedirectController");
        this.templateController = newController(grailsApplication, classLoader, TEMPLATE_CONTROLLER_SOURCE, "BenchmarkTemplateController");

        this.request = new MockHttpServletRequest(servletContext, "GET", "/benchmark/index");
        GrailsWebMockUtil.bindMockWebRequest(applicationContext, this.request, new MockHttpServletResponse());

        assertFixtureRedirects();
        assertFixtureRenders();
    }

    private static Controller newController(GrailsApplication grailsApplication, GroovyClassLoader classLoader,
            String source, String className) throws Exception {
        classLoader.parseClass(source, className + ".groovy");
        Class<?> controllerClass = classLoader.loadClass(className);
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass);
        if (grailsApplication.getArtefact(ControllerArtefactHandler.TYPE, controllerClass.getName()) == null) {
            throw new IllegalStateException("Expected " + className + " to be retrievable from the artefact registry");
        }
        return (Controller) controllerClass.getDeclaredConstructor().newInstance();
    }

    private static Map<String, Object> redirectArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>(4);
        arguments.put("controller", "catalog");
        arguments.put("action", "show");
        return arguments;
    }

    private static Map<String, Object> renderArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>(2);
        arguments.put("template", "summary");
        return arguments;
    }

    /**
     * A redirect is only allowed once per request, so the flag the redirector sets is cleared before
     * every measured redirect. Kept out of the benchmark methods' documentation of what they measure
     * because it is the same map removal in every state being compared.
     */
    private void clearRedirectIssued() {
        this.request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED);
    }

    /**
     * A redirect that resolves no link still returns normally, so setup checks the location header
     * and the resolved namespace rather than publishing a number measured on a path that did nothing.
     */
    private void assertFixtureRedirects() {
        clearRedirectIssued();
        Map<String, Object> plainArguments = redirectArguments();
        this.plainController.redirect(plainArguments);
        require(plainArguments.get("namespace"), null, "plain controller namespace");
        if (this.request.getAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED) == null) {
            throw new IllegalStateException("Expected the redirect to generate a link and mark the request as redirected");
        }

        clearRedirectIssued();
        Map<String, Object> namespacedArguments = redirectArguments();
        this.namespacedController.redirect(namespacedArguments);
        require(namespacedArguments.get("namespace"), "admin", "namespaced controller namespace");

        clearRedirectIssued();
    }

    private void assertFixtureRenders() {
        long before = this.countingView.getRenderCount();
        this.templateController.render(renderArguments());
        if (this.countingView.getRenderCount() != before + 1) {
            throw new IllegalStateException("Expected render(template:) to resolve and render the template view");
        }
    }

    private static void require(Object actual, Object expected, String description) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException("Expected " + description + " to be [" + expected + "] but it was [" + actual + "]");
        }
    }

    /** A redirect issued by a controller that declares no namespace - the common shape. */
    @Benchmark
    public Object redirectWithoutNamespace() {
        clearRedirectIssued();
        Map<String, Object> arguments = redirectArguments();
        this.plainController.redirect(arguments);
        return arguments;
    }

    /** A redirect issued by a controller that declares a namespace. */
    @Benchmark
    public Object redirectWithNamespace() {
        clearRedirectIssued();
        Map<String, Object> arguments = redirectArguments();
        this.namespacedController.redirect(arguments);
        return arguments;
    }

    /** {@code render(template: ...)}, which has to reach the composite view resolver. */
    @Benchmark
    public long renderTemplate() {
        this.templateController.render(renderArguments());
        return this.countingView.getRenderCount();
    }
}
