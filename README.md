# Karate JS Engine Benchmark

This benchmark compares [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) with [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs). It measures the workload that Karate cares about: **many small scripts, each evaluated in a fresh context**.

Each competitor engine is measured in its default configuration and in one or more configurations tuned for this workload. The tuning matters more than the choice of engine. Rhino at its defaults is more than 10x slower than Rhino in its documented embedding configuration. Karate is always scored against the fastest tuned competitor. In `2.1.3.RC1`, Karate matches that fastest competitor: it is equal or faster on four of five short-script workloads and on all large-script sizes. It is 1.3x slower on Mixed.

Engine embedders can use these results to tune Rhino and GraalJS. The default and tuned columns show the effect of workload-specific configuration. See [Engine configuration](#engine-configuration).

`karate-js` is built **from source** from [`karatelabs/karate`](https://github.com/karatelabs/karate) `main`. The Maven Central release is much older than `main`.

## Results

The numbers below come from [`etc/ec2-benchmark.sh`](etc/ec2-benchmark.sh). The script runs the benchmark on a separate on-demand EC2 instance of a fixed type (`c6a.2xlarge` by default). Three properties make these numbers trustworthy:

- A separate instance avoids the contention seen on shared CI runners. Shared runners were too noisy for comparisons below 2x.
- The instance has 8 vCPUs. On 4 vCPU hosts, JIT compiler threads compete with the measured thread. In the recorded 4-vCPU test, individual Karate rows varied by 2.0x and 4.1x between runs. GitHub runners have 4 vCPUs. The script header records this measurement.
- The script performs three complete benchmark runs. It publishes the middle run after it ranks all three by the geometric mean of the five fresh-workload ratios.

The instance type is in the same EPYC CPU family as GitHub's hosted runners, so the ratios stay comparable with the archived `results/*-ci.csv` files. **Do not edit the numbers by hand.** See [Updating the results](#updating-the-results).

<!-- BENCHMARK:START -->

### Test Environment

| | |
|---|---|
| Machine | AMD EPYC 7R13 Processor, 8 vCPU, 15 GB (EC2 c6a.2xlarge, al2023, X64) |
| Java | 21.0.12 (OpenJDK 64-Bit Server VM) |
| Karate JS | 2.1.3.RC1, built from source at `ee85f28cd` |
| Rhino | 1.9.1 |
| GraalJS | 25.2.4 (Community Edition) |
| Run | EC2 c6a.2xlarge on-demand, 2026-08-16 — median of 3 runs by fresh-workload geomean |

Each competitor appears twice — in its default configuration, and tuned for this workload. **Karate vs best** compares Karate against whichever non-Karate configuration was fastest for that row, so Karate is never flattered by a competitor's suboptimal default.

### Context Creation Overhead

Cost of a fresh set of globals, nothing evaluated. The engines defer different amounts of work, so read this as an architectural difference rather than a like-for-like measurement — see the Analysis section.

| | Karate (µs) | Rhino (µs) | Rhino-int (µs) | Rhino-best (µs) | Graal (µs) | Graal-shared (µs) | Karate vs best |
|---|---|---|---|---|---|---|---|
| Context Create | 0.06 | 69.77 | 69.36 | 0.05 | 49.87 | 3.55 | 1.2x slower (Rhino-best) |

### Script Evaluation (Fresh Context)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| Arithmetic | 123 | 0.0549 | 0.8978 | 0.1543 | 0.0611 | 0.6204 | 0.2701 | 1.1x faster (Rhino-best) |
| Strings | 93 | 0.0259 | 0.6631 | 0.1217 | 0.0259 | 0.5018 | 0.2698 | 1.0x faster (Rhino-best) |
| Objects | 329 | 0.0851 | 1.3624 | 0.1787 | 0.0894 | 0.6408 | 0.3280 | 1.0x faster (Rhino-best) |
| Functions | 247 | 0.0637 | 1.0502 | 0.1504 | 0.0699 | 0.6067 | 0.3104 | 1.1x faster (Rhino-best) |
| Mixed | 576 | 0.1617 | 1.4230 | 0.2080 | 0.1215 | 0.6778 | 0.3725 | 1.3x slower (Rhino-best) |

### Context Reuse (Pure Execution Speed)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| Mixed-Reuse | 576 | 0.1714 | 1.5790 | 0.1400 | 0.1309 | 0.0598 | 0.0588 | 2.9x slower (Graal-shared) |
| Mixed-NoCache | 576 | 0.1711 | 1.4983 | 0.1294 | 0.1322 | 0.1379 | 0.1367 | 1.3x slower (Rhino-int) |

### Large Script Scaling (Fresh Context per Eval)

| Target | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| 1KB | 1133 | 0.1227 | 2.3441 | 0.1846 | 0.1447 | 0.7235 | 0.3518 | 1.2x faster (Rhino-best) |
| 5KB | 5378 | 0.3743 | 8.9318 | 0.4758 | 0.3843 | 0.9892 | 0.6440 | 1.0x faster (Rhino-best) |
| 10KB | 10728 | 0.7366 | 16.3720 | 0.8670 | 0.7573 | 1.3816 | 1.2167 | 1.0x faster (Rhino-best) |
| 50KB | 53533 | 3.9011 | 77.5950 | 4.0687 | 4.1955 | 5.0260 | 4.9492 | 1.0x faster (Rhino-int) |
| 100KB | 107533 | 7.7087 | 157.2919 | 8.5935 | 8.7062 | 9.1453 | 9.4081 | 1.1x faster (Rhino-int) |

<!-- BENCHMARK:END -->

## Benchmark Categories

| Workload | Description |
|----------|-------------|
| **Arithmetic** | Loops with math operations |
| **Strings** | String concatenation and splitting |
| **Objects** | Array/object creation, `filter()`, `reduce()` |
| **Functions** | Recursive fibonacci and factorial |
| **Mixed** | Realistic data processing function |
| **Context Reuse** | Same context, many evals — pure execution speed. The `no-cache` variant randomizes each script to defeat GraalJS's parsed-script caching |
| **Large Script Scaling** | Generated scripts from 1KB to 100KB, fresh context per eval |

Within each complete run, each reported measurement is the median of five timed samples, after a per-workload warmup of all six configurations.

## Analysis

### The headline: parity with a tuned Rhino on the core rows

In `2.1.3.RC1`, `karate-js` matches the fastest tuned competitor on the fresh-context rows this benchmark exists for:

- Equal or faster than `Rhino-best` on Arithmetic, Strings, Objects, and Functions.
- Faster on every large-script size from 1KB to 100KB.
- Slower only on Mixed (1.3x). That row combines object literals, array building, and property access. The remaining engine work targets it.

Karate's ratios improved across releases (Arithmetic / Strings / Objects / Functions / Mixed; CSVs in [`results/`](results/)):

| Release | Ratios (lower is better for Karate) | Geometric mean |
|---|---|---|
| 2.1.2.RC1 | 2.09 / 1.98 / 1.48 / 2.42 / 2.34 | ~2.0x |
| 2.1.2.RC2 | 1.41 / 1.56 / 1.26 / 2.13 / 2.03 | ~1.64x |
| 2.1.3.RC1 | 0.90 / 1.00 / 0.95 / 0.91 / 1.33 | ~1.0x |

The first two rows are CI runs. The third row comes from the EC2 run. The ratios are comparable across this hardware class, but CI noise makes the older per-row digits approximate. GraalJS with a shared `Engine` still wins the context-reuse rows.

Earlier results used Rhino's default compiled mode and initialized its standard objects for every evaluation. Production embedders avoid those configurations; Rhino's own docs describe standard-object initialization as expensive. They overstated Karate's advantage — up to 2–8x on scripts and ~1300x on context creation. Those were measurement artifacts, not engine differences.

### Why the comparison changed

Three corrections. Each moved results against Karate:

1. **Rhino got its documented embedding configuration.** Rhino's docs call `initStandardObjects` *"an expensive method to call"*. They recommend a shared sealed scope, with a cheap prototype scope per evaluation. That design is the same as the design of `karate-js`: an immutable shared standard library behind a cheap per-eval global. So it is the fair comparison.
2. **The benchmark now prevents parse caching.** Each evaluation gets unique source text. GraalJS with a shared `Engine` caches parsed sources. `karate-js` has no source cache and parses every time. Identical text let GraalJS skip work that Karate always does, and made it look several times faster on large scripts than it is.
3. **The benchmark disables Java interop in all three engines.** Interop is off in `karate-js` here, and off by default in GraalJS. Rhino therefore uses `initSafeStandardObjects`, which skips interop setup that the other two never perform.

The third correction removed Karate's context-creation lead. When Rhino builds its standard objects one time and shares them, a prototyped Rhino scope costs about the same as `new Engine()`.

### What these results do not say

They do not say that Karate is the fastest possible engine. For repeated evaluation of identical source, the shared-`Engine` cache of GraalJS is the fastest option. The core-workload numbers are fractions of a millisecond per evaluation. In a test that also makes an HTTP call, this difference is likely to be negligible. The results also say nothing about the reasons `karate-js` was written. Speed was never the primary reason.

### Why `karate-js` exists

- **Java interop as a first-class concept.** Karate is a Java tool. Its scripts cross into Java constantly. `karate-js` is designed around that boundary. Note: interop is disabled in this benchmark for all three engines, so these numbers do not reflect it.
- **Protection from JavaScript-engine churn.** Karate was hurt by this twice: Nashorn was removed from the JDK, and GraalJS changed its packaging and runtime requirements. When Karate owns the engine, a JDK or vendor decision cannot break Karate users. The Truffle 25.1 change described below is the same pattern again.
- **A fit-for-purpose engine.** `karate-js` is tuned for Karate's real shape: many small scripts, fresh contexts, and AST caching where it pays. For example, `karate-config.js` is cached, not re-parsed.
- **Execution events.** The interpreter reports statement and expression entry and exit. It also reports branches, comparison operands, property reads, and variable bindings. An embedder can install a listener for these events. This enables branch-level code coverage, execution tracing, debuggers, and tools that observe the real behaviour of a script. No bytecode weaving or separate static analysis is needed.

  These events cost time on every evaluation. A leaner interpreter never pays that cost. Karate accepts this cost to provide runtime inspection. That makes the parity result notable: the event machinery no longer causes a measurable speed penalty on these rows.

Engine optimisation continues, and the improvement from RC1 to 2.1.3 above is that work. Recent changes include dense local-variable storage, cheaper scope exit, single-pass property lookup, and array fast paths.

### Script sizes in practice

An inline expression in a feature file is usually a few hundred bytes. A `karate-config.js` is usually 0.5–1.5 KB. A large JS utility file is a few KB. Tens of KB is rare: jQuery minified is 87 KB. The large-script rows test workloads outside Karate's usual range.

## Running the Benchmark

### Prerequisites

- Java 21+
- Maven 3.8+
- A local checkout of [`karatelabs/karate`](https://github.com/karatelabs/karate)

### Run locally

`build.sh` builds `karate-js` from source into `~/.m2`, then runs the benchmark:

```bash
./build.sh                         # find the karate checkout at ../karate
KARATE_SRC=/path/to/karate ./build.sh
./build.sh --no-run                # build karate-js only
./build.sh results.csv             # write the CSV to a specific path
./build.sh --quick                 # fast directional pass: Karate vs Rhino-best only,
                                   # fresh workloads, reduced iterations - not for publication
```

The script stops if `<karate.version>` in `pom.xml` does not match the version that `karate` `main` builds. Update the pom when karate's version moves.

To run against the `karate-js` already in `~/.m2`, without the source build:

```bash
mvn compile exec:java
```

Each run writes a CSV and a markdown block: `target/benchmark.csv` and `target/benchmark.md`. The markdown block goes between the Results markers above. See [Updating the results](#updating-the-results).

### Run on EC2 (canonical)

The canonical run is [`etc/ec2-benchmark.sh`](etc/ec2-benchmark.sh). The script:

1. Launches a `c6a.2xlarge` on-demand instance.
2. Copies your **local** karate checkout to it, so unpushed engine work is benchmarked correctly.
3. Builds `karate-js` from source and runs the benchmark three times.
4. Selects the median run by the fresh-workload geometric mean.
5. Copies the artifacts back to `target/ec2/`.
6. Terminates everything it created, on success or failure.

A run takes 30–45 minutes and costs about $0.15.

Shared CI runners are no longer the canonical source. Their numbers cannot be trusted at the margins this table measures: back-to-back runs disagreed by more than 2x on a row that neither build touched. The [`benchmark` workflow](.github/workflows/benchmark.yml) still exists for ad-hoc use (`workflow_dispatch`), but its numbers are not published here.

### Updating the results

A results refresh is a manual commit. Every change of the published numbers traces to a person and a run in git history:

```bash
# 1. check the version pin first - the script stops (before any AWS spend) if these disagree
grep karate.version pom.xml
#    compare against <version> in karatelabs/karate's root pom.xml on main,
#    and update pom.xml if it has moved

# 2. run it - a two-line env (AWS_PROFILE + AWS_REGION) is enough in a typical
#    account: the script uses your default VPC and an ephemeral key pair it
#    creates and deletes itself. Copy etc/aws.env.example somewhere private.
source /path/to/your/private/aws.env
KARATE_SRC=/path/to/karate ./etc/ec2-benchmark.sh

# 3. splice the median run's block into the Results section, archive its CSV
./etc/update-readme.py README.md target/ec2/benchmark-median.md
cp target/ec2/benchmark-median.csv results/benchmark-<karate-version>-ec2.csv

# 4. commit both, citing the karate sha the block records
```

**Then re-read the Analysis section and correct it.** `update-readme.py` only rewrites the text between the markers. The Analysis prose is written by hand and sits outside the markers. New results can invalidate it.

After you splice, check at minimum:

- Does `Karate vs best` still name the same winner on every row?
- Do the quoted ranges still bracket the actual numbers?
- Does the intro paragraph still describe the right outcome?

Publish the numbers whatever they say. An earlier version of this README led with `karate-js` losing.

## Notes

### Engine configuration

Six configurations are measured. Each competitor appears as its default and tuned.

| Column | What it is |
|---|---|
| `Karate` | `new Engine().eval(source)` |
| `Rhino` | Rhino's default — generates JVM bytecode and defines a class per evaluation |
| `Rhino-int` | `cx.setInterpretedMode(true)` — no bytecode generation |
| `Rhino-best` | interpreted **and** a shared sealed root scope; each eval gets a cheap scope prototyped off it |
| `Graal` | `Context.create()` — a private `Engine` per `Context` |
| `Graal-shared` | one `Engine` shared by every `Context`, so runtime setup and parsed-code cache are reused |

If you embed these engines yourself, the two Rhino tuning steps are the highest-value changes for short-script workloads. Both are a few lines:

```java
// interpreted mode - skip per-eval bytecode generation
cx.setInterpretedMode(true);

// build the standard objects ONCE, sealed, and share them
ScriptableObject root = cx.initStandardObjects(null, true);
root.sealObject();

// per eval: a cheap fresh global that prototypes off the shared root
Scriptable scope = cx.newObject(root);
scope.setPrototype(root);
scope.setParentScope(null);
```

For GraalJS, share one `Engine` across all `Context` instances ([Oracle's documented recipe](https://www.graalvm.org/latest/reference-manual/embed-languages/)). Each `Context` still has isolated global variables, so this is not a semantic shortcut.

**GraalJS runs interpreted here, and on a stock JDK that is now the only supported mode.** Since Truffle 25.1, the optimizing runtime requires GraalVM 25.1+. The earlier workaround — the compiler on `--upgrade-module-path` on plain OpenJDK — was removed ([Truffle CHANGELOG](https://github.com/oracle/graal/blob/master/truffle/CHANGELOG.md)). These results do not show GraalJS's best possible performance. GraalJS can use JIT compilation on GraalVM or with the `js-isolate` artifact. That configuration can change the large-script and context-reuse results.

`Karate vs best` always scores Karate against the fastest non-Karate configuration for that row.

### Other caveats

- **Each fresh-context evaluation gets unique source text**, so no engine can serve a cached parse. `karate-js` has no source cache; GraalJS with a shared `Engine` has one. Identical text would credit GraalJS for work that Karate always performs. The context-reuse table keeps both variants, cached and `no-cache`, because repetition is the point there.
- Before anything is timed, all six configurations must return **the same value** for every workload. A configuration that failed silently would look fastest.
- All configurations share one JVM, run in a fixed order, and are timed with `System.nanoTime()`, not JMH. The median of five samples with warmup absorbs most of this. These are not JMH-grade figures. Do not over-read small differences.
- `Rhino-best` uses a *sealed* shared scope, so scripts cannot modify built-in prototypes. `karate-js` permits per-engine modification of built-ins. This difference does not affect these workloads, but it is not a perfect semantic match.
- **Java interop is off for all three engines.** A bare `new Engine()` installs no `ExternalBridge` — `karate-core` wires up Karate's Java bridge, not `karate-js` — and GraalJS denies host access by default. Rhino therefore uses `initSafeStandardObjects`, which skips the LiveConnect interop objects that the other two never set up.

### Scope

This is a **cross-engine comparison at a point in time**. It answers "how does `karate-js` compare to the alternatives". It does not answer "did `karate-js` get faster this release". Version-over-version tracking happens in the [karate](https://github.com/karatelabs/karate) repo, where a change can be bisected against commits. Do not read run-to-run movement here as a regression signal.

Historical CSVs live in [`results/`](results/).
