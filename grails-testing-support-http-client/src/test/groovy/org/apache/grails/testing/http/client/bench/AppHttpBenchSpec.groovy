/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client.bench

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir

class AppHttpBenchSpec extends Specification {

    @TempDir
    Path tempDir

    void cleanup() {
        System.clearProperty('app.bench')
        System.clearProperty('app.bench.out')
        System.clearProperty('app.bench.samples')
        System.clearProperty('app.bench.warmup')
        System.clearProperty('app.bench.forks')
        System.clearProperty('appBenchWarmup')
        System.clearProperty('appBenchSamples')
        System.clearProperty('appBenchForks')
    }

    void 'enabled is off by default'() {
        expect:
        !AppHttpBench.enabled()
    }

    void 'enabled follows the app.bench system property'() {
        when:
        System.setProperty('app.bench', 'true')

        then:
        AppHttpBench.enabled()
    }

    void 'toJmhEntry reports a 95 percent t-interval over fork means'() {
        given:
        double[] values = [10d, 20d, 30d, 40d] as double[]

        when:
        Map<String, Object> entry = AppHttpBench.toJmhEntry('appbench.example.httpGet', values, 2)

        then:
        entry.benchmark == 'appbench.example.httpGet'
        entry.mode == 'avgt'
        entry.forks == 2
        ((Map) entry.primaryMetric).score == 25d
        Math.abs(((Map) entry.primaryMetric).scoreError - 127.06d) < 0.000001d
        ((Map) entry.primaryMetric).scoreConfidence.every { double value -> Math.abs(Math.abs(value - 25d) - 127.06d) < 0.000001d }
        ((Map) entry.primaryMetric).scoreUnit == 'ns/op'
        ((List) ((Map) entry.primaryMetric).rawData).size() == 2
    }

    void 'writeEntries replaces an existing JMH JSON array'() {
        given:
        Path out = tempDir.resolve('bench.json')
        Map<String, Object> first = AppHttpBench.toJmhEntry('one', [1d] as double[], 1)
        Map<String, Object> second = AppHttpBench.toJmhEntry('two', [2d] as double[], 1)

        when:
        AppHttpBench.writeEntries(out, [first])
        AppHttpBench.writeEntries(out, [second])
        List parsed = (List) new JsonSlurper().parse(out.toFile())

        then:
        parsed.size() == 1
        parsed[0].benchmark == 'two'
        Files.size(out) > 0
    }

    void 'writeEntries rejects non-array and truncated JSON'() {
        given:
        Path out = tempDir.resolve('bench.json')

        when: 'the prior output is a JSON object'
        Files.writeString(out, '{}')
        AppHttpBench.writeEntries(out, [])

        then:
        IllegalStateException objectError = thrown()
        objectError.message.contains('non-JSON-array')

        when: 'the prior output is truncated'
        Files.writeString(out, '[')
        AppHttpBench.writeEntries(out, [])

        then:
        IllegalStateException truncatedError = thrown()
        truncatedError.message.contains('Truncated or invalid')
    }

    void 'measureAndWrite writes HTTP and ruler benchmarks'() {
        given:
        System.setProperty('app.bench.warmup', '0')
        System.setProperty('app.bench.samples', '2')
        System.setProperty('app.bench.forks', '1')
        Path out = tempDir.resolve('bench.json')

        when:
        AppHttpBench.measureAndWrite('appbench.example.httpGet', out) { }
        List parsed = (List) new JsonSlurper().parse(out.toFile())

        then:
        parsed*.benchmark == ['appbench.example.httpGet', AppHttpBench.RULER_BENCHMARK]
    }

    void 'measureAndWrite rejects a sample count below 1'() {
        given:
        System.setProperty('app.bench.samples', '0')

        when:
        AppHttpBench.measureAndWrite('appbench.invalid', tempDir.resolve('empty.json')) { }

        then:
        IllegalArgumentException error = thrown()
        error.message.contains('app.bench.samples must be >= 1')
    }

    void 'toJmhEntry rejects an empty sample array'() {
        when:
        AppHttpBench.toJmhEntry('appbench.empty', new double[0], 1)

        then:
        IllegalArgumentException error = thrown()
        error.message.contains('at least one sample')
    }

    void 'toJmhEntry clamps forks to the sample count'() {
        given:
        double[] values = [1d, 2d, 3d] as double[]

        when:
        Map<String, Object> entry = AppHttpBench.toJmhEntry('appbench.clamp', values, 10)

        then:
        entry.forks == 3
        ((List) ((Map) entry.primaryMetric).rawData).size() == 3
        ((List) ((Map) entry.primaryMetric).rawData).every { List chunk -> !chunk.isEmpty() }
    }

    void 'intProperty parses decimal values and rejects invalid values'() {
        given:
        System.setProperty('app.bench.samples', configured)

        expect:
        AppHttpBench.intProperty('app.bench.samples', 'appBenchSamples', 1, false) == expected

        where:
        configured || expected
        '010'      || 10
        '1000'     || 1000
    }

    void 'intProperty rejects empty and non-decimal values'() {
        when:
        System.setProperty('app.bench.samples', configured)
        AppHttpBench.intProperty('app.bench.samples', 'appBenchSamples', 1, false)

        then:
        IllegalArgumentException error = thrown()
        error.message.contains('decimal integer')

        where:
        configured << ['', '1O00']
    }
}
