### JMH Benchmark Report

**Regressions:** 1
**Improvements:** 1
**Runner health:** not measured
Ruler benchmarks are excluded from verdicts and group summaries; runner health is a stability check, not a calibration factor.

Group geometric means are descriptive only, not verdicts.

| Group | Descriptive geomean speedup | n |
| --- | ---: | ---: |
| group | 1.00x | 2 |

<details>
<summary>Per-benchmark results</summary>

| Benchmark | Base score | Head score | Speedup | Verdict | Allocation delta (ADVISORY) |
| --- | ---: | ---: | ---: | --- | ---: |
| g.group.First.run | 100 ns/op | 200 ns/op | 0.50x | REGRESSED | — |
| g.group.Second.run | 100 ns/op | 50 ns/op | 2.00x | IMPROVED | — |

</details>

<!-- grails-jmh-benchmark -->
