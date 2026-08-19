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
package org.apache.grails.testing.http.client.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonOutput
import groovy.transform.CompileStatic

/**
 * App-level HTTP microbench helper that emits JMH-compatible JSON so results can be compared
 * with {@code :grails-benchmarks:jmhCompare} using the same methodology as the framework JMH suite.
 *
 * <p>Measurement model (deliberately simple and reproducible on one machine):
 * <ul>
 *   <li>warm up the full Spring Boot stack with {@code warmup} requests (discarded)</li>
 *   <li>collect {@code samples} timed requests as one raw series</li>
 *   <li>split the series into {@code forks} equal chunks to mimic JMH multi-fork rawData shape</li>
 *   <li>report mean ns/op with a simple standard-error-based scoreError</li>
 * </ul>
 *
 * <p>Enable gated specs with {@code -PappBench=true}. Optional properties:
 * {@code appBenchWarmup}, {@code appBenchSamples}, {@code appBenchForks}, {@code appBenchOut}.
 */
@CompileStatic
final class AppHttpBench {

    private AppHttpBench() {
    }

    static boolean enabled() {
        Boolean.getBoolean('app.bench') || Boolean.parseBoolean(System.getProperty('appBench', 'false'))
    }

    static int warmupCount() {
        Integer.getInteger('app.bench.warmup', Integer.getInteger('appBenchWarmup', 200))
    }

    static int sampleCount() {
        Integer.getInteger('app.bench.samples', Integer.getInteger('appBenchSamples', 1000))
    }

    static int forkCount() {
        Integer.getInteger('app.bench.forks', Integer.getInteger('appBenchForks', 2))
    }

    static Path outputPath(String defaultFileName) {
        String configured = System.getProperty('app.bench.out', System.getProperty('appBenchOut', ''))
        if (configured) {
            return Paths.get(configured)
        }
        Path dir = Paths.get('build', 'app-bench')
        Files.createDirectories(dir)
        return dir.resolve(defaultFileName)
    }

    /**
     * Time a single request body. The closure must perform the HTTP call and assert success.
     *
     * @return elapsed nanoseconds
     */
    static long timeNanos(Closure<?> request) {
        long start = System.nanoTime()
        request.call()
        return System.nanoTime() - start
    }

    /**
     * Warm up, sample, and append one JMH-shaped benchmark entry to {@code out}.
     *
     * @param benchmark fully-qualified-style name, e.g. {@code appbench.latency.FastPing.httpGet}
     * @param request closure that performs one successful request
     */
    static void measureAndWrite(String benchmark, Path out, Closure<?> request) {
        int warmup = Math.max(0, warmupCount())
        int samples = sampleCount()
        if (samples < 1) {
            throw new IllegalArgumentException("app.bench.samples must be >= 1, was ${samples}")
        }

        for (int i = 0; i < warmup; i++) {
            request.call()
        }

        double[] values = new double[samples]
        for (int i = 0; i < samples; i++) {
            values[i] = (double) timeNanos(request)
        }

        Map<String, Object> entry = toJmhEntry(benchmark, values, forkCount())
        appendEntry(out, entry)
    }

    static Map<String, Object> toJmhEntry(String benchmark, double[] values, int forks) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException('values must contain at least one sample')
        }
        double mean = mean(values)
        double stdev = stdev(values, mean)
        double scoreError = stdev * 1.96d / Math.sqrt((double) values.length)

        int forkCount = Math.min(Math.max(1, forks), values.length)
        int perFork = Math.max(1, values.length.intdiv(forkCount))
        List<List<Double>> rawData = new ArrayList<>(forkCount)
        int offset = 0
        for (int f = 0; f < forkCount; f++) {
            int end = (f == forkCount - 1) ? values.length : Math.min(values.length, offset + perFork)
            List<Double> chunk = new ArrayList<>(Math.max(0, end - offset))
            for (int i = offset; i < end; i++) {
                chunk.add(values[i])
            }
            rawData.add(chunk)
            offset = end
        }

        Map<String, Object> percentiles = new LinkedHashMap<>()
        double[] sorted = Arrays.copyOf(values, values.length)
        Arrays.sort(sorted)
        percentiles.put('0.0', sorted[0])
        percentiles.put('50.0', percentile(sorted, 0.50d))
        percentiles.put('90.0', percentile(sorted, 0.90d))
        percentiles.put('95.0', percentile(sorted, 0.95d))
        percentiles.put('99.0', percentile(sorted, 0.99d))
        percentiles.put('100.0', sorted[sorted.length - 1])

        Map<String, Object> primary = new LinkedHashMap<>()
        primary.put('score', mean)
        primary.put('scoreError', scoreError)
        primary.put('scoreConfidence', [mean - scoreError, mean + scoreError])
        primary.put('scorePercentiles', percentiles)
        primary.put('scoreUnit', 'ns/op')
        primary.put('rawData', rawData)

        Map<String, Object> entry = new LinkedHashMap<>()
        entry.put('jmhVersion', 'app-bench-1.0')
        entry.put('benchmark', benchmark)
        entry.put('mode', 'avgt')
        entry.put('threads', 1)
        entry.put('forks', forkCount)
        entry.put('jdkVersion', System.getProperty('java.version', 'unknown'))
        entry.put('vmName', System.getProperty('java.vm.name', 'unknown'))
        entry.put('vmVersion', System.getProperty('java.vm.version', 'unknown'))
        entry.put('warmupIterations', 1)
        entry.put('warmupTime', "${warmupCount()} reqs")
        entry.put('measurementIterations', values.length)
        entry.put('measurementTime', '1 req')
        entry.put('primaryMetric', primary)
        entry.put('secondaryMetrics', Collections.emptyMap())
        return entry
    }

    static void appendEntry(Path out, Map<String, Object> entry) {
        List<Object> entries = new ArrayList<>()
        if (Files.exists(out)) {
            String existing = Files.readString(out, StandardCharsets.UTF_8).trim()
            if (existing.startsWith('[')) {
                Object parsed = new groovy.json.JsonSlurper().parseText(existing)
                if (parsed instanceof List) {
                    entries.addAll((List) parsed)
                }
            }
        }
        entries.add(entry)
        Path parent = out.getParent()
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(out, JsonOutput.prettyPrint(JsonOutput.toJson(entries)), StandardCharsets.UTF_8)
    }

    private static double mean(double[] values) {
        double sum = 0d
        for (double value : values) {
            sum += value
        }
        return sum / (double) values.length
    }

    private static double stdev(double[] values, double mean) {
        if (values.length < 2) {
            return 0d
        }
        double sumSq = 0d
        for (double value : values) {
            double delta = value - mean
            sumSq += delta * delta
        }
        return Math.sqrt(sumSq / (double) (values.length - 1))
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0]
        }
        double rank = p * (double) (sorted.length - 1)
        int low = (int) Math.floor(rank)
        int high = (int) Math.ceil(rank)
        if (low == high) {
            return sorted[low]
        }
        double weight = rank - (double) low
        return sorted[low] * (1d - weight) + sorted[high] * weight
    }
}
