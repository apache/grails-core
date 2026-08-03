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

import java.util.Comparator
import java.util.Locale

@CompileStatic
class ReportRenderer {
    static final String MARKER = '<!-- grails-jmh-benchmark -->'
    static final String DASH = '—'
    static final String BENCHMARK_PACKAGE = 'org.apache.grails.benchmarks.'

    static String safe(String value) {
        value.replaceAll('[`|<>\\[\\]\\(\\)!\\r\\n]', '')
    }

    static String number(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return DASH
        }
        String rendered = String.format(Locale.ROOT, '%.3g', value)
        int exponent = rendered.indexOf('e')
        String mantissa = exponent >= 0 ? rendered.substring(0, exponent) : rendered
        String suffix = exponent >= 0 ? rendered.substring(exponent) : ''
        if (mantissa.contains('.')) {
            mantissa = mantissa.replaceFirst('0+$', '').replaceFirst('\\.$', '')
        }
        return mantissa + suffix
    }

    static String speedup(Double value) {
        value != null && Double.isFinite(value)
                ? String.format(Locale.ROOT, '%.2fx', value)
                : DASH
    }

    static String identity(String value) {
        safe(removePrefix(value, BENCHMARK_PACKAGE))
    }

    static String score(Benchmark value) {
        String rendered = number(value.score)
        rendered == DASH ? DASH : rendered + ' ' + safe(value.unit)
    }

    static String allocation(ComparisonRow row) {
        if (row.allocationDelta == null || row.allocationPercent == null) {
            return DASH
        }
        if (Math.abs(row.allocationDelta) < 1D) {
            return '~0 B/op'
        }
        return String.format(Locale.ROOT, '%+,.0f B/op (%+.1f%%)%s',
                row.allocationDelta,
                row.allocationPercent * 100D,
                row.allocationCandidate ? ' **candidate**' : '')
    }

    static String render(Comparison comparison) {
        int regressions = comparison.rows.count { ComparisonRow row -> row.verdict == 'REGRESSED' }.intValue()
        int improvements = comparison.rows.count { ComparisonRow row -> row.verdict == 'IMPROVED' }.intValue()
        List<RulerDeviation> deviations = comparison.rulerDeviations ?: comparison.rulerSpeedups.collect { List<Object> item -> new RulerDeviation('', (String) item[0], (Double) item[1]) }
        RulerDeviation worst = deviations ? deviations.max { RulerDeviation item -> deviation(item.speedup) } : null
        String health = comparison.rulerIncomplete
                ? 'INCOMPLETE/unreliable (missing or non-finite ruler measurements)'
                : worst == null
                        ? 'not measured'
                        : "worst ruler deviation: ${String.format(Locale.ROOT, '%.1f%%', deviation(worst.speedup) * 100D)}${worst.shard ? ' in ' + safe(worst.shard) : ''} (${safe(removePrefix(worst.identity, BenchmarkComparator.RULER_PACKAGE))})"
        List<String> lines = ['### JMH Benchmark Report', '', "**Regressions:** ${regressions}".toString(), "**Improvements:** ${improvements}".toString(), "**Runner health:** ${health}".toString(), 'Ruler benchmarks are excluded from verdicts and group summaries; runner health is a stability check, not a calibration factor.']
        if (comparison.missingShards) lines.addAll(['', '> **Warning:** No usable base/head pair was produced by ' + comparison.missingShards.collect { String item -> safe(item) }.join(', ') + ". This comparison rests on ${comparison.pairedShards.size()} shard pair(s) instead of the expected ${comparison.pairedShards.size() + comparison.missingShards.size()}, so the alternating measurement order did not fully cancel and the result is weaker than a normal run."])
        if (deviations) {
            lines.add('**Ruler movements:** ' + deviations.collect { RulerDeviation item ->
                (item.shard ? safe(item.shard) + ': ' : '') +
                        safe(removePrefix(item.identity, BenchmarkComparator.RULER_PACKAGE)) +
                        ': ' + speedup(item.speedup)
            }.join(', '))
        }
        if (comparison.rulerIncomplete) lines.addAll(['', '> **Warning:** Runner health is INCOMPLETE because expected ruler measurements were missing or non-finite. Treat results as unreliable.', '', '**Incomplete ruler measurements:** ' + comparison.rulerIncomplete.collect { String item -> safe(item) }.join(', ')])
        if (deviations.any { RulerDeviation item -> Math.max(item.speedup, 1D / item.speedup) > 1.05D }) lines.addAll(['', '> **Warning:** The runner was unstable BETWEEN the two halves of the A/B run. Treat results as unreliable. Runner health is a stability check, not a calibration factor.'])
        lines.addAll(['', 'Group geometric means are descriptive only, not verdicts.', ''])
        if (comparison.groupSpeedups) {
            lines.addAll(['| Group | Descriptive geomean speedup | n |', '| --- | ---: | ---: |'])
            comparison.groupSpeedups.each { String group, List<Object> value ->
                lines.add("| ${safe(group)} | ${speedup((Double) value[0])} | ${value[1]} |".toString())
            }
        } else {
            lines.add('No comparable non-ruler benchmarks were available for group summaries.')
        }
        lines.addAll(['', '<details>', '<summary>Per-benchmark results</summary>', '', '| Benchmark | Base score | Head score | Speedup | Verdict | Allocation delta (ADVISORY) |', '| --- | ---: | ---: | ---: | --- | ---: |'])
        List<ComparisonRow> sortedRows = new ArrayList<>(comparison.rows)
        Comparator<ComparisonRow> rowOrder = Comparator
                .comparingInt { ComparisonRow row -> verdictRank(row.verdict) }
                .thenComparing { ComparisonRow row -> row.identity }
        sortedRows.sort(rowOrder)
        sortedRows.each { ComparisonRow row -> lines.add("| ${identity(row.identity)} | ${score(row.base)} | ${score(row.head)} | ${speedup(row.speedup)} | ${row.verdict} | ${allocation(row)} |".toString()) }
        lines.addAll(['', '</details>'])
        if (comparison.onlyHead) lines.addAll(['', '**Only in head:** ' + comparison.onlyHead.collect { String item -> safe(item) }.join(', ')])
        if (comparison.onlyBase) lines.addAll(['', '**Only in base:** ' + comparison.onlyBase.collect { String item -> safe(item) }.join(', ')])
        if (comparison.dropped) {
            lines.add('')
            lines.add(("**Dropped unpaired shard samples (${comparison.dropped.size()}):** " + comparison.dropped.collect { String item -> safe(item) }.join(', ')).toString())
        }
        if (comparison.malformed) {
            lines.add('')
            lines.add("**Malformed comparisons skipped:** ${comparison.malformed}".toString())
        }
        return lines.join('\n') + '\n\n' + MARKER
    }

    static String headOnly(Map<String, Benchmark> head) {
        List<String> lines = ['### JMH Benchmark Report', '', '**Regressions:** 0 (no base)', '**Improvements:** 0 (no base)', '**Runner health:** not measured (no base)', '', 'No comparison was possible because the base revision has no benchmark harness.', '', '| Benchmark | Head score | Error | Unit |', '| --- | ---: | ---: | --- |']
        head.values().sort { Benchmark item -> item.identity }.each { Benchmark item -> lines.add("| ${identity(item.identity)} | ${number(item.score)} | ${number(item.error)} | ${safe(item.unit)} |".toString()) }
        return lines.join('\n') + '\n\n' + MARKER
    }

    static double deviation(double value) {
        Math.max(value, 1D / value) - 1D
    }

    private static int verdictRank(String verdict) {
        if (verdict == 'REGRESSED') {
            return 0
        }
        if (verdict == 'IMPROVED') {
            return 1
        }
        return 2
    }

    private static String removePrefix(String value, String prefix) {
        value.startsWith(prefix) ? value.substring(prefix.length()) : value
    }
}
