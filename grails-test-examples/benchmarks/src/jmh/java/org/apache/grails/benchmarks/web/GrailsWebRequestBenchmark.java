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
package org.apache.grails.benchmarks.web;

import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContext;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;

import grails.web.servlet.mvc.GrailsParameterMap;
import org.grails.web.servlet.mvc.GrailsWebRequest;

/**
 * Measures the per-request cost of binding a Grails request, which every request pays before any
 * application code runs.
 *
 * <ul>
 *   <li>{@link #construct()} - {@code new GrailsWebRequest(request, response, servletContext)},
 *       the whole per-request bind including however the application attributes are obtained.</li>
 *   <li>{@link #paramsOnFreshRequest()} - construction plus the first {@code getParams()}, i.e.
 *       what a controller action actually pays the first time it touches {@code params}.</li>
 *   <li>{@link #paramsCached()} - the memoised {@code getParams()} fast path.</li>
 *   <li>{@link #paramsRebuilt()} - {@code resetParams()} plus {@code getParams()}, which isolates
 *       the deep clone of the already-built {@code GrailsParameterMap}.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class GrailsWebRequestBenchmark {

    private ServletContext servletContext;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private GrailsWebRequest webRequest;

    @Setup
    public void setup() {
        servletContext = WebContextFixture.createServletContext();
        request = new MockHttpServletRequest(servletContext, "GET", "/book/show/42");
        request.setContextPath("");
        // A query string representative of a real controller request: a handful of flat parameters
        // plus a nested one, so that GrailsParameterMap builds (and later deep clones) a nested map
        // rather than a flat one.
        request.addParameter("q", "groovy");
        request.addParameter("format", "json");
        request.addParameter("offset", "0");
        request.addParameter("max", "20");
        request.addParameter("sort", "dateCreated");
        request.addParameter("order", "desc");
        request.addParameter("author.name", "Rocher");
        request.addParameter("author.email", "rocher@example.com");
        response = new MockHttpServletResponse();

        webRequest = new GrailsWebRequest(request, response, servletContext);
        assertFixtureBinds();
    }

    // A missing application context makes GrailsWebRequest take an error path rather than the
    // normal one, which would leave these benchmarks timing the wrong code.
    private void assertFixtureBinds() {
        GrailsParameterMap params = webRequest.getParams();
        if (!"groovy".equals(params.get("q"))) {
            throw new IllegalStateException("Request parameters did not bind: " + params);
        }
    }

    @TearDown
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Benchmark
    public GrailsWebRequest construct() {
        return new GrailsWebRequest(request, response, servletContext);
    }

    @Benchmark
    public GrailsParameterMap paramsOnFreshRequest() {
        return new GrailsWebRequest(request, response, servletContext).getParams();
    }

    @Benchmark
    public GrailsParameterMap paramsCached() {
        return webRequest.getParams();
    }

    @Benchmark
    public GrailsParameterMap paramsRebuilt() {
        webRequest.resetParams();
        return webRequest.getParams();
    }
}
