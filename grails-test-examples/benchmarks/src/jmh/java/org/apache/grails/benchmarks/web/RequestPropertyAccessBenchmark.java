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
import jakarta.servlet.http.HttpServletRequest;

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

/**
 * Measures Groovy property access on an {@code HttpServletRequest}, which application code performs
 * on every request that reads a request attribute.
 *
 * <p>{@code request.someAttribute} in application code is not a field read: the property is unknown
 * to the request class, so it goes through the metaclass, falls through to the {@code getProperty}
 * / {@code propertyMissing} methods contributed by {@code HttpServletRequestExtension} (registered
 * as a Groovy extension module by {@code grails-web-core}), and that implementation performs a
 * further {@code metaClass.getMetaProperty(name)} lookup before reading the request attribute.</p>
 *
 * <p>{@link #groovyAttributeCall()} and {@link #javaGetAttribute()} bracket that cost with the
 * explicit {@code getAttribute} call from Groovy and from Java respectively.</p>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class RequestPropertyAccessBenchmark {

    private HttpServletRequest request;

    private DynamicRequestPropertyReader reader;

    @Setup
    public void setup() {
        ServletContext servletContext = WebContextFixture.createServletContext();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest(servletContext, "GET", "/book/show/42");
        mockRequest.setAttribute("someAttribute", "someValue");
        request = mockRequest;
        reader = RequestPropertyFixture.createReader();
        assertFixtureResolves();
    }

    // Without the extension module on the classpath the unknown property raises rather than
    // resolving, so check it here instead of publishing a number for the wrong path.
    private void assertFixtureResolves() {
        if (!"someValue".equals(reader.readUnknownProperty(request))) {
            throw new IllegalStateException(
                    "request.someAttribute did not resolve through HttpServletRequestExtension");
        }
    }

    /** {@code request.someAttribute} - metaclass miss, extension {@code getProperty}, attribute read. */
    @Benchmark
    public Object groovyUnknownProperty() {
        return reader.readUnknownProperty(request);
    }

    /** {@code request.method} - metaclass hit on a real getter. */
    @Benchmark
    public Object groovyGetterBackedProperty() {
        return reader.readGetterBackedProperty(request);
    }

    /** {@code request.getAttribute('someAttribute')} from Groovy - a dynamic call, not a property. */
    @Benchmark
    public Object groovyAttributeCall() {
        return reader.readAttributeDirectly(request);
    }

    /** The floor: the same attribute read straight from Java. */
    @Benchmark
    public Object javaGetAttribute() {
        return request.getAttribute("someAttribute");
    }
}
