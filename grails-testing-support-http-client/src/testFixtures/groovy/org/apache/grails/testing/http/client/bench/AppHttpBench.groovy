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
import groovy.json.JsonException
import groovy.transform.CompileStatic

/**
 * App-level HTTP microbench helper that emits JMH-compatible JSON so results can be compared
 * with {@code :grails-benchmarks:jmhCompare} using the same methodology as the framework JMH suite.
 *
 * <p>This type lives in test fixtures, not the published main API of grails-testing-support-http-client.
 *
 * <p>Measurement model:
 * <ul>
 *   <li>warm up the full Spring Boot stack with {@code warmup} requests (discarded)</li>
 *   <li>collect {@code samples} timed requests as one raw series</li>
 *   <li>split the series into {@code forks} equal chunks and report a t-interval over fork means</li>
 *   <li>also emit a ruler-equivalent tight loop so runner-health gates apply</li>
 * </ul>
 *
 * <p>Enable gated specs with {@code -PappBench=true}. Optional properties:
 * {@code appBenchWarmup}, {@code appBenchSamples}, {@code appBenchForks},
 * {@code appBenchOut}, {@code appBenchOutDir}.
 */
@CompileStatic
final class AppHttpBench {

    static final String RULER_BENCHMARK = 'org.apache.grails.benchmarks.ruler.AppBenchCpu.measure'

    private AppHttpBench() {
    }

    static boolean enabled() {
        Boolean.getBoolean('app.bench') || Boolean.parseBoolean(System.getProperty('appBench', 'false'))
    }

    static int warmupCount() {
        intProperty('app.bench.warmup', 'appBenchWarmup', 200, true)
    }

    static int sampleCount() {
        intProperty('app.bench.samples', 'appBenchSamples', 1000, false)
    }

    static int forkCount() {
        intProperty('app.bench.forks', 'appBenchForks', 2, false)
    }

    static Path outputPath(String defaultFileName) {
        String configured = System.getProperty('app.bench.out', System.getProperty('appBenchOut', ''))
        if (configured) {
            return Paths.get(configured)
        }
        String directory = System.getProperty('app.bench.out.dir', System.getProperty('appBenchOutDir', ''))
        Path dir = directory ? Paths.get(directory) : Paths.get('build', 'app-bench')
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
     * Warm up, sample, and replace {@code out} with a JMH-shaped array containing the HTTP
     * benchmark and a ruler-equivalent entry.
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

        int forks = forkCount()
        writeEntries(out, [
                toJmhEntry(benchmark, values, forks),
                toJmhEntry(RULER_BENCHMARK, rulerSamples(samples, warmup), forks)
        ])
    }

    static Map<String, Object> toJmhEntry(String benchmark, double[] values, int forks) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException('values must contain at least one sample')
        }

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

        double[] forkMeans = new double[rawData.size()]
        for (int f = 0; f < rawData.size(); f++) {
            forkMeans[f] = mean(rawData.get(f))
        }
        double score = mean(forkMeans)
        double scoreError = tIntervalError(forkMeans)

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
        primary.put('score', score)
        primary.put('scoreError', scoreError)
        primary.put('scoreConfidence', [score - scoreError, score + scoreError])
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

    static void writeEntries(Path out, List<Map<String, Object>> entries) {
        if (Files.exists(out)) {
            String existing = Files.readString(out, StandardCharsets.UTF_8).trim()
            if (!existing) {
                throw new IllegalStateException("Truncated or invalid bench output: ${out}")
            }
            Object parsed
            try {
                parsed = new groovy.json.JsonSlurper().parseText(existing)
            } catch (JsonException error) {
                throw new IllegalStateException("Truncated or invalid bench output: ${out}", error)
            }
            if (!(parsed instanceof List)) {
                throw new IllegalStateException("Refusing to overwrite non-JSON-array bench output: ${out}")
            }
        }
        Path parent = out.getParent()
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(out, JsonOutput.prettyPrint(JsonOutput.toJson(entries)), StandardCharsets.UTF_8)
    }

    static int intProperty(String primary, String fallback, int defaultValue, boolean allowZero) {
        String raw = System.getProperty(primary)
        String name = primary
        if (raw == null) {
            raw = System.getProperty(fallback)
            name = fallback
        }
        if (raw == null) {
            return defaultValue
        }
        return parseDecimalInt(raw, name, allowZero)
    }

    private static int parseDecimalInt(String raw, String name, boolean allowZero) {
        int value
        try {
            value = Integer.parseInt(raw)
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("${name} must be a decimal integer, was '${raw}'")
        }
        if (value < 0 || (!allowZero && value < 1)) {
            throw new IllegalArgumentException("${name} must be ${allowZero ? '>= 0' : '>= 1'}, was ${value}")
        }
        return value
    }

    private static double[] rulerSamples(int samples, int warmup) {
        for (int i = 0; i < warmup; i++) {
            rulerNanos()
        }
        double[] values = new double[samples]
        for (int i = 0; i < samples; i++) {
            values[i] = (double) rulerNanos()
        }
        return values
    }

    private static long rulerNanos() {
        long start = System.nanoTime()
        long acc = 0L
        for (int i = 0; i < 64; i++) {
            acc += i
        }
        if (acc < 0L) {
            throw new IllegalStateException('ruler accumulator overflow')
        }
        return System.nanoTime() - start
    }

    private static double mean(List<Double> values) {
        double sum = 0d
        for (Double value : values) {
            sum += value.doubleValue()
        }
        return sum / (double) values.size()
    }

    private static double mean(double[] values) {
        double sum = 0d
        for (double value : values) {
            sum += value
        }
        return sum / (double) values.length
    }

    private static double tIntervalError(double[] forkMeans) {
        if (forkMeans.length < 2) {
            return 0d
        }
        double mean = mean(forkMeans)
        double sumSq = 0d
        for (double value : forkMeans) {
            double delta = value - mean
            sumSq += delta * delta
        }
        double stdev = Math.sqrt(sumSq / (double) (forkMeans.length - 1))
        return tCritical95(forkMeans.length - 1) * stdev / Math.sqrt((double) forkMeans.length)
    }

    /**
     * Two-sided 95% Student t critical values. df &gt;= 30 uses 1.96.
     */
    private static double tCritical95(int df) {
        if (df < 1) {
            return 0d
        }
        double[] table = [
                12.706d, 4.303d, 3.182d, 2.776d, 2.571d, 2.447d, 2.365d, 2.306d, 2.262d, 2.228d,
                2.201d, 2.179d, 2.160d, 2.145d, 2.131d, 2.120d, 2.110d, 2.101d, 2.093d, 2.086d,
                2.080d, 2.074d, 2.069d, 2.064d, 2.060d, 2.056d, 2.052d, 2.048d, 2.045d, 2.042d
        ] as double[]
        return df <= table.length ? table[df - 1] : 1.96d
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
