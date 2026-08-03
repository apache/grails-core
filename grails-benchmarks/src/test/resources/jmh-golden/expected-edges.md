### JMH Benchmark Report

**Regressions:** 0
**Improvements:** 1
**Runner health:** not measured
Ruler benchmarks are excluded from verdicts and group summaries; runner health is a stability check, not a calibration factor.

Group geometric means are descriptive only, not verdicts.

| Group | Descriptive geomean speedup | n |
| --- | ---: | ---: |
| e | 0.89x | 3 |
| feature | 1.25x | 1 |
| sample | 1.00x | 3 |

<details>
<summary>Per-benchmark results</summary>

| Benchmark | Base score | Head score | Speedup | Verdict | Allocation delta (ADVISORY) |
| --- | ---: | ---: | ---: | --- | ---: |
| feature.Subject.runlabel=Ruler | 100 ns/op | 80 ns/op | 1.25x | IMPROVED | — |
| e.Insufficient.run | 100 ns/op | 120 ns/op | 0.83x | insufficient data | — |
| e.NonFinite.run | 100 ns/op | 120 ns/op | 0.83x | insufficient data | — |
| e.Shared.run | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.binjected.run | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.Bang.runxy | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.Evil.runlabel=xhttp://example.com | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |

</details>

**Dropped unpaired shard samples (2):** e.BaseOnly.run, e.HeadOnly.run

**Malformed comparisons skipped:** 3

<!-- grails-jmh-benchmark -->
