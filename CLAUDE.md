# CLAUDE.md — working in `karate-js-benchmark`

A public benchmark of Karate Labs' own JS engine against Rhino and GraalJS. Karate Labs
owns `karate-js`, so **this repo publishes a result about our own product**. That single
fact drives everything below.

## The prime directive

**Publish what the numbers say, including when they are unflattering.** The current README
leads with `karate-js` losing to a properly tuned Rhino on every row. That is deliberate and
approved. Do not soften it, do not re-tune the comparison until Karate wins, and do not
quietly drop a losing row.

If a change you make moves results in Karate's favour, be more suspicious of it, not less —
every measurement flaw found in this repo so far had that direction.

## Read this before touching the benchmark

Three flaws were found here, all favouring Karate. Do not reintroduce them:

1. **Competitors must be tuned, not left at defaults.** Rhino's default compiled mode
   generates bytecode per evaluation; that is not how anyone embeds it for short scripts.
   Every competitor appears as default *and* tuned, and Karate is scored against the fastest
   tuned one (`Karate vs best`).
2. **No engine may serve a cached parse.** `karate-js` has no source cache; GraalJS with a
   shared `Engine` does. Every fresh-context eval gets unique source text via `unique()`.
   Remove that and GraalJS gets credit for work Karate performs.
3. **Java interop must be equal.** A bare `new Engine()` installs no `ExternalBridge`, and
   GraalJS denies host access by default — so Rhino uses `initSafeStandardObjects`, not
   `initStandardObjects`, which would charge it for LiveConnect setup nobody else pays.

`verifyAllConfigsAgree()` runs before any timing and aborts if the configurations disagree
on any workload. If you add a configuration it must pass that gate — a config that silently
fails would otherwise be reported as the fastest.

## Where numbers come from

CI only. `karate-js` is built from source off `karatelabs/karate` `main` (never Maven
Central, which lags badly), and `<karate.version>` in `pom.xml` must match karate's root
`<version>` — both `build.sh` and CI fail fast if not.

`mvn -pl karate-js install` needs **`-am`**. Without it the `karate-parent` pom is not
installed, and RC versions of it are not on Central, so a cold machine cannot resolve
`karate-js`. This works on a warm `~/.m2` and fails everywhere else.

The workflow never writes to the repo. Refreshing the README is a manual commit citing a run
id — see "Updating the results" in README.md, and heed its warning about the hand-written
Analysis prose going stale when the spliced numbers move.

## Local runs

`./build.sh` (needs a karate checkout at `../karate` or `KARATE_SRC=`). Local numbers are
noisier than CI and are not what gets published. `build.sh` runs `mvn clean`, so anything
you care about in `target/` should be archived first — it already archives the previous
`benchmark.csv` into `results/`.

## Style

Terse. The README is written for a skeptical reader who may arrive from Hacker News: state
the method, state the caveat, cite the primary source. No marketing voice, no hedging around
a bad result.
