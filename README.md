# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs), for the workload Karate cares about: **many small scripts, each evaluated in a fresh context**.

Every competing engine is measured in its default configuration **and tuned**. That turned out to matter more than the engine choice itself: a properly configured Rhino is faster than `karate-js` here, while Rhino at its defaults looks several times slower. Karate is scored against whichever tuned competitor is fastest.

That makes this repo useful beyond Karate. If you embed Rhino or GraalJS yourself, the default-vs-tuned columns show what a one-line configuration change is worth for short-script workloads — see [Engine configuration](#engine-configuration).

`karate-js` is built **from source** off [`karatelabs/karate`](https://github.com/karatelabs/karate) `main` — not from the Maven Central release, which lags `main` considerably.

## Results

All numbers below are produced by the [`benchmark` workflow](.github/workflows/benchmark.yml) on a GitHub-hosted Linux runner, so every run uses the same hardware class and results stay comparable across engine versions. **Do not edit them by hand** — see [Updating the results](#updating-the-results).

<!-- BENCHMARK:START -->
<!-- BENCHMARK:END -->

## Analysis

### The headline: a tuned Rhino is faster than `karate-js`

On this benchmark, Rhino in interpreted mode with a shared sealed root scope (`Rhino-best`) beats `karate-js` on every script-evaluation workload — by roughly 1.5–3x on short fresh-context scripts, and on large scripts too. Karate keeps a lead only on raw context creation.

Earlier versions of this README claimed Karate was 2–8x faster than Rhino. That number came from benchmarking Rhino in its default compiled mode, which generates JVM bytecode and defines a class for every evaluation — cost that a run-once script never amortises. It was a measurement artifact, not an engine difference.

### Why the comparison changed

Two corrections, both of which moved results against Karate:

1. **Rhino got its documented embedding configuration.** Rhino's own docs call `initStandardObjects` *"an expensive method to call"* and recommend a shared sealed scope that each evaluation cheaply prototypes off. That is structurally the same design as `karate-js` — an immutable shared standard library behind a cheap per-eval global — so it is the fair comparison, not merely the fast one.
2. **Parse caching was defeated.** Every evaluation now gets unique source text. GraalJS with a shared `Engine` caches parsed sources across contexts; `karate-js` has no source cache and re-parses every time. Benchmarking identical text let GraalJS skip work Karate always does, which had made it look several times faster on large scripts. It isn't.

### What Karate still wins

Context creation. `new Engine()` is cheap because the standard library lives in lazily-initialised JVM-wide singletons. Read that row as an architectural difference rather than like-for-like — the engines defer different amounts of work, and it is the noisiest measurement in the suite.

That advantage is real but narrow, and on these numbers it is not enough to make `karate-js` the fastest option for its own workload.

### Script sizes in practice

Inline expressions in a feature file run to a few hundred bytes. A `karate-config.js` is normally 0.5–1.5 KB; a substantial JS utility file is a few KB. Tens of KB is a rare outlier — jQuery minified is 87 KB. The large-script rows are included for completeness, not because Karate operates there.

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

Every measurement is the **median of 5 timed runs**, after a per-workload warmup of all six configurations.

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

If you embed these engines yourself, the two Rhino tuning steps are the highest-value changes available for short-script workloads, and both are a few lines:

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

For GraalJS, share one `Engine` across `Context`s ([Oracle's documented recipe](https://www.graalvm.org/latest/reference-manual/embed-languages/)). Each `Context` still gets isolated globals, so this is not a semantic shortcut.

**GraalJS runs interpreted throughout, and on a stock JDK that is now the only supported mode.** Since Truffle 25.1 the optimizing runtime requires GraalVM 25.1+; the previous escape hatch of putting the compiler on `--upgrade-module-path` for a plain OpenJDK was removed ([Truffle CHANGELOG](https://github.com/oracle/graal/blob/master/truffle/CHANGELOG.md)). So these are not GraalJS's best possible numbers, and there is no supported way to improve them without changing JVM. On GraalVM, or via the `js-isolate` artifact, GraalJS would JIT-compile and the large-script and context-reuse rows in particular would look different.

`Karate vs best` always scores Karate against whichever non-Karate configuration was fastest for that row.

### Other caveats

- **Every fresh-context evaluation gets unique source text**, so no engine can serve a cached parse. `karate-js` has no source cache; GraalJS with a shared `Engine` does. Measuring identical text repeatedly would have credited GraalJS for work Karate always performs. The context-reuse table keeps both variants — cached and `no-cache` — because there the repetition is the point.
- Before anything is timed, all six configurations must return **the same value** for every workload. A config that silently failed would otherwise look fastest.
- All configurations share a single JVM, are measured in a fixed order, and are timed with `System.nanoTime()` rather than JMH. Median-of-5 with per-workload warmup absorbs most of this, but these are not JMH-grade figures and small differences should not be over-read.
- `Rhino-best` uses a *sealed* shared scope, so scripts cannot modify built-in prototypes. `karate-js` permits per-engine modification of built-ins. For these workloads that difference does not affect results, but it is not a perfect semantic match.

### Scope

This is a **cross-engine comparison at a point in time** — it answers "how does `karate-js` compare to the alternatives", not "did `karate-js` get faster this release". Version-over-version tracking of the engine itself is done separately in the [karate](https://github.com/karatelabs/karate) repo, where a change can be bisected against commits. Don't read run-to-run movement here as a regression signal.

Historical CSVs live in [`results/`](results/).
