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
package org.apache.grails.benchmarks.gsp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.grails.gsp.compiler.GroovyPageParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures GSP source parsing into generated Groovy source, the work performed when GSP templates
 * are prepared. Parser regressions slow application startup and template reloads.
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class GroovyPageParserBenchmark {

    private static final String SMALL_TEMPLATE = "<div>Hello ${name}</div>";
    private static final String TAGGED_TEMPLATE = """
            <%@ page expressionCodec=\"HTML\" %>
            <section class=\"${cssClass}\">
                <g:link controller=\"book\" action=\"show\" id=\"${bookId}\">${title}</g:link>
                <g:each in=\"${books}\" var=\"book\"><p>${book.name}</p></g:each>
            </section>
            """;

    @Benchmark
    public byte[] parseSmallTemplate() throws IOException {
        return parse("small.gsp", SMALL_TEMPLATE);
    }

    @Benchmark
    public byte[] parseTemplateWithTagsAndExpressions() throws IOException {
        return parse("tagged.gsp", TAGGED_TEMPLATE);
    }

    private byte[] parse(String uri, String template) throws IOException {
        GroovyPageParser parser = new GroovyPageParser(
                uri,
                uri,
                uri,
                new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8.name(),
                "HTML",
                null
        );
        return parser.parse().readAllBytes();
    }
}
