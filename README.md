# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs).

## Results

Karate's custom JS engine outperforms both Rhino and GraalJS for typical short script evaluations:

```
═══════════════════════════════════════════════════════════════════════════════════════════
                       KARATE vs RHINO vs GRAALJS COMPARISON
═══════════════════════════════════════════════════════════════════════════════════════════

Workload         Karate (ms)  Rhino (ms)  Graal (ms)       vs Rhino       vs Graal
------------------------------------------------------------------------------------------
Arithmetic            0.14        0.60        0.46      4.1x faster    3.2x faster
Strings               0.05        0.49        0.37      9.5x faster    7.4x faster
Objects               0.12        0.86        0.44      7.1x faster    3.6x faster
Functions             0.28        0.62        0.42      2.2x faster    1.5x faster
Mixed                 0.30        0.93        0.44      3.2x faster    1.5x faster
------------------------------------------------------------------------------------------
Combined              0.93        2.34        0.66      2.5x faster    1.4x slower
══════════════════════════════════════════════════════════════════════════════════════════
```

### Summary

| Comparison | Performance |
|------------|-------------|
| vs Rhino | **2.2x - 9.5x faster** |
| vs GraalJS | **1.5x - 7.4x faster** (5 of 6 tests) |

### Lexer Performance

The lexer processes **100+ chars/µs** and **23+ tokens/µs**:

```
Small source (mixed)       5621 chars 1240 tokens |   0.05 ms |  104.6 chars/µs | 23.1 tok/µs
Large source (50x)       281100 chars 61951 tokens |   2.63 ms |  106.9 chars/µs | 23.5 tok/µs
```

### Lexer vs Full Eval Breakdown

Only **1-3%** of execution time is spent in the lexer:

```
Workload               Lex (ms)  Eval (ms)      Lex %    Other %
----------------------------------------------------------------------
Arithmetic               0.0018     0.14         1.2%      98.8%
Strings                  0.0013     0.05         2.9%      97.1%
Objects                  0.0038     0.12         3.2%      96.8%
Functions                0.0028     0.28         1.0%      99.0%
Mixed                    0.0059     0.29         2.0%      98.0%
All Combined             0.0157     0.89         1.8%      98.2%
```

## Why Karate JS is Faster

1. **Lightweight Context Creation**: Minimal startup overhead, ideal for short script evaluations
2. **Optimized for Typical Use Cases**: Purpose-built for API testing (JSON manipulation, assertions, data transformation)
3. **No JIT Compilation Overhead**: For short-lived scripts, JIT warmup time in larger engines becomes a liability

## Test Environment

- **Java**: 21+
- **Karate JS**: 2.0.0.RC1
- **Rhino**: 1.9.0 (ES6 mode)
- **GraalJS**: 24.1.1 (Community Edition, interpreted mode)

All engines create a fresh context for each `eval()` to ensure fair comparison.

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

### Lexer Benchmarks
| Category | Description |
|----------|-------------|
| **Identifiers/Keywords** | Variable names, function declarations, control flow |
| **Strings/Templates** | String literals, template literals with interpolation |
| **Numbers/Operators** | Numeric literals, arithmetic, bitwise, comparison operators |
| **Objects/Arrays** | Object literals, array literals, destructuring |
| **Functions** | Arrow functions, async/await, try/catch |
| **Comments/Whitespace** | Single-line, multi-line, JSDoc comments |
| **Regex** | Regular expression literals |
| **Edge Cases** | Unicode identifiers, BigInt, numeric separators, optional chaining |

### Eval Benchmarks
| Workload | Description |
|----------|-------------|
| **Arithmetic** | Loops with math operations |
| **Strings** | String concatenation and splitting |
| **Objects** | Array/object creation, `filter()`, `reduce()` |
| **Functions** | Recursive fibonacci and factorial |
| **Mixed** | Realistic data processing function |
| **Combined** | All workloads combined |

## Notes

- GraalJS runs in **interpreted mode** on standard JVMs. With GraalVM + JVMCI enabled, GraalJS would be significantly faster for long-running scripts due to JIT compilation.
- For Karate's use case (short API test scripts with fresh contexts), the lightweight Karate engine is the optimal choice.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Karate](https://github.com/karatelabs/karate) - API Test Automation Made Simple
