### JMH Benchmark Report

**Regressions:** 1
**Improvements:** 2
**Runner health:** worst ruler deviation: 12.4% in anonymous shard (CpuRulerBenchmark.integerArithmetic (mode=avgt))
Ruler benchmarks are excluded from verdicts and group summaries; runner health is a stability check, not a calibration factor.
**Ruler movements:** anonymous shard: CpuRulerBenchmark.integerArithmetic (mode=avgt): 1.12x, anonymous shard: MemoryRulerBenchmark.allocateAndCopyArray (mode=avgt): 0.93x

> **Warning:** The runner was unstable BETWEEN the two halves of the A/B run. Treat results as unreliable. Runner health is a stability check, not a calibration factor.

Group geometric means are descriptive only, not verdicts.

| Group | Descriptive geomean speedup | n |
| --- | ---: | ---: |
| databinding | 0.94x | 1 |
| gsp | 1.00x | 1 |
| throughput | 1.20x | 1 |
| urlmappings | 0.69x | 2 |
| views | 2.00x | 1 |

<details>
<summary>Per-benchmark results</summary>

| Benchmark | Base score | Head score | Speedup | Verdict | Allocation delta (ADVISORY) |
| --- | ---: | ---: | ---: | --- | ---: |
| urlmappings.UrlMappingsBenchmark.matchWarmCache (mode=avgt) | 4.07 ns/op | 8.77 ns/op | 0.46x | REGRESSED | ~0 B/op |
| throughput.Ops.run (mode=avgt) | 100 ops/s | 120 ops/s | 1.20x | IMPROVED | — |
| views.ViewTemplateRenderingBenchmark.renderJsonTemplate (mode=avgt) | 0.92 ns/op | 0.46 ns/op | 2.00x | IMPROVED | +17 B/op (+17.0%) **candidate** |
| databinding.SimpleDataBinderBenchmark.bindFlatMap (mode=avgt) | 1.72e+04 ns/op | 1.83e+04 ns/op | 0.94x | no clear change | +17 B/op (+1.7%) |
| gsp.GroovyPageParserBenchmark.parseSmallTemplate (mode=avgt) | 9.98e+03 ns/op | 9.94e+03 ns/op | 1.00x | no clear change | ~0 B/op |
| ruler.CpuRulerBenchmark.integerArithmetic (mode=avgt) | 100 ns/op | 89 ns/op | 1.12x | ruler - excluded | — |
| ruler.MemoryRulerBenchmark.allocateAndCopyArray (mode=avgt) | 100 ns/op | 108 ns/op | 0.93x | ruler - excluded | — |
| urlmappings.UrlMappingsBenchmark.matchColdVariedKeys (mode=avgt) | 818 ns/op | 802 ns/op | 1.02x | no clear change | +10 B/op (+1.0%) |

</details>

<!-- grails-jmh-benchmark -->
