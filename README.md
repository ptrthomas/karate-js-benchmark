# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs), for the workload Karate cares about: **many small scripts, each evaluated in a fresh context**.

Every competing engine is measured in its default configuration **and tuned**. That turned out to matter more than the engine choice itself: a properly configured Rhino is still faster than `karate-js` on the short-script rows here, while Rhino at its defaults looks several times slower. Karate is scored against whichever tuned competitor is fastest.

That makes this repo useful beyond Karate. If you embed Rhino or GraalJS yourself, the default-vs-tuned columns show what a one-line configuration change is worth for short-script workloads — see [Engine configuration](#engine-configuration).

`karate-js` is built **from source** off [`karatelabs/karate`](https://github.com/karatelabs/karate) `main` — not from the Maven Central release, which lags `main` considerably.

## Results

All numbers below are produced by the [`benchmark` workflow](.github/workflows/benchmark.yml) on a GitHub-hosted Linux runner, so every run uses the same hardware class and results stay comparable across engine versions. **Do not edit them by hand** — see [Updating the results](#updating-the-results).

<!-- BENCHMARK:START -->

### Test Environment

| | |
|---|---|
| Machine | AMD EPYC 7763 64-Core Processor, 4 vCPU, 16 GB (ubuntu24, X64) |
| Java | 21.0.11 (OpenJDK 64-Bit Server VM) |
| Karate JS | 2.1.2.RC2, built from source at `70c1aa7` |
| Rhino | 1.9.1 |
| GraalJS | 25.2.4 (Community Edition) |
| Run | [GitHub Actions run 4](https://github.com/ptrthomas/karate-js-benchmark/actions/runs/31318907659) |

Each competitor appears twice — in its default configuration, and tuned for this workload. **Karate vs best** compares Karate against whichever non-Karate configuration was fastest for that row, so Karate is never flattered by a competitor's suboptimal default.

### Context Creation Overhead

Cost of a fresh set of globals, nothing evaluated. The engines defer different amounts of work, so read this as an architectural difference rather than a like-for-like measurement — see the Analysis section.

| | Karate (µs) | Rhino (µs) | Rhino-int (µs) | Rhino-best (µs) | Graal (µs) | Graal-shared (µs) | Karate vs best |
|---|---|---|---|---|---|---|---|
| Context Create | 0.06 | 89.53 | 73.00 | 0.07 | 91.60 | 8.13 | 1.2x faster (Rhino-best) |

### Script Evaluation (Fresh Context)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| Arithmetic | 123 | 0.0940 | 0.9539 | 0.1787 | 0.0667 | 0.6992 | 0.2813 | 1.4x slower (Rhino-best) |
| Strings | 93 | 0.0475 | 0.7244 | 0.1221 | 0.0303 | 0.5416 | 0.2548 | 1.6x slower (Rhino-best) |
| Objects | 329 | 0.1169 | 1.4710 | 0.2010 | 0.0930 | 0.7104 | 0.3555 | 1.3x slower (Rhino-best) |
| Functions | 247 | 0.1341 | 1.1124 | 0.1625 | 0.0630 | 0.6345 | 0.2967 | 2.1x slower (Rhino-best) |
| Mixed | 576 | 0.2750 | 1.5816 | 0.2296 | 0.1356 | 0.7344 | 0.3946 | 2.0x slower (Rhino-best) |

### Context Reuse (Pure Execution Speed)

| Workload | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| Mixed-Reuse | 576 | 0.2726 | 1.6428 | 0.1711 | 0.1499 | 0.0756 | 0.0711 | 3.8x slower (Graal-shared) |
| Mixed-NoCache | 576 | 0.2848 | 1.6548 | 0.1476 | 0.1501 | 0.1794 | 0.1403 | 2.0x slower (Graal-shared) |

### Large Script Scaling (Fresh Context per Eval)

| Target | Bytes | Karate (ms) | Rhino (ms) | Rhino-int (ms) | Rhino-best (ms) | Graal (ms) | Graal-shared (ms) | Karate vs best |
|---|---|---|---|---|---|---|---|---|
| 1KB | 1133 | 0.1763 | 2.6983 | 0.2188 | 0.1065 | 1.1567 | 0.3504 | 1.7x slower (Rhino-best) |
| 5KB | 5378 | 0.4348 | 10.0579 | 0.5225 | 0.4265 | 1.1317 | 0.7253 | 1.0x slower (Rhino-best) |
| 10KB | 10728 | 0.8473 | 18.5890 | 0.9270 | 0.8271 | 1.5015 | 1.1440 | 1.0x slower (Rhino-best) |
| 50KB | 53533 | 4.5306 | 85.5510 | 4.1624 | 4.7276 | 5.1822 | 4.6387 | 1.1x slower (Rhino-int) |
| 100KB | 107533 | 9.1912 | 177.1715 | 9.3590 | 9.1355 | 11.5352 | 12.3493 | 1.0x slower (Rhino-best) |

<!-- BENCHMARK:END -->

## Analysis

### The headline: a tuned Rhino still leads the core short-script rows — by less each release

On the fresh-context rows this benchmark exists for, Rhino in interpreted mode with a shared sealed root scope (`Rhino-best`) remains ahead of `karate-js` — 1.3–2.1x depending on the row as of 2.1.2.RC2. The rest of the picture is no longer one-sided: context creation is now marginally *faster* than a prototyped Rhino scope, and from 5KB of script upward the two engines are at parity (1.0–1.1x), with `karate-js` also ahead of GraalJS there. The core-row gap is closing measurably: between 2.1.2.RC1 and RC2 (both CI runs, CSVs in [`results/`](results/)) the five fresh-workload ratios moved from 2.09 / 1.98 / 1.48 / 2.42 / 2.34 to 1.41 / 1.56 / 1.26 / 2.13 / 2.03 — a geometric-mean gap of ~2.0x down to ~1.64x. GraalJS with a shared `Engine` wins the context-reuse rows outright.

Earlier versions of this README claimed Karate was 2–8x faster than Rhino and ~1300x faster at context creation. Both came from benchmarking Rhino in configurations no informed embedder would use: compiled mode, which generates JVM bytecode and defines a class for every evaluation, and a full `initStandardObjects` per evaluation, which Rhino's own docs warn is expensive. Those were measurement artifacts, not engine differences.

### Why the comparison changed

Three corrections, all of which moved results against Karate:

1. **Rhino got its documented embedding configuration.** Rhino's own docs call `initStandardObjects` *"an expensive method to call"* and recommend a shared sealed scope that each evaluation cheaply prototypes off. That is structurally the same design as `karate-js` — an immutable shared standard library behind a cheap per-eval global — so it is the fair comparison, not merely the fast one.
2. **Parse caching was defeated.** Every evaluation now gets unique source text. GraalJS with a shared `Engine` caches parsed sources across contexts; `karate-js` has no source cache and re-parses every time. Benchmarking identical text let GraalJS skip work Karate always does, which had made it look several times faster on large scripts. It isn't.
3. **Java interop was equalised.** Interop is off in `karate-js` here and denied by default in GraalJS, so Rhino now uses `initSafeStandardObjects` instead of being charged for LiveConnect setup the others never perform.

The third correction is what erased Karate's context-creation lead. That row was the original headline of this benchmark at ~1300x; once Rhino builds its standard objects once and shares them, a prototyped Rhino scope is actually cheaper than `new Engine()`.

### What this does and doesn't say

It says a well-configured Rhino is still the fastest engine for this benchmark's core rows — currently by 1.3–2.1x — while `karate-js` matches it from 5KB of script upward and edges it on context creation.

It does not say Karate is slow. The absolute numbers are tens of microseconds for a typical script; on a test that also makes an HTTP call, the difference is noise. And it says nothing about the reasons `karate-js` was written, which were never primarily about speed.

### Why `karate-js` exists

Speed was never the only goal, and these results do not change the reasons it was written:

- **Java interop as a first-class concept.** Karate is a Java tool whose scripts constantly cross into Java. `karate-js` is designed around that boundary rather than bolting it on. Note that interop is *disabled* in this benchmark for all three engines, so none of these numbers reflect it.
- **Insulating Karate users from JavaScript-engine churn.** Karate has been bitten by this twice — Nashorn's removal from the JDK, and GraalJS's packaging and runtime changes. Owning the engine means a JDK or vendor decision cannot break Karate users. GraalJS losing stock-JDK JIT support in Truffle 25.1, described above, is the same pattern happening again.
- **A fit-for-purpose engine.** `karate-js` is tuned for Karate's actual shape: many small scripts, fresh contexts, and AST caching where it pays — `karate-config.js`, for instance, is cached rather than re-parsed.
- **A deep event and introspection framework.** The interpreter emits fine-grained events as it runs — statement and expression entry/exit, which arm of a branch was taken, the concrete operands of every comparison, dynamic property reads, and variable binds — to a listener the embedder installs. That makes AOP-like capabilities possible without bytecode weaving or a separate static-analysis pass: branch-level code coverage, execution tracing, debuggers, and tooling that can study a script by *observing* its real behaviour rather than reasoning about its source — and then visualise it.

  This is part of why a tuned Rhino wins above. Firing events at that granularity, and keeping the AST walkable and richly annotated so it can be analysed and rendered, costs something on every evaluation. That trade was made deliberately.

We continue to optimise the engine — the RC1 → RC2 movement above is that work landing. On the evidence here it is fast in the range that matters for Karate users, and that is the bar it is held to.

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
./build.sh --quick                 # fast directional pass: Karate vs Rhino-best only,
                                   # fresh workloads, reduced iterations - not for publication
```

It fails fast if `<karate.version>` in `pom.xml` does not match the version `karate` `main` currently builds — bump the pom when karate's version moves.

Without the source build (against whatever `karate-js` is already in `~/.m2`):

```bash
mvn compile exec:java
```

Each run writes a CSV and a markdown block (`target/benchmark.csv` and `target/benchmark.md`). The markdown block is what gets spliced into the Results section above, by hand — see [Updating the results](#updating-the-results).

### Run on CI

The canonical run is `workflow_dispatch` on the [`benchmark` workflow](.github/workflows/benchmark.yml) — on demand, not scheduled. It takes a `karate_ref` input if you want to benchmark a branch other than `main`.

The workflow does **not** write to this repo. It publishes the results to the run's job summary (rendered, plus a paste-ready block and the full console output) and as a `benchmark-results` artifact.

### Updating the results

Refreshing the Results section is a deliberate manual commit, so every numbers change in git history traces back to a person and a run id:

```bash
# 1. check the version pin first - CI fails fast if these disagree
grep karate.version pom.xml
#    compare against <version> in karatelabs/karate's root pom.xml on main,
#    and update pom.xml if it has moved

# 2. run it, wait for green
gh workflow run benchmark.yml --ref main
gh run list --workflow=benchmark.yml --limit 1        # note the run id
gh run watch <run-id> --exit-status

# 3. splice the generated block into the Results section
gh run download <run-id> -n benchmark-results -D /tmp/bench
./etc/update-readme.py README.md /tmp/bench/benchmark.md
cp /tmp/bench/benchmark.csv results/benchmark-<karate-version>-ci.csv

# 4. commit both, citing the run id
```

**Then re-read the Analysis section and fix it to match.** This is the step that gets missed. `update-readme.py` only rewrites what sits between the markers; the Analysis and Notes prose is hand-written, sits *outside* them, and quotes specific multiples ("roughly 1.5–2.4x", "around 2x on context creation") plus a conclusion about which engine wins. If the new numbers move, that prose silently becomes wrong, and wrong in the most embarrassing direction — a published claim contradicted by the table directly above it.

So after splicing, check at minimum:

- Does `Karate vs best` still name the same winner on every row? If the winning configuration changed, the Analysis headline is wrong.
- Do the quoted ranges still bracket the actual numbers?
- Does the intro paragraph still describe the right outcome?

The numbers are allowed to say whatever they say. Publish them either way — that is the entire point of this repo, and the reason the current README leads with `karate-js` losing.

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
- **Java interop is off for all three engines.** A bare `new Engine()` installs no `ExternalBridge` — Karate's Java bridge is wired up by `karate-core`, not by `karate-js` — and GraalJS denies host access by default. Rhino therefore uses `initSafeStandardObjects`, which skips the LiveConnect interop objects, rather than `initStandardObjects`, which would have charged Rhino for setup the other two never perform.

### Scope

This is a **cross-engine comparison at a point in time** — it answers "how does `karate-js` compare to the alternatives", not "did `karate-js` get faster this release". Version-over-version tracking of the engine itself is done separately in the [karate](https://github.com/karatelabs/karate) repo, where a change can be bisected against commits. Don't read run-to-run movement here as a regression signal.

Historical CSVs live in [`results/`](results/).
