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
| feature.Subject.run (mode=avgt,label=Ruler) | 100 ns/op | 80 ns/op | 1.25x | IMPROVED | — |
| e.Insufficient.run (mode=avgt) | 100 ns/op | 120 ns/op | 0.83x | insufficient data | — |
| e.NonFinite.run (mode=avgt) | 100 ns/op | 120 ns/op | 0.83x | insufficient data | — |
| e.Shared.run (mode=avgt) | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.binjected.run (mode=avgt) | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.Bang.runxy (mode=avgt) | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |
| sample.Evil.runlabel=xhttp://example.com (mode=avgt) | 100 ns/op | 100 ns/op | 1.00x | no clear change | — |

</details>

**Dropped unpaired shard samples (4):** e.BaseOnly.run (mode=avgt), e.HeadOnly.run (mode=avgt), e.ModeSwitch.run (mode=avgt), e.ModeSwitch.run (mode=sample)

**Malformed comparisons skipped:** 2

<!-- grails-jmh-benchmark -->
