### Test Environment

| | |
|---|---|
| Machine | Mac OS X aarch64, 10 CPUs |
| Java | 24.0.2 (OpenJDK 64-Bit Server VM) |
| Karate JS | 2.1.2.RC1 |
| Rhino | 1.9.1 (ES6, compiled mode — Rhino's default; see Notes) |
| GraalJS | 25.2.4 (Community Edition, interpreted mode) |
| Run | 2026-08-08T22:54:39.883198 (local) |

### Context Creation Overhead

| | Karate (µs) | Rhino (µs) | Graal (µs) | vs Rhino | vs Graal |
|---|---|---|---|---|---|
| Context Create | 0.03 | 40.35 | 30.74 | 1180.0x faster | 899.0x faster |

### Script Evaluation (Fresh Context)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Graal (ms) | vs Rhino | vs Graal |
|---|---|---|---|---|---|---|
| Arithmetic | 123 B | 0.0889 | 0.5456 | 0.3623 | 6.1x faster | 4.1x faster |
| Strings | 93 B | 0.0352 | 0.3584 | 0.3109 | 10.2x faster | 8.8x faster |
| Objects | 329 B | 0.1032 | 0.8139 | 0.3722 | 7.9x faster | 3.6x faster |
| Functions | 247 B | 0.0928 | 0.5895 | 0.3524 | 6.3x faster | 3.8x faster |
| Mixed | 576 B | 0.2045 | 0.8804 | 0.3920 | 4.3x faster | 1.9x faster |

### Context Reuse (Pure Execution Speed)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Graal (ms) | vs Rhino | vs Graal |
|---|---|---|---|---|---|---|
| Mixed-Reuse | 576 B | 0.2018 | 0.9907 | 0.0395 | 4.9x faster | 5.1x slower |
| Mixed-NoCache | 576 B | 0.2005 | 0.9251 | 0.0453 | 4.6x faster | 4.4x slower |

### Large Script Scaling (Fresh Context per Eval)

| Target | Bytes | Karate (ms) | Rhino (ms) | Graal (ms) | vs Rhino | vs Graal |
|---|---|---|---|---|---|---|
| 1KB | 1133 B | 0.0742 | 1.7086 | 0.3922 | 23.0x faster | 5.3x faster |
| 5KB | 5378 B | 0.3641 | 6.0279 | 0.6310 | 16.6x faster | 1.7x faster |
| 10KB | 10728 B | 0.6817 | 11.2522 | 0.8624 | 16.5x faster | 1.3x faster |
| 50KB | 53533 B | 3.4382 | 55.0656 | 3.1936 | 16.0x faster | 1.1x slower |
| 100KB | 107533 B | 7.1118 | 112.6587 | 6.2580 | 15.8x faster | 1.1x slower |

