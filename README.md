# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate-v2/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs).

## Results Summary

| Comparison | Performance |
|------------|-------------|
| vs Rhino | **2.3x - 8.3x faster** |
| vs GraalJS | **1.5x - 7.9x faster** (typical scripts < 5KB) |

## Context Creation Overhead

The key to Karate's performance advantage: **~1300x faster context creation**.

```
                 Karate (µs)  Rhino (µs)  Graal (µs)       vs Rhino       vs Graal
───────────────────────────────────────────────────────────────────────────────────────────
Context Create          0.03       41.43       39.78 1295.8x faster 1244.1x faster
```

This matters because Karate creates a fresh JavaScript context for each script evaluation (the typical pattern for API testing).

## Script Evaluation (Fresh Context)

Karate outperforms both engines for typical short scripts:

```
Workload         Karate (ms)  Rhino (ms)  Graal (ms)       vs Rhino       vs Graal
───────────────────────────────────────────────────────────────────────────────────────────
Arithmetic            0.14        0.57        0.41    4.2x faster    3.0x faster
Strings               0.04        0.36        0.34    8.3x faster    7.9x faster
Objects               0.12        0.80        0.40    6.6x faster    3.3x faster
Functions             0.25        0.57        0.39    2.3x faster    1.5x faster
Mixed                 0.28        0.91        0.43    3.2x faster    1.5x faster
```

## Context Reuse (Pure Execution Speed)

When the same context is reused (no context creation overhead), GraalJS shows faster raw execution:

```
Workload         Karate (ms)  Rhino (ms)  Graal (ms)       vs Rhino       vs Graal
───────────────────────────────────────────────────────────────────────────────────────────
Mixed (reuse)         0.30        1.02        0.05    3.4x faster    5.5x slower
Mixed (no-cache)      0.30        0.92        0.05    3.1x faster    6.3x slower
```

**Note**: GraalJS appears to cache parsed scripts. Even with cache-defeating randomized scripts ("no-cache"), GraalJS's interpreter is ~5-6x faster than Karate for pure execution.

## Large Script Scaling

How performance changes with script size (fresh context per eval):

```
Size             Karate (ms)  Rhino (ms)  Graal (ms)       vs Rhino       vs Graal
───────────────────────────────────────────────────────────────────────────────────────────
1KB (1133B)             0.11        1.55        0.46   14.1x faster    4.2x faster
5KB (5378B)             0.52        7.08        0.72   13.5x faster    1.4x faster
10KB (10728B)           1.07       11.78        0.97   11.0x faster    1.1x slower
50KB (53533B)           5.55       55.68        3.78   10.0x faster    1.5x slower
100KB (107533B)        13.44      118.11        7.29    8.8x faster    1.8x slower
```

**Crossover point**: ~10KB. Below this, Karate wins. Above this, GraalJS edges ahead.

## Analysis

### Karate is a good fit for short expressions

Creating a new Karate `Engine` instance is ~1300x faster. Karate tests are typically short snippets or expressions scattered within a Gherkin file. Context creation overhead dominates total execution time. GraalJS and Rhino pay ~40µs per context; Karate pays ~0.03µs.

### When GraalJS Would Win

- **Large scripts (>10KB)**: GraalJS is ~1.8x faster at 100KB
- **Context reuse**: GraalJS's interpreter is ~5x faster when context overhead is removed
- **GraalVM native**: With JVMCI enabled, GraalJS would JIT-compile hot paths

### Perspective on Script Sizes

| Example | Size |
|---------|------|
| Typical Karate expression | 50-200 bytes |
| Benchmark "Mixed" workload | 576 bytes |
| All 5 workloads combined | 1.4 KB |
| **Crossover point** | **~10 KB** |
| 10KB = 20 functions, 400 lines | (see benchmark) |
| jQuery minified | 87 KB |
| Lodash minified | 72 KB |

## Test Environment

- **Java**: 21+
- **Karate JS**: 2.0.0.RC1
- **Rhino**: 1.9.0 (ES6 mode)
- **GraalJS**: 25.0.2 (Community Edition, interpreted mode)

## Running the Benchmark

### Prerequisites

- Java 21+
- Maven 3.8+

### Run

```bash
mvn compile exec:java
```

### Run with Custom CSV Output

```bash
mvn compile exec:java -Dexec.args="results.csv"
```

## Benchmark Categories

| Workload | Description |
|----------|-------------|
| **Arithmetic** | Loops with math operations |
| **Strings** | String concatenation and splitting |
| **Objects** | Array/object creation, `filter()`, `reduce()` |
| **Functions** | Recursive fibonacci and factorial |
| **Mixed** | Realistic data processing function |

## Notes

- GraalJS runs in **interpreted mode** on standard JVMs. With GraalVM + JVMCI enabled, GraalJS would be significantly faster for long-running scripts due to JIT compilation.
- GraalJS appears to cache parsed scripts, giving it an advantage when the same script is evaluated multiple times on the same context.
- Karate's engine is optimized for the API testing and LLM REPL use case: many small, independent script evaluations with fresh contexts.
