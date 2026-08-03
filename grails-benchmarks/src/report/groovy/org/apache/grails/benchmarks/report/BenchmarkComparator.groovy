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

import groovy.transform.CompileStatic

@CompileStatic
class BenchmarkComparator {
    static final String RULER_PACKAGE = 'org.apache.grails.benchmarks.ruler.'

    static boolean comparable(Benchmark head, Benchmark base) {
        return !head.mode.isEmpty() && !base.mode.isEmpty() && head.mode.equalsIgnoreCase(base.mode) && head.unit.trim().equalsIgnoreCase(base.unit.trim())
    }

    static Double speedup(Benchmark head, Benchmark base) {
        if (head.score == null || base.score == null || head.score <= 0D || base.score <= 0D || !comparable(head, base)) {
            return null
        }
        return head.unit.toLowerCase(Locale.ROOT).startsWith('ops') ? head.score / base.score : base.score / head.score
    }

    static boolean disjoint(Benchmark head, Benchmark base) {
        return head.confidence != null && base.confidence != null && (head.confidence[1] < base.confidence[0] || base.confidence[1] < head.confidence[0])
    }

    static String group(String identity) {
        List<String> parts = identity.split('\\[', 2)[0].split('\\.') as List<String>
        if (parts.size() >= 3) {
            return parts[parts.size() - 3]
        }
        if (parts.size() >= 2) {
            return parts[parts.size() - 2]
        }
        return parts[parts.size() - 1]
    }

    static Comparison compare(Map<String, Benchmark> head, Map<String, Benchmark> base, double threshold) {
        List<ComparisonRow> rows = []
        int malformed = 0
        Map<String, List<Double>> groups = new TreeMap<>()
        List<List<Object>> rulers = []
        List<String> common = (head.keySet().intersect(base.keySet()) as List<String>).sort()
        double regression = 1D / (1D + threshold)
        common.each { String identity ->
            Benchmark h = head.get(identity)
            Benchmark b = base.get(identity)
            Double value = speedup(h, b)
            if (value == null || !Double.isFinite(value) || value <= 0D) {
                malformed++
                return
            }
            boolean ruler = identity.split('\\[', 2)[0].startsWith(RULER_PACKAGE)
            Double delta = h.allocation != null && b.allocation != null && b.allocation > 0D ? h.allocation - b.allocation : null
            Double percent = delta != null ? delta / b.allocation : null
            boolean candidate = delta != null && percent != null && delta > 16D && percent > .05D
            String verdict = ruler ? 'ruler - excluded' : h.confidence == null || b.confidence == null ? 'insufficient data' : value <= regression && disjoint(h, b) ? 'REGRESSED' : value >= 1D + threshold && disjoint(h, b) ? 'IMPROVED' : 'no clear change'
            if (ruler) {
                rulers.add([identity, value])
            } else {
                groups.computeIfAbsent(group(identity)) { [] }.add(value)
            }
            rows.add(new ComparisonRow(identity, b, h, value, verdict, delta, percent, candidate))
        }
        Map<String, List<Object>> means = new TreeMap<>()
        groups.each { String name, List<Double> values ->
            Double mean = geometricMean(values)
            if (mean != null) {
                means.put(name, [mean, values.size()])
            }
        }
        return new Comparison(rows, (head.keySet() - base.keySet()).sort(), (base.keySet() - head.keySet()).sort(), malformed, means, rulers.sort { List<Object> item -> (String) item[0] }, [], [], [], [], [])
    }

    static Comparison compareShards(Map<String, Map<String, Benchmark>> head, Map<String, Map<String, Benchmark>> base, double threshold, List<String> expected = []) {
        Map<String, List<Benchmark>> headSamples = new TreeMap<>()
        Map<String, List<Benchmark>> baseSamples = new TreeMap<>()
        Set<String> dropped = new TreeSet<>()
        Set<String> rulers = new TreeSet<>()
        List<String> paired = (head.keySet().intersect(base.keySet()) as List<String>).sort()
        paired.each { String shard ->
            Map<String, Benchmark> h = head.get(shard)
            Map<String, Benchmark> b = base.get(shard)
            dropped.addAll(h.keySet() - b.keySet())
            dropped.addAll(b.keySet() - h.keySet())
            (h.keySet() + b.keySet()).findAll { String id -> id.split('\\[', 2)[0].startsWith(RULER_PACKAGE) }.each { String id -> rulers.add(id) }
            h.keySet().intersect(b.keySet()).each { String id -> headSamples.computeIfAbsent(id) { [] }.add(h.get(id)); baseSamples.computeIfAbsent(id) { [] }.add(b.get(id)) }
        }
        [head, base].each { Map<String, Map<String, Benchmark>> all -> (all.keySet() - paired).each { String shard -> dropped.addAll(all.get(shard).keySet()) } }
        List<RulerDeviation> deviations = []
        List<String> incomplete = []
        paired.each { String shard -> rulers.each { String id ->
            Benchmark h = head.get(shard).get(id)
            Benchmark b = base.get(shard).get(id)
            Double value = h != null && b != null ? speedup(h, b) : null
            if (value == null || !Double.isFinite(value) || value <= 0D) incomplete.add("${shard}: ${id}".toString())
            else deviations.add(new RulerDeviation(shard, id, value))
        } }
        Comparison comparison = compare(headSamples.collectEntries { String id, List<Benchmark> samples -> [(id): JmhResults.poolBenchmarks(samples)] }, baseSamples.collectEntries { String id, List<Benchmark> samples -> [(id): JmhResults.poolBenchmarks(samples)] }, threshold)
        Set<String> missing = new TreeSet<>(expected.findAll { String shard -> !paired.contains(shard) })
        missing.addAll((head.keySet() - base.keySet()) - paired)
        missing.addAll((base.keySet() - head.keySet()) - paired)
        return new Comparison(comparison.rows, comparison.onlyHead, comparison.onlyBase, comparison.malformed, comparison.groupSpeedups, comparison.rulerSpeedups, dropped as List<String>, deviations.sort { RulerDeviation item -> item.shard + item.identity }, incomplete.sort(), missing as List<String>, paired)
    }

    static Double geometricMean(List<Double> values) {
        if (values.isEmpty() || values.any { Double value -> value <= 0D || !Double.isFinite(value) }) {
            return null
        }
        double sum = 0D
        values.each { Double value -> sum += Math.log(value) }
        return Math.exp(sum / values.size())
    }
}
