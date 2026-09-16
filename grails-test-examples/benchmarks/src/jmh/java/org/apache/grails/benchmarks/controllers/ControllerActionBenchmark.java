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

import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.util.Environment;
import grails.util.GrailsWebMockUtil;
import grails.web.databinding.DataBindingUtils;
import grails.web.databinding.GrailsWebDataBinder;
import grails.web.mime.MimeTypeResolver;
import org.apache.grails.benchmarks.web.WebContextFixture;
import org.grails.core.DefaultGrailsControllerClass;
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry;
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry;
import org.grails.web.mime.DefaultMimeTypeResolver;

/**
 * Measures one controller action invocation through {@code GrailsControllerClass.invoke}, which is
 * the call {@code UrlMappingsInfoHandlerAdapter} makes for every request that reaches a controller.
 *
 * <p>Three shapes are measured, because the generated wrapper differs between them:</p>
 * <ul>
 *   <li>{@link #plainAction()} - a controller that declares no {@code allowedMethods} at all. This
 *   is the overwhelmingly common shape.</li>
 *   <li>{@link #restrictedAction()} - a controller that declares {@code allowedMethods}, where the
 *   bookkeeping and the {@code AllowedMethodsHelper.isAllowed} check both have to run.</li>
 *   <li>{@link #commandObjectAction()} - an action taking a command object, whose generated no-arg
 *   wrapper instantiates and data-binds the command object.</li>
 * </ul>
 *
 * <p>The controllers are compiled at setup by a {@code GrailsAwareClassLoader} running the real
 * {@code ControllerActionTransformer}, so the bytecode invoked is the bytecode a Grails application
 * would run. Setup also prints, once per fork, how many request-attribute operations one invocation
 * of each action performs, measured against a counting request outside the timed region. That count
 * - not the timing - is the direct evidence of what the generated code does.</p>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class ControllerActionBenchmark {

    private static final String PLAIN_CONTROLLER_SOURCE = """
            @grails.artefact.Artefact('Controller')
            class BenchmarkPlainController {
                def index() {
                    'plain'
                }
            }
            """;

    private static final String RESTRICTED_CONTROLLER_SOURCE = """
            @grails.artefact.Artefact('Controller')
            class BenchmarkRestrictedController {
                static allowedMethods = [index: 'GET']
                def index() {
                    'restricted'
                }
            }
            """;

    private static final String COMMAND_CONTROLLER_SOURCE = """
            class BenchmarkBookCommand {
                String title
                Integer pages
            }

            @grails.artefact.Artefact('Controller')
            class BenchmarkCommandController {
                def save(BenchmarkBookCommand command) {
                    'saved'
                }
            }
            """;

    private GrailsControllerClass plainControllerClass;

    private Object plainController;

    private GrailsControllerClass restrictedControllerClass;

    private Object restrictedController;

    private GrailsControllerClass commandControllerClass;

    private Object commandController;

    @Setup
    public void setup() throws Throwable {
        MockServletContext servletContext = WebContextFixture.createServletContext();
        WebApplicationContext applicationContext = WebContextFixture.applicationContext(servletContext);
        registerDataBindingBeans(applicationContext);

        GroovyClassLoader classLoader = ControllerFixture.createTransformingClassLoader();

        classLoader.parseClass(PLAIN_CONTROLLER_SOURCE, "BenchmarkPlainController.groovy");
        Class<?> plainClass = classLoader.loadClass("BenchmarkPlainController");
        plainControllerClass = new DefaultGrailsControllerClass(plainClass);
        plainController = plainClass.getDeclaredConstructor().newInstance();

        classLoader.parseClass(RESTRICTED_CONTROLLER_SOURCE, "BenchmarkRestrictedController.groovy");
        Class<?> restrictedClass = classLoader.loadClass("BenchmarkRestrictedController");
        restrictedControllerClass = new DefaultGrailsControllerClass(restrictedClass);
        restrictedController = restrictedClass.getDeclaredConstructor().newInstance();

        classLoader.parseClass(COMMAND_CONTROLLER_SOURCE, "BenchmarkCommandController.groovy");
        Class<?> commandClass = classLoader.loadClass("BenchmarkCommandController");
        commandControllerClass = new DefaultGrailsControllerClass(commandClass);
        commandController = commandClass.getDeclaredConstructor().newInstance();

        reportAttributeCounts(servletContext, applicationContext);

        // The request all three measured invocations run against. GET, which is what the restricted
        // controller's allowedMethods declares, so that benchmark measures the check passing rather
        // than the 405 error path. Two request parameters are present so the command object action
        // has something to bind.
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/benchmark/index");
        request.setParameter("title", "Groovy in Action");
        request.setParameter("pages", "912");
        GrailsWebMockUtil.bindMockWebRequest(applicationContext, request, new MockHttpServletResponse());

        assertFixtureInvokes();

        System.out.println("[fixture] Environment.isDevelopmentMode()=" + Environment.isDevelopmentMode() +
                " (false means GrailsControllerClass.invoke dispatches through a MethodHandle, as in production)");
    }

    /**
     * Registers the data binding collaborators an application context normally holds as singletons,
     * and points the {@code GrailsApplication} at that context so they are found.
     *
     * <p>Without this, {@code DataBindingUtils} cannot find a {@code grailsWebDataBinder} bean and
     * builds a whole {@code GrailsWebDataBinder} - conversion service and all - on every single
     * command object binding, which costs tens of microseconds and is nothing like what a running
     * application does.</p>
     */
    private static void registerDataBindingBeans(WebApplicationContext applicationContext) {
        GrailsApplication grailsApplication = applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
        grailsApplication.setApplicationContext(applicationContext);

        DefaultDataBindingSourceRegistry dataBindingSourceRegistry = new DefaultDataBindingSourceRegistry();
        dataBindingSourceRegistry.initialize();

        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        beanFactory.registerSingleton(DataBindingSourceRegistry.BEAN_NAME, dataBindingSourceRegistry);
        beanFactory.registerSingleton(MimeTypeResolver.BEAN_NAME, new DefaultMimeTypeResolver());
        beanFactory.registerSingleton(DataBindingUtils.DATA_BINDER_BEAN_NAME, new GrailsWebDataBinder(grailsApplication));
    }

    /**
     * Invokes each action once against a request that counts attribute operations, and prints the
     * counts. Runs before the measured request is bound, and never inside the timed region.
     */
    private void reportAttributeCounts(MockServletContext servletContext, WebApplicationContext applicationContext) throws Throwable {
        AttributeCountingRequest countingRequest = ControllerFixture.createCountingRequest(servletContext, "GET", "/benchmark/index");
        countingRequest.setParameter("title", "Groovy in Action");
        countingRequest.setParameter("pages", "912");
        GrailsWebMockUtil.bindMockWebRequest(applicationContext, countingRequest, new MockHttpServletResponse());

        // The first invocation of an action initialises metaclasses and caches, which does its own
        // attribute traffic; the reported counts are from the second, steady state, invocation.
        plainControllerClass.invoke(plainController, "index");
        countingRequest.resetCounts();
        plainControllerClass.invoke(plainController, "index");
        System.out.println("[fixture] plainAction request attribute ops: " + countingRequest.describeCounts());

        restrictedControllerClass.invoke(restrictedController, "index");
        countingRequest.resetCounts();
        restrictedControllerClass.invoke(restrictedController, "index");
        System.out.println("[fixture] restrictedAction request attribute ops: " + countingRequest.describeCounts());

        commandControllerClass.invoke(commandController, "save");
        countingRequest.resetCounts();
        commandControllerClass.invoke(commandController, "save");
        System.out.println("[fixture] commandObjectAction request attribute ops: " + countingRequest.describeCounts());
    }

    // An action that fails - a rejected HTTP method, say - still returns, so check the return value
    // rather than publishing a number measured on an error path.
    private void assertFixtureInvokes() throws Throwable {
        require(plainControllerClass.invoke(plainController, "index"), "plain");
        require(restrictedControllerClass.invoke(restrictedController, "index"), "restricted");
        require(commandControllerClass.invoke(commandController, "save"), "saved");
    }

    private static void require(Object actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected the action to return '" + expected + "' but it returned '" + actual + "'");
        }
    }

    /** An action on a controller declaring no {@code allowedMethods} - the common case. */
    @Benchmark
    public Object plainAction() throws Throwable {
        return plainControllerClass.invoke(plainController, "index");
    }

    /** An action on a controller declaring {@code allowedMethods}, where the check has to run. */
    @Benchmark
    public Object restrictedAction() throws Throwable {
        return restrictedControllerClass.invoke(restrictedController, "index");
    }

    /** An action taking a command object, so the generated wrapper binds one per invocation. */
    @Benchmark
    public Object commandObjectAction() throws Throwable {
        return commandControllerClass.invoke(commandController, "save");
    }
}
