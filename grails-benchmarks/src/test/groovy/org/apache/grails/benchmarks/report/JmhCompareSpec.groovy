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
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class JmhCompareSpec extends Specification {
    @TempDir Path temporaryDirectory

    def "speedup respects throughput and latency direction"() {
        expect:
        BenchmarkComparator.speedup(benchmark(120, 'ops/s'), benchmark(100, 'ops/s')) == 1.2D
        BenchmarkComparator.speedup(benchmark(100), benchmark(120)) == 1.2D
    }

    def "verdict requires threshold and disjoint intervals"() {
        expect:
        compare(benchmark(120, 'ns/op', [119, 121]), benchmark(100, 'ns/op', [99, 101])).rows[0].verdict == 'REGRESSED'
        compare(benchmark(120, 'ns/op', [95, 125]), benchmark(100, 'ns/op', [90, 121])).rows[0].verdict == 'no clear change'
        compare(benchmark(105, 'ns/op', [104, 106]), benchmark(100, 'ns/op', [99, 101])).rows[0].verdict == 'no clear change'
    }

    def "fallback errors and missing data render correctly"() {
        given:
        Benchmark fallbackHead = new Benchmark('sample.Fallback.run', 120D, 1D, [119D, 121D], 'ns/op', 'avgt', null)
        Benchmark fallbackBase = new Benchmark('sample.Fallback.run', 100D, 1D, [99D, 101D], 'ns/op', 'avgt', null)
        Benchmark missing = new Benchmark('sample.Fallback.run', 120D, null, null, 'ns/op', 'avgt', null)
        expect:
        compare(fallbackHead, fallbackBase).rows[0].verdict == 'REGRESSED'
        compare(missing, missing).rows[0].verdict == 'insufficient data'
        !ReportRenderer.render(compare(missing, missing)).toLowerCase().contains('nan')
    }

    def "identity pooling and incompatible units preserve comparison safety"() {
        given:
        Benchmark first = new Benchmark('sample.Run[a=1,z=2]', 100D, 1D, [90D, 110D], 'ns/op', 'avgt', 200D)
        Benchmark second = new Benchmark('sample.Run[a=1,z=2]', 200D, 1D, [190D, 210D], 'ns/op', 'avgt', 400D)
        expect:
        JmhResults.poolBenchmarks([first, second]).with { score == 150D && allocation == 300D && confidence == [90D, 210D] }
        JmhResults.poolBenchmarks([first, new Benchmark(first.identity, 100D, 1D, [90D, 110D], 'ops/s', 'avgt', null)]).score == null
        BenchmarkComparator.compare([(first.identity): first], [(first.identity): new Benchmark(first.identity, 100D, 1D, [99D, 101D], 'ops/s', 'avgt', null)], .1D).malformed == 1
    }

    def "rulers are excluded and strict instability is reported"() {
        given:
        String ruler = 'org.apache.grails.benchmarks.ruler.CpuRuler.run'
        Comparison unstable = compare(new Benchmark(ruler, 100D, 1D, [99D, 101D], 'ns/op', 'avgt', null), new Benchmark(ruler, 90D, 1D, [89D, 91D], 'ns/op', 'avgt', null))
        expect:
        unstable.rows[0].verdict == 'ruler - excluded'
        ReportRenderer.render(unstable).contains('runner was unstable BETWEEN')
        !ReportRenderer.render(compare(new Benchmark(ruler, 100D, 1D, [99D, 101D], 'ns/op', 'avgt', null), new Benchmark(ruler, 105D, 1D, [104D, 106D], 'ns/op', 'avgt', null))).contains('runner was unstable BETWEEN')
    }

    def "shards drop unpaired data and expose missing runners"() {
        given:
        Benchmark value = benchmark(100)
        Comparison result = BenchmarkComparator.compareShards(['a.json': ['sample.Run': value]], ['a.json': ['sample.Run': value]], .1D, ['a.json', 'b.json'])
        expect:
        result.missingShards == ['b.json']
        ReportRenderer.render(result).contains('1 shard pair(s) instead of the expected 2')
    }

    def "number formatting matches Python percent g rendering"() {
        expect:
        ReportRenderer.number(value) == rendered
        where:
        value    || rendered
        100D     || '100'
        4.07D    || '4.07'
        18300D   || '1.83e+04'
    }

    def "head only output writes no workflow summary and null PR does not post"() {
        given:
        Path json = temporaryDirectory.resolve('head.json')
        Path output = temporaryDirectory.resolve('report.md')
        Files.writeString(json, '[{"benchmark":"sample.New.run","mode":"avgt","primaryMetric":{"score":42,"scoreError":1,"scoreUnit":"ns/op"}}]')
        CommentPoster poster = Mock()
        when:
        int code = JmhCompare.run(['--head', json.toString(), '--output', output.toString(), '--repo', 'apache/grails-core', '--pr-number', 'null'] as String[], poster)
        then:
        code == 0
        Files.readString(output).toLowerCase().contains('no comparison was possible')
        0 * poster._
    }

    def "fail on regression remains successful"() {
        given:
        Path head = temporaryDirectory.resolve('head.json')
        Path base = temporaryDirectory.resolve('base.json')
        String json = '[{"benchmark":"sample.Run","mode":"avgt","primaryMetric":{"score":%s,"scoreError":1,"scoreUnit":"ns/op"}}]'
        Files.writeString(head, String.format(json, '120'))
        Files.writeString(base, String.format(json, '100'))
        expect:
        JmhCompare.run(['--head', head.toString(), '--base', base.toString(), '--fail-on-regression'] as String[], Mock(CommentPoster)) == 0
    }

    private static Benchmark benchmark(double score, String unit = 'ns/op', List<Double> confidence = [99D, 101D]) {
        new Benchmark('sample.Group.run', score, 1D, confidence, unit, 'avgt', null)
    }

    private static Comparison compare(Benchmark head, Benchmark base) {
        BenchmarkComparator.compare([(head.identity): head], [(base.identity): base], .1D)
    }
}
