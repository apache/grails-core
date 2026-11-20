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

import spock.lang.Specification

class BenchmarkComparatorSpec extends Specification {
    def "speedup follows throughput direction"() { expect: BenchmarkComparator.speedup(item(120D, 'ops/s'), item(100D, 'ops/s')) == 1.2D }
    def "speedup follows latency direction"() { expect: BenchmarkComparator.speedup(item(100D), item(120D)) == 1.2D }
    def "large disjoint latency change regresses"() { expect: compare(item(120D, 'ns/op', [119D, 121D]), item(100D, 'ns/op', [99D, 101D])).rows[0].verdict == 'REGRESSED' }
    def "overlapping intervals suppress a large change"() { expect: compare(item(120D, 'ns/op', [95D, 125D]), item(100D, 'ns/op', [90D, 121D])).rows[0].verdict == 'no clear change' }
    def "small effect suppresses a disjoint change"() { expect: compare(item(105D, 'ns/op', [104D, 106D]), item(100D, 'ns/op', [99D, 101D])).rows[0].verdict == 'no clear change' }
    def "score error supplies a confidence interval"() { expect: JmhResults.confidenceInterval([score: 120D, scoreError: 1D]) == [119D, 121D] }
    def "missing interval is insufficient data"() { expect: compare(item(120D, 'ns/op', null, null), item(100D, 'ns/op', null, null)).rows[0].verdict == 'insufficient data' }
    def "nan score error is unavailable"() { expect: JmhResults.confidenceInterval([score: 100D, scoreError: 'NaN']) == null }
    def "one-sided benchmarks are excluded"() { expect: BenchmarkComparator.compare(['a': item(100D), 'head': item(100D)], ['a': item(100D), 'base': item(100D)], .1D).with { rows.size() == 1 && onlyHead == ['head'] && onlyBase == ['base'] } }
    def "identity sorts parameters and normalizes mode"() { expect: JmhResults.identity('sample.Run', 'AVGT', [z: '2', a: '1']) == 'sample.Run[mode=avgt,a=1,z=2]' }
    def "parser preserves same benchmark parameters in separate modes"() {
        given:
        List entries = [
                [benchmark: 'sample.Run', mode: 'avgt', params: [size: '10'], primaryMetric: [score: 100D, scoreError: 1D, scoreUnit: 'ns/op']],
                [benchmark: 'sample.Run', mode: 'thrpt', params: [size: '10'], primaryMetric: [score: 200D, scoreError: 1D, scoreUnit: 'ops/s']]
        ]

        when:
        Map<String, Benchmark> benchmarks = JmhResults.parseEntries(entries)
        Comparison comparison = BenchmarkComparator.compare(benchmarks, benchmarks, .1D)

        then:
        benchmarks.keySet() == ['sample.Run[mode=avgt,size=10]', 'sample.Run[mode=thrpt,size=10]'] as Set
        comparison.rows*.identity == ['sample.Run[mode=avgt,size=10]', 'sample.Run[mode=thrpt,size=10]']
    }
    def "invalid scores are malformed"() { expect: compare(item(0D), item(100D)).malformed == 1 }
    def "allocation requires percentage and absolute thresholds"() { expect: compare(item(100D, 'ns/op', [99D, 101D], 1D, 117D), item(100D, 'ns/op', [99D, 101D], 1D, 100D)).rows[0].allocationCandidate }
    def "unit changes are not comparable"() { expect: compare(item(5000D, 'ops/s'), item(200D)).malformed == 1 }
    def "mode changes are not comparable"() { expect: compare(item(100D, 'ns/op', [99D, 101D], 1D, null, 'sample'), item(100D)).malformed == 1 }
    def "matching mode and unit compare"() { expect: compare(item(120D, 'ns/op', [119D, 121D]), item(100D, 'ns/op', [99D, 101D])).rows[0].verdict == 'REGRESSED' }
    def "ruler benchmarks are excluded from verdicts"() { expect: compare(item(80D, 'ns/op', [79D, 81D], 1D, null, 'avgt', 'org.apache.grails.benchmarks.ruler.Cpu.run'), item(100D, 'ns/op', [99D, 101D], 1D, null, 'avgt', 'org.apache.grails.benchmarks.ruler.Cpu.run')).rows[0].verdict == 'ruler - excluded' }
    def "group uses class name for two-component identities"() {
        expect:
        BenchmarkComparator.group('Class.method') == 'Class'
        BenchmarkComparator.group('pkg.Class.method') == 'pkg'
        BenchmarkComparator.group('method') == 'method'
    }

    def "geometric mean handles known speedups"() { expect: BenchmarkComparator.geometricMean([2D, .5D]) == 1D }

    private static Benchmark item(double score, String unit = 'ns/op', List<Double> confidence = [99D, 101D], Double error = 1D, Double allocation = null, String mode = 'avgt', String name = 'sample.Group.run') {
        new Benchmark(name, score, error, confidence, unit, mode, allocation)
    }

    private static Comparison compare(Benchmark head, Benchmark base) {
        BenchmarkComparator.compare([(head.identity): head], [(base.identity): base], .1D)
    }
}
