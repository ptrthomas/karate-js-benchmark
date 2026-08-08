# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs), for the workload Karate cares about: **many small scripts, each evaluated in a fresh context**.

Every competing engine is measured **twice** — in its default configuration, and tuned for this workload. That matters more than we expected: the defaults are not these engines' best showing, and a benchmark that only reported defaults would have flattered Karate considerably. Karate is scored against whichever *tuned* competitor is fastest.

That makes this repo useful beyond Karate. If you embed Rhino or GraalJS yourself, the default-vs-tuned columns show what a one-line configuration change is worth for short-script workloads — see [Engine configuration](#engine-configuration).

`karate-js` is built **from source** off [`karatelabs/karate`](https://github.com/karatelabs/karate) `main` — not from the Maven Central release, which lags `main` considerably.

## Results

All numbers below are produced by the [`benchmark` workflow](.github/workflows/benchmark.yml) on a GitHub-hosted Linux runner, so every run uses the same hardware class and results stay comparable across engine versions. **Do not edit them by hand** — see [Updating the results](#updating-the-results).

<!-- BENCHMARK:START -->
<!-- BENCHMARK:END -->

## Analysis

### Where Karate wins

Sub-1KB scripts in fresh contexts, and context creation. Against **tuned** competitors that is roughly 1–2x on short-script evaluation, and one to two orders of magnitude on context creation. Against the *defaults* the gap looks far larger, which is why the defaults alone would have been a misleading thing to publish.

Karate scripts are short snippets scattered through feature files, so context creation dominates total execution time. That is the regime the engine is built for and the regime it wins.

### Where Karate loses

- **Anything beyond a few KB.** GraalJS with a shared `Engine` overtakes Karate at around 5KB and is several times faster by 100KB.
- **Context reuse.** With context creation removed, GraalJS is several times faster for pure execution, and tuned Rhino is ahead too.
- **GraalVM proper.** With JVMCI enabled GraalJS would JIT-compile hot paths. On a stock JVM it runs interpreted, which is what this measures.

### Reading the context-creation number

Treat it as an architectural difference, not like-for-like. The engines defer different amounts of work: Karate's standard library lives in lazily-initialised JVM-wide singletons, so `new Engine()` allocates very little; Rhino's `initStandardObjects()` eagerly builds a full global environment; GraalJS does not initialise the JS language until something is evaluated, so its figure understates true first-use cost. Sharing an immutable standard library across contexts is a genuine design win here, and a shared GraalJS `Engine` is the closest analogue — which is why it is measured.

This row is also the noisiest in the suite. Treat its exact multiple as approximate.

### Script sizes in practice

Inline expressions in a feature file run to a few hundred bytes. A `karate-config.js` is normally 0.5–1.5 KB; a substantial JS utility file is a few KB. Tens of KB is a rare outlier — jQuery minified is 87 KB.

So GraalJS pulls ahead at sizes above where Karate normally operates. That is the honest scope of Karate's advantage: real, and narrow.

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

### Engine configuration

Five configurations are measured. Each competitor appears as its default and tuned.

| Column | What it is |
|---|---|
| `Karate` | `new Engine().eval(source)` |
| `Rhino` | Rhino's default — generates JVM bytecode and defines a class per evaluation |
| `Rhino-int` | `cx.setInterpretedMode(true)` — no bytecode generation |
| `Graal` | `Context.create()` — a private `Engine` per `Context` |
| `Graal-shared` | one `Engine.create()` shared by every `Context`, so runtime setup and parsed-code cache are reused |

Both tuning changes are one-liners and both are large:

- **Rhino interpreted mode is several times faster than Rhino's default** for parse-once-run-once. The default pays bytecode compilation on every evaluation and never amortises it. If you embed Rhino for short scripts, this is the single highest-value setting you can change.
- **A shared GraalJS `Engine` cuts context-creation cost substantially** and dominates on larger scripts. Each `Context` still gets isolated globals, so this is not a semantic shortcut.

GraalJS runs interpreted throughout — a stock JVM has no JVMCI, so it never JIT-compiles. On GraalVM these numbers would differ.

`Karate vs best` always scores Karate against whichever non-Karate configuration was fastest for that row.

### Other caveats

- GraalJS caches parsed scripts, giving it an advantage when the same script is evaluated repeatedly on one context — hence the `no-cache` variant.
- All three engines share a single JVM, are measured in a fixed order, and are timed with `System.nanoTime()` rather than JMH. Median-of-5 with per-workload warmup absorbs most of this, but these are not JMH-grade figures and small differences should not be over-read.
- Karate's engine is optimized for the API testing and LLM REPL use case: many small, independent script evaluations with fresh contexts.

### Scope

This is a **cross-engine comparison at a point in time** — it answers "how does `karate-js` compare to the alternatives", not "did `karate-js` get faster this release". Version-over-version tracking of the engine itself is done separately in the [karate](https://github.com/karatelabs/karate) repo, where a change can be bisected against commits. Don't read run-to-run movement here as a regression signal.

Historical CSVs live in [`results/`](results/).
