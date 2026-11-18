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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import org.grails.web.util.WebUtils;

/**
 * Measures {@code WebUtils.resolveMultipartRequest(request)}, which every
 * {@code GrailsParameterMap} construction calls to discover uploaded files.
 *
 * <p>The overwhelmingly common case is a request that is <em>not</em> multipart, so the miss path
 * matters more than the hit path. Both the flat and the wrapped shapes are measured: in a real
 * application the Grails request filter sits under several servlet filters, so the request handed
 * to Grails is normally a couple of {@code HttpServletRequestWrapper}s deep.</p>
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class MultipartResolutionBenchmark {

    private HttpServletRequest plain;

    private HttpServletRequest plainBehindTwoWrappers;

    private HttpServletRequest multipartBehindTwoWrappers;

    private HttpServletRequest multipartByAttribute;

    @Setup
    public void setup() {
        ServletContext servletContext = WebContextFixture.createServletContext();

        MockHttpServletRequest plainRequest = new MockHttpServletRequest(servletContext, "POST", "/book/save");
        plainRequest.setContentType("application/x-www-form-urlencoded");
        plain = plainRequest;
        plainBehindTwoWrappers = wrapTwice(plainRequest);

        MockMultipartHttpServletRequest multipartRequest = new MockMultipartHttpServletRequest(servletContext);
        multipartRequest.setMethod("POST");
        multipartRequest.setRequestURI("/book/save");
        multipartRequest.addFile(new MockMultipartFile("cover", "cover.png", "image/png",
                "not-really-a-png".getBytes(StandardCharsets.UTF_8)));
        multipartBehindTwoWrappers = wrapTwice(multipartRequest);

        // Spring's StandardServletMultipartResolver publishes the resolved multipart request as a
        // request attribute rather than as a wrapper, so the attribute fallback is a real path too.
        MockHttpServletRequest attributeCarrier = new MockHttpServletRequest(servletContext, "POST", "/book/save");
        attributeCarrier.setAttribute(MultipartHttpServletRequest.class.getName(), multipartRequest);
        multipartByAttribute = attributeCarrier;

        assertFixtureResolves();
    }

    // A resolution that silently found nothing would leave the multipart benchmarks timing the
    // miss path under a name that claims a hit.
    private void assertFixtureResolves() {
        if (WebUtils.resolveMultipartRequest(plain) != null) {
            throw new IllegalStateException("A plain request must not resolve to a multipart request");
        }
        if (WebUtils.resolveMultipartRequest(multipartBehindTwoWrappers) == null) {
            throw new IllegalStateException("A wrapped multipart request must resolve through the wrappers");
        }
        if (WebUtils.resolveMultipartRequest(multipartByAttribute) == null) {
            throw new IllegalStateException("A multipart request published as an attribute must resolve");
        }
    }

    private static HttpServletRequest wrapTwice(HttpServletRequest request) {
        return new HttpServletRequestWrapper(new HttpServletRequestWrapper(request));
    }

    @Benchmark
    public MultipartHttpServletRequest resolvePlain() {
        return WebUtils.resolveMultipartRequest(plain);
    }

    @Benchmark
    public MultipartHttpServletRequest resolvePlainBehindTwoWrappers() {
        return WebUtils.resolveMultipartRequest(plainBehindTwoWrappers);
    }

    @Benchmark
    public MultipartHttpServletRequest resolveMultipartBehindTwoWrappers() {
        return WebUtils.resolveMultipartRequest(multipartBehindTwoWrappers);
    }

    @Benchmark
    public MultipartHttpServletRequest resolveMultipartByAttribute() {
        return WebUtils.resolveMultipartRequest(multipartByAttribute);
    }
}
