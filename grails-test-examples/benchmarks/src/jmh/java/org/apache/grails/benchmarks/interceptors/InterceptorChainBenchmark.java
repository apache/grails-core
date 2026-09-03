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
package org.apache.grails.benchmarks.interceptors;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContext;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
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

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import grails.artefact.Interceptor;
import org.apache.grails.benchmarks.web.WebContextFixture;
import org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter;

/**
 * Measures the {@code preHandle} + {@code postHandle} pair of
 * {@code GrailsInterceptorHandlerInterceptorAdapter}, which every request with at least one
 * interceptor bean runs through twice - once before the handler and once after.
 *
 * <p>The dominant production configuration is a no-op {@code ObservationRegistry}: an application
 * that has not enabled metrics or tracing gets {@code ObservationRegistry.NOOP}, and even one that
 * has, gets a registry that is no-op until a handler is registered. The observing variants are
 * measured too, but the no-op numbers are the ones that describe most applications.</p>
 *
 * <p>The interceptors leave {@code before()} and {@code after()} at their trait defaults, so the
 * score is the adapter's own per-interceptor per-phase cost - matcher evaluation, list bookkeeping,
 * and whatever the adapter allocates to dispatch the callback - and not the cost of anybody's
 * interceptor body.</p>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class InterceptorChainBenchmark {

    private static final String MATCHED_INTERCEPTORS = "org.grails.web.MATCHED_INTERCEPTORS";

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private ModelAndView modelAndView;

    private Object handler;

    private GrailsInterceptorHandlerInterceptorAdapter oneNoOp;

    private GrailsInterceptorHandlerInterceptorAdapter threeNoOp;

    private GrailsInterceptorHandlerInterceptorAdapter oneObserving;

    private GrailsInterceptorHandlerInterceptorAdapter threeObserving;

    @Setup
    public void setup() {
        ServletContext servletContext = WebContextFixture.createServletContext();
        request = new MockHttpServletRequest(servletContext, "GET", "/book/show/42");
        response = new MockHttpServletResponse();
        modelAndView = new ModelAndView("/book/show");
        handler = new Object();

        oneNoOp = createAdapter(1, ObservationRegistry.NOOP);
        threeNoOp = createAdapter(3, ObservationRegistry.NOOP);
        oneObserving = createAdapter(1, createObservingRegistry());
        threeObserving = createAdapter(3, createObservingRegistry());

        assertFixtureMatches(oneNoOp, 1);
        assertFixtureMatches(threeNoOp, 3);
        assertFixtureMatches(oneObserving, 1);
        assertFixtureMatches(threeObserving, 3);
    }

    private static GrailsInterceptorHandlerInterceptorAdapter createAdapter(int count, ObservationRegistry registry) {
        GrailsInterceptorHandlerInterceptorAdapter adapter = new GrailsInterceptorHandlerInterceptorAdapter();
        Interceptor[] interceptors = InterceptorChainFixture.createMatchingInterceptors(count);
        adapter.setInterceptors(interceptors);
        adapter.setObservationRegistry(registry);
        return adapter;
    }

    /**
     * @return a registry that is not no-op, because a handler is registered on it - the shape an
     * application running Micrometer tracing or metrics has
     */
    private static ObservationRegistry createObservingRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        if (registry.isNoop()) {
            throw new IllegalStateException("The observing registry reports itself as no-op");
        }
        return registry;
    }

    // An interceptor whose matcher rejects the request is skipped silently, which would leave the
    // benchmark timing an empty chain under a name that claims one, three interceptors.
    private void assertFixtureMatches(GrailsInterceptorHandlerInterceptorAdapter adapter, int expected) {
        try {
            adapter.preHandle(request, response, handler);
        }
        catch (Exception e) {
            throw new IllegalStateException("preHandle threw during setup", e);
        }
        Object matched = request.getAttribute(MATCHED_INTERCEPTORS);
        int size = matched instanceof List ? ((List<?>) matched).size() : -1;
        if (size != expected) {
            throw new IllegalStateException("Expected " + expected + " matched interceptors but got " + size);
        }
    }

    /** One matched interceptor, no-op observation registry - the dominant production shape. */
    @Benchmark
    public boolean oneInterceptorNoOpRegistry() throws Exception {
        boolean proceed = oneNoOp.preHandle(request, response, handler);
        oneNoOp.postHandle(request, response, handler, modelAndView);
        return proceed;
    }

    /** Three matched interceptors, no-op observation registry. */
    @Benchmark
    public boolean threeInterceptorsNoOpRegistry() throws Exception {
        boolean proceed = threeNoOp.preHandle(request, response, handler);
        threeNoOp.postHandle(request, response, handler, modelAndView);
        return proceed;
    }

    /** One matched interceptor, with a registry that actually records observations. */
    @Benchmark
    public boolean oneInterceptorObservingRegistry() throws Exception {
        boolean proceed = oneObserving.preHandle(request, response, handler);
        oneObserving.postHandle(request, response, handler, modelAndView);
        return proceed;
    }

    /** Three matched interceptors, with a registry that actually records observations. */
    @Benchmark
    public boolean threeInterceptorsObservingRegistry() throws Exception {
        boolean proceed = threeObserving.preHandle(request, response, handler);
        threeObserving.postHandle(request, response, handler, modelAndView);
        return proceed;
    }
}
