# Karate JS Engine Benchmark

Performance benchmark comparing [Karate's JavaScript engine](https://github.com/karatelabs/karate/tree/main/karate-js) against [Mozilla Rhino](https://github.com/mozilla/rhino) and [GraalJS](https://github.com/oracle/graaljs), for the workload Karate cares about: **many small scripts, each evaluated in a fresh context**.

Every competing engine is measured in its default configuration **and tuned**. That turned out to matter more than the engine choice itself: Rhino at its defaults looks an order of magnitude slower than its own documented embedding configuration. Karate is scored against whichever tuned competitor is fastest — and as of `2.1.3.RC1` it sits at **parity with that frontier**: even or ahead on four of the five short-script rows and on every large-script size, behind only on the mixed workload (1.3x).

That makes this repo useful beyond Karate. If you embed Rhino or GraalJS yourself, the default-vs-tuned columns show what a one-line configuration change is worth for short-script workloads — see [Engine configuration](#engine-configuration).

`karate-js` is built **from source** off [`karatelabs/karate`](https://github.com/karatelabs/karate) `main` — not from the Maven Central release, which lags `main` considerably.

## Results

All numbers below are produced by [`etc/ec2-benchmark.sh`](etc/ec2-benchmark.sh) on a dedicated EC2 instance of a **pinned type** (`c6a.2xlarge` — the same EPYC CPU family as GitHub's hosted runners, so the karate-vs-best *ratios* stay comparable with the archived `results/*-ci.csv` files). A dedicated host removes the noisy-neighbour variance that made shared CI runners untrustworthy for sub-2x comparisons, the 8 vCPU size keeps JIT compiler threads from racing the measured thread (on 4 vCPU hosts — GitHub runners included — that race made single rows swing 2–4x between runs; the script header records the measurement), and each publish is the **median of three runs**, selected by the fresh-workload geometric mean. **Do not edit the numbers by hand** — see [Updating the results](#updating-the-results).

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

## Analysis

### The headline: parity with a tuned Rhino on the core rows — one workload still behind

On the fresh-context rows this benchmark exists for, `karate-js` at `2.1.3.RC1` has reached **parity with the tuned frontier**: it is even-or-ahead of Rhino in its best configuration on Arithmetic, Strings, Objects and Functions, ahead on every large-script size from 1KB to 100KB, and behind only on Mixed (1.3x — the row that combines object literals, array building and property access, which is where the remaining engine work is aimed). The gap's trajectory across releases (fresh-workload ratios, Arithmetic / Strings / Objects / Functions / Mixed; CSVs in [`results/`](results/)): 2.1.2.RC1 **2.09 / 1.98 / 1.48 / 2.42 / 2.34** → RC2 **1.41 / 1.56 / 1.26 / 2.13 / 2.03** → 2.1.3.RC1 **0.90 / 1.00 / 0.95 / 0.91 / 1.33** — a geometric-mean gap of ~2.0x → ~1.64x → **~1.0x**. (The first two are CI runs, the third the EC2 instrument; ratios are hardware-class-comparable, and the CI era's per-row noise means the older per-row digits are approximate.) GraalJS with a shared `Engine` still wins the context-reuse rows outright.

Earlier versions of this README claimed Karate was 2–8x faster than Rhino and ~1300x faster at context creation. Both came from benchmarking Rhino in configurations no informed embedder would use: compiled mode, which generates JVM bytecode and defines a class for every evaluation, and a full `initStandardObjects` per evaluation, which Rhino's own docs warn is expensive. Those were measurement artifacts, not engine differences.

### Why the comparison changed

Three corrections, all of which moved results against Karate:

1. **Rhino got its documented embedding configuration.** Rhino's own docs call `initStandardObjects` *"an expensive method to call"* and recommend a shared sealed scope that each evaluation cheaply prototypes off. That is structurally the same design as `karate-js` — an immutable shared standard library behind a cheap per-eval global — so it is the fair comparison, not merely the fast one.
2. **Parse caching was defeated.** Every evaluation now gets unique source text. GraalJS with a shared `Engine` caches parsed sources across contexts; `karate-js` has no source cache and re-parses every time. Benchmarking identical text let GraalJS skip work Karate always does, which had made it look several times faster on large scripts. It isn't.
3. **Java interop was equalised.** Interop is off in `karate-js` here and denied by default in GraalJS, so Rhino now uses `initSafeStandardObjects` instead of being charged for LiveConnect setup the others never perform.

The third correction is what erased Karate's context-creation lead. That row was the original headline of this benchmark at ~1300x; once Rhino builds its standard objects once and shares them, a prototyped Rhino scope is actually cheaper than `new Engine()`.

### What this does and doesn't say

It says `karate-js` and a well-configured Rhino are now interchangeable on speed for this benchmark's core rows — each wins some rows, the geometric mean is ~1.0x, and only the Mixed workload (1.3x) still separates them. For context reuse with identical source, GraalJS's shared-`Engine` caching remains the fastest option.

It does not say Karate is the fastest possible engine, and it never needed to say Karate was slow. The absolute numbers are tens of microseconds for a typical script; on a test that also makes an HTTP call, the difference is noise. And it says nothing about the reasons `karate-js` was written, which were never primarily about speed.

### Why `karate-js` exists

Speed was never the only goal, and these results do not change the reasons it was written:

- **Java interop as a first-class concept.** Karate is a Java tool whose scripts constantly cross into Java. `karate-js` is designed around that boundary rather than bolting it on. Note that interop is *disabled* in this benchmark for all three engines, so none of these numbers reflect it.
- **Insulating Karate users from JavaScript-engine churn.** Karate has been bitten by this twice — Nashorn's removal from the JDK, and GraalJS's packaging and runtime changes. Owning the engine means a JDK or vendor decision cannot break Karate users. GraalJS losing stock-JDK JIT support in Truffle 25.1, described above, is the same pattern happening again.
- **A fit-for-purpose engine.** `karate-js` is tuned for Karate's actual shape: many small scripts, fresh contexts, and AST caching where it pays — `karate-config.js`, for instance, is cached rather than re-parsed.
- **A deep event and introspection framework.** The interpreter emits fine-grained events as it runs — statement and expression entry/exit, which arm of a branch was taken, the concrete operands of every comparison, dynamic property reads, and variable binds — to a listener the embedder installs. That makes AOP-like capabilities possible without bytecode weaving or a separate static-analysis pass: branch-level code coverage, execution tracing, debuggers, and tooling that can study a script by *observing* its real behaviour rather than reasoning about its source — and then visualise it.

  This costs something on every evaluation — firing events at that granularity, and keeping the AST walkable and richly annotated so it can be analysed and rendered, is overhead a leaner interpreter never pays. That trade was made deliberately, which makes the current parity-with-tuned-Rhino result notable: the introspection machinery is no longer buying a measurable speed penalty on these rows.

We continue to optimise the engine — the RC1 → RC2 → 2.1.3 movement above is that work landing (profiling-driven: dense slot frames for locals and confined block bindings, allocation-free scope exit, single-pass property probes, and array fast paths). On the evidence here it is fast in the range that matters for Karate users, and that is the bar it is held to.

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

### Run on EC2 (canonical)

The canonical run is [`etc/ec2-benchmark.sh`](etc/ec2-benchmark.sh): it launches a `c6a.xlarge` on-demand instance, ships your **local** karate checkout (so unpushed engine work benchmarks correctly), builds `karate-js` from source there, runs the benchmark three times, selects the **median run** by fresh-workload geometric mean, copies the artifacts back to `target/ec2/`, and terminates everything it created — on success or failure. Roughly 30–45 minutes and ~$0.15 of instance time.

Shared CI runners were retired as the canonical source because their numbers cannot be trusted at the margins this table now measures: spikes and noisy neighbours have made back-to-back runs disagree by more than 2x on a row neither build touched. A dedicated host of a pinned instance type keeps runs comparable *and* quiet. The [`benchmark` workflow](.github/workflows/benchmark.yml) still exists as an ad-hoc convenience (`workflow_dispatch`), but its numbers are not published here.

### Updating the results

Refreshing the Results section is a deliberate manual commit, so every numbers change in git history traces back to a person and a run:

```bash
# 1. check the version pin first - the script fails fast (before any AWS spend) if these disagree
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
