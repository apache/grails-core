/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class JmhResults {

    static final String ALLOCATION_METRIC = 'gc.alloc.rate.norm'

    static Double finiteNumber(Object value) {
        if (value == null) {
            return null
        }
        try {
            Double number = Double.valueOf(value.toString())
            return Double.isFinite(number) ? number : null
        } catch (NumberFormatException ignored) {
            return null
        }
    }

    static List<Double> confidenceInterval(Map<String, Object> metric) {
        Object value = metric.get('scoreConfidence')
        if (value instanceof List && ((List<?>) value).size() == 2) {
            Double lower = finiteNumber(((List<?>) value).get(0))
            Double upper = finiteNumber(((List<?>) value).get(1))
            if (lower != null && upper != null) {
                return [Math.min(lower, upper), Math.max(lower, upper)]
            }
        }
        Double score = finiteNumber(metric.get('score'))
        Double error = finiteNumber(metric.get('scoreError'))
        return score != null && error != null && error >= 0D ? [score - error, score + error] : null
    }

    static String identity(String name, String mode, Map<String, Object> params) {
        List<String> components = ['mode=' + mode.trim().toLowerCase(Locale.ROOT)]
        if (params != null && !params.isEmpty()) {
            List<String> keys = new ArrayList<>(params.keySet())
            keys.sort()
            components.addAll(keys.collect { String key -> key + '=' + params.get(key) })
        }
        return name + '[' + components.join(',') + ']'
    }

    static Map<String, Benchmark> parseEntries(List<?> entries) {
        Map<String, Benchmark> benchmarks = new LinkedHashMap<>()
        entries.each { Object entry ->
            if (!(entry instanceof Map)) {
                return
            }
            Map<String, Object> item = (Map<String, Object>) entry
            Object name = item.get('benchmark')
            Object metricValue = item.get('primaryMetric')
            if (!(name instanceof String) || !(metricValue instanceof Map)) {
                return
            }
            Map<String, Object> metric = (Map<String, Object>) metricValue
            Map<String, Object> params = item.get('params') instanceof Map ? (Map<String, Object>) item.get('params') : null
            Map<String, Object> secondary = item.get('secondaryMetrics') instanceof Map ? (Map<String, Object>) item.get('secondaryMetrics') : null
            Map<String, Object> allocationMetric = secondary?.get(ALLOCATION_METRIC) instanceof Map ? (Map<String, Object>) secondary.get(ALLOCATION_METRIC) : null
            Double allocation = allocationMetric?.get('scoreUnit') == 'B/op' ? finiteNumber(allocationMetric.get('score')) : null
            String mode = item.get('mode') instanceof String ? ((String) item.get('mode')).trim() : ''
            String benchmarkId = identity((String) name, mode, params)
            benchmarks.put(benchmarkId, new Benchmark(benchmarkId, finiteNumber(metric.get('score')), finiteNumber(metric.get('scoreError')), confidenceInterval(metric), String.valueOf(metric.getOrDefault('scoreUnit', '')), mode, allocation))
        }
        return benchmarks
    }

    static Map<String, Map<String, Benchmark>> readShards(String location) {
        Path input = Path.of(location)
        List<Path> paths = Files.isDirectory(input)
                ? Files.list(input).withCloseable { stream ->
                    stream.filter { Path path -> path.fileName.toString().endsWith('.json') }.sorted().toList()
                }
                : [input]
        Map<String, Map<String, Benchmark>> shards = new LinkedHashMap<>()
        paths.each { Path path ->
            Object parsed = new JsonSlurper().parse(path.toFile())
            if (!(parsed instanceof List)) {
                throw new IllegalArgumentException("JMH JSON must be an array: ${path}")
            }
            shards.put(Files.isDirectory(input) ? path.fileName.toString() : 'anonymous shard', parseEntries((List<?>) parsed))
        }
        return shards
    }

    static Map<String, Benchmark> readResults(String location) {
        return poolShards(readShards(location))
    }

    static Map<String, Benchmark> poolShards(Map<String, Map<String, Benchmark>> shards) {
        Map<String, List<Benchmark>> samples = new TreeMap<>()
        shards.values().each { Map<String, Benchmark> shard -> shard.each { String key, Benchmark value -> samples.computeIfAbsent(key) { [] }.add(value) } }
        return samples.collectEntries { String key, List<Benchmark> value -> [(key): poolBenchmarks(value)] }
    }

    static Benchmark poolBenchmarks(List<Benchmark> samples) {
        if (samples.size() == 1) {
            return samples[0]
        }
        Benchmark first = samples[0]
        if (samples.any { Benchmark sample -> sample.unit != first.unit || sample.mode != first.mode }) {
            return new Benchmark(first.identity, null, null, null, first.unit, first.mode, null)
        }
        List<List<Double>> intervals = samples.collect { Benchmark sample -> sample.confidence }.findAll { List<Double> interval -> interval != null }
        List<Double> confidence = intervals.size() == samples.size() ? [intervals.collect { List<Double> interval -> interval[0] }.min(), intervals.collect { List<Double> interval -> interval[1] }.max()] : null
        return new Benchmark(first.identity, mean(samples.collect { Benchmark sample -> sample.score }), mean(samples.collect { Benchmark sample -> sample.error }), confidence, first.unit, first.mode, mean(samples.collect { Benchmark sample -> sample.allocation }))
    }

    static Double mean(List<Double> values) {
        List<Double> present = values.findAll { Double value -> value != null }
        if (present.isEmpty()) {
            return null
        }
        double sum = 0D
        present.each { Double value -> sum += value }
        return sum / present.size()
    }
}
