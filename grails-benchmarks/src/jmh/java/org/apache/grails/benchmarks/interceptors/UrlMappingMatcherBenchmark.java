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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import grails.web.mapping.UrlMappingData;
import grails.web.mapping.UrlMappingInfo;
import org.grails.plugins.web.interceptors.UrlMappingMatcher;
import org.grails.web.servlet.mvc.GrailsWebRequest;
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

/**
 * Measures URI matcher decisions that select Grails interceptors during request dispatch. Both
 * matching and rejected paths matter because every interceptor evaluates incoming requests.
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class UrlMappingMatcherBenchmark {

    private UrlMappingMatcher matcher;
    private UrlMappingInfo mappingInfo;

    @Setup
    public void setup() {
        matcher = new UrlMappingMatcher(InterceptorFixture.createInterceptor());
        matcher.matches(Map.of("uri", "/orders/**"));
        mappingInfo = new BenchmarkUrlMappingInfo();
    }

    @Benchmark
    public boolean matchUriPattern() {
        return matcher.doesMatch("/orders/42", mappingInfo);
    }

    @Benchmark
    public boolean rejectNonMatchingUriPattern() {
        return matcher.doesMatch("/catalog/42", mappingInfo);
    }

    private static final class BenchmarkUrlMappingInfo implements UrlMappingInfo {
        @Override
        public String getURI() {
            return null;
        }

        @Override
        public String getHttpMethod() {
            return "GET";
        }

        @Override
        public String getVersion() {
            return null;
        }

        @Override
        public String getControllerName() {
            return "orders";
        }

        @Override
        public String getActionName() {
            return "show";
        }

        @Override
        public String getNamespace() {
            return null;
        }

        @Override
        public String getPluginName() {
            return null;
        }

        @Override
        public String getViewName() {
            return null;
        }

        @Override
        public String getId() {
            return "42";
        }

        @Override
        public Map<?, ?> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public void configure(GrailsWebRequest webRequest) {
        }

        @Override
        public boolean isParsingRequest() {
            return false;
        }

        @Override
        public Object getRedirectInfo() {
            return null;
        }

        @Override
        public UrlMappingData getUrlData() {
            return null;
        }
    }
}
