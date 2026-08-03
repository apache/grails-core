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
package org.apache.grails.benchmarks.views;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import groovy.text.Template;
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
 * Measures rendering of already-created JSON and markup view templates, which is the per-request
 * response path. Regressions here directly delay API and server-rendered view responses.
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class ViewTemplateRenderingBenchmark {

    private Template jsonTemplate;
    private Template markupTemplate;
    private Map<String, Object> jsonModel;
    private Map<String, Object> markupModel;

    @Setup
    public void setup() {
        jsonTemplate = ViewTemplateFixture.createJsonTemplate();
        markupTemplate = ViewTemplateFixture.createMarkupTemplate();
        jsonModel = Map.of("name", "Ada", "count", 42);
        markupModel = Map.of("make", "Audi", "trim", "A5");
    }

    @Benchmark
    public String renderJsonTemplate() throws IOException {
        StringWriter writer = new StringWriter();
        jsonTemplate.make(jsonModel).writeTo(writer);
        return writer.toString();
    }

    @Benchmark
    public String renderMarkupTemplate() throws IOException {
        StringWriter writer = new StringWriter();
        markupTemplate.make(markupModel).writeTo(writer);
        return writer.toString();
    }
}
