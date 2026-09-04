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
package org.apache.grails.benchmarks.urlmappings;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

import grails.web.mapping.UrlCreator;
import grails.web.mapping.UrlMappingInfo;
import org.grails.web.mapping.DefaultUrlMappingsHolder;

/**
 * Measures request URI matching and reverse URL creation, which are performed for every routed
 * Grails request and generated link. Cache regressions here directly increase request latency.
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class UrlMappingsBenchmark {

    private static final String WARM_URI = "/catalog/books/42";
    private static final String EXPECTED_REVERSE_URL = "/catalog/books/42";
    private static final int COLD_URI_COUNT = 16_384;

    private DefaultUrlMappingsHolder holder;
    private String[] coldUris;
    private Map<String, Object> reverseParameters;
    private int coldUriIndex;

    @Setup
    public void setup() {
        holder = UrlMappingsFixture.createHolder();
        coldUris = new String[COLD_URI_COUNT];
        for (int index = 0; index < COLD_URI_COUNT; index++) {
            coldUris[index] = "/catalog/books/" + index;
        }
        reverseParameters = new HashMap<>();
        reverseParameters.put("category", "books");
        reverseParameters.put("id", "42");
        assertFixtureMatches();
        holder.match(WARM_URI);
    }

    // A mappings DSL mistake makes match() return null rather than raise, which would leave these
    // benchmarks quietly timing failed lookups. Fail the run instead of publishing wrong numbers.
    private void assertFixtureMatches() {
        UrlMappingInfo info = holder.match(WARM_URI);
        if (info == null) {
            throw new IllegalStateException("URL mappings fixture does not match " + WARM_URI);
        }
        if (!"catalog".equals(info.getControllerName()) || !"show".equals(info.getActionName())) {
            throw new IllegalStateException("Unexpected mapping for " + WARM_URI + ": " + info);
        }
        String reverse = holder.getReverseMapping("catalog", "show", reverseParameters)
                .createRelativeURL("catalog", "show", reverseParameters, "UTF-8");
        if (!EXPECTED_REVERSE_URL.equals(reverse)) {
            throw new IllegalStateException("Unexpected reverse URL: " + reverse);
        }
    }

    @Benchmark
    public UrlMappingInfo matchWarmCache() {
        return holder.match(WARM_URI);
    }

    @Benchmark
    public UrlMappingInfo matchColdVariedKeys() {
        String uri = coldUris[coldUriIndex++ & (COLD_URI_COUNT - 1)];
        return holder.match(uri);
    }

    @Benchmark
    public String reverseMappingAndCreateRelativeUrl() {
        UrlCreator creator = holder.getReverseMapping("catalog", "show", reverseParameters);
        return creator.createRelativeURL("catalog", "show", reverseParameters, "UTF-8");
    }
}
