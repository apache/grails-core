### JMH Benchmark Report

**Regressions:** 0
**Improvements:** 0
**Runner health:** worst ruler deviation: 12.0% in shard-b.json (CpuRuler.run)
Ruler benchmarks are excluded from verdicts and group summaries; runner health is a stability check, not a calibration factor.

> **Warning:** No usable base/head pair was produced by shard-c.json. This comparison rests on 2 shard pair(s) instead of the expected 3, so the alternating measurement order did not fully cancel and the result is weaker than a normal run.
**Ruler movements:** shard-a.json: CpuRuler.run: 0.89x, shard-b.json: CpuRuler.run: 1.12x

> **Warning:** The runner was unstable BETWEEN the two halves of the A/B run. Treat results as unreliable. Runner health is a stability check, not a calibration factor.

Group geometric means are descriptive only, not verdicts.

| Group | Descriptive geomean speedup | n |
| --- | ---: | ---: |
| s | 0.95x | 1 |

<details>
<summary>Per-benchmark results</summary>

| Benchmark | Base score | Head score | Speedup | Verdict | Allocation delta (ADVISORY) |
| --- | ---: | ---: | ---: | --- | ---: |
| ruler.CpuRuler.run | 100 ns/op | 101 ns/op | 0.99x | ruler - excluded | — |
| s.Paired.run | 100 ns/op | 105 ns/op | 0.95x | no clear change | +200 B/op (+200.0%) **candidate** |

</details>

**Dropped unpaired shard samples (1):** s.CrossShard.run

<!-- grails-jmh-benchmark -->
