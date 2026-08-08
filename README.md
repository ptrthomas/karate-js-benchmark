# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs).

`karate-js` is built **from source** off [`karatelabs/karate`](https://github.com/karatelabs/karate) `main` — not from the Maven Central release, which lags `main` considerably.

## Results

All numbers below are produced by the [`benchmark` workflow](.github/workflows/benchmark.yml) on a GitHub-hosted Linux runner, so every run uses the same hardware class and results stay comparable across engine versions. **Do not edit them by hand** — see [Updating the results](#updating-the-results).

<!-- BENCHMARK:START -->
<!-- BENCHMARK:END -->

## Analysis

### Karate is a good fit for short expressions

Creating a new Karate `Engine` is orders of magnitude cheaper than a Rhino or GraalJS context. Karate scripts are typically short snippets or expressions scattered within a Gherkin file, so context creation overhead dominates total execution time — Rhino and GraalJS pay tens of microseconds per context, Karate pays a small fraction of one.

Read that number as an *architectural* difference, not a like-for-like one. The three engines defer different amounts of work: Karate's standard library lives in lazily-initialised JVM-wide singletons, so `new Engine()` allocates very little; Rhino's `initStandardObjects()` eagerly builds a full global environment; GraalJS's `Context.build()` does not initialise the JS language until something is actually evaluated, so its figure understates its true first-use cost. Sharing an immutable standard library across contexts is a real design win for this use case — but the honest end-to-end comparison is the fresh-context evaluation table, not this one.

### When GraalJS would win

- **Large scripts**: past the crossover point (~30–50KB), GraalJS's faster execution outweighs its context setup cost.
- **Context reuse**: with context creation removed from the picture, GraalJS's interpreter is several times faster than Karate for pure execution.
- **GraalVM native**: with JVMCI enabled, GraalJS would JIT-compile hot paths. On a stock JVM it runs interpreted, which is what this benchmark measures.

### Perspective on script sizes

The crossover only matters if real Karate scripts get that large — and they don't. Inline expressions in a feature file are typically well under a few hundred bytes. A `karate-config.js` is normally around 0.5–1.5 KB, and a substantial JS utility file is a few KB. Scripts in the tens of KB are rare outliers; for context, jQuery minified is 87 KB.

So the sizes where GraalJS pulls ahead sit well outside the range Karate actually operates in.

## Running the Benchmark

### Prerequisites

- Java 21+
- Maven 3.8+
- A local checkout of [`karatelabs/karate`](https://github.com/karatelabs/karate)

### Run

`build.sh` builds `karate-js` from source into `~/.m2`, then runs the benchmark:

```bash
./build.sh                         # find the karate checkout at ../karate
KARATE_SRC=/path/to/karate ./build.sh
./build.sh --no-run                # build karate-js only
./build.sh results.csv             # write the CSV to a specific path
```

It fails fast if `<karate.version>` in `pom.xml` does not match the version `karate` `main` currently builds — bump the pom when karate's version moves.

Without the source build (against whatever `karate-js` is already in `~/.m2`):

```bash
mvn compile exec:java
```

Each run writes a CSV and a markdown block (`target/benchmark.csv` and `target/benchmark.md`). The markdown block is what gets spliced into the Results section above, by hand — see [Updating the results](#updating-the-results).

### Run on CI

The canonical run is `workflow_dispatch` on the [`benchmark` workflow](.github/workflows/benchmark.yml), which also runs weekly. It takes a `karate_ref` input if you want to benchmark a branch other than `main`.

The workflow does **not** write to this repo. It publishes the results to the run's job summary (rendered, plus a paste-ready block and the full console output) and as a `benchmark-results` artifact.

### Updating the results

Refreshing the Results section is a deliberate manual commit, so every numbers change in git history traces back to a person and a run id:

1. Run the workflow and open the run page.
2. Either copy the paste-ready block from the job summary into the `BENCHMARK:START` / `BENCHMARK:END` markers, or download the artifact and splice it in:
   ```bash
   gh run download <run-id> -n benchmark-results -D /tmp/bench
   ./etc/update-readme.py README.md /tmp/bench/benchmark.md
   cp /tmp/bench/benchmark.csv results/benchmark-<karate-version>-ci.csv
   ```
3. Commit both, referencing the run id.

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

Every measurement is the **median of 5 timed runs**, after a per-workload warmup of all three engines.

## Notes

### Engine configuration — read this before quoting the numbers

Both competing engines are run in their **default** configuration, and in both cases that default is not necessarily their fastest for this workload:

- **GraalJS runs interpreted.** On a stock JVM there is no JVMCI, so GraalJS never JIT-compiles. On GraalVM, or with JVMCI enabled, it would be significantly faster for long-running scripts.
- **Rhino runs in compiled mode.** Rhino's default generates JVM bytecode and defines a class per evaluation. For parse-once-run-once workloads — which is exactly the fresh-context scenario benchmarked here — Rhino's *interpreted* mode (`Context.setInterpretedMode(true)`) skips that codegen and is typically much faster. A meaningful part of Rhino's cost in the fresh-context and large-script tables is bytecode compilation it never gets to amortise. **The Rhino numbers here should not be read as Rhino's best possible showing.**

Benchmarking each engine's *default* is a defensible choice and it is applied consistently, but if you are choosing an engine, tune each one before deciding.

### Other caveats

- GraalJS caches parsed scripts, giving it an advantage when the same script is evaluated repeatedly on one context — hence the `no-cache` variant.
- All three engines share a single JVM, are measured in a fixed order, and are timed with `System.nanoTime()` rather than JMH. Median-of-5 with per-workload warmup absorbs most of this, but these are not JMH-grade figures and small differences should not be over-read.
- Karate's engine is optimized for the API testing and LLM REPL use case: many small, independent script evaluations with fresh contexts.

### Scope

This is a **cross-engine comparison at a point in time** — it answers "how does `karate-js` compare to the alternatives", not "did `karate-js` get faster this release". Version-over-version tracking of the engine itself is done separately in the [karate](https://github.com/karatelabs/karate) repo, where a change can be bisected against commits. Don't read run-to-run movement here as a regression signal.

Historical CSVs live in [`results/`](results/).
