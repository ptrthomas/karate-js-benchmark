/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.benchmark;

import io.karatelabs.js.Engine;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Performance benchmark comparing Karate's JavaScript engine against Rhino and GraalJS.
 * <p>
 * Every competing engine is measured in BOTH its default configuration and a tuned one,
 * because for this workload - many small scripts, each in a fresh context - the defaults
 * are not the engines' best showing, and publishing only the defaults would misrepresent
 * them. See {@link #CONFIGS}.
 * <p>
 * Run with: mvn compile exec:java
 */
public class JsEngineBenchmark {

    // Executable JavaScript samples for benchmarking
    private static final String JS_ARITHMETIC = """
        let result = 0;
        for (let i = 0; i < 100; i++) {
            result += i * 2 + i / 2 - i % 7;
            result = result * 1.01;
        }
        result;
        """;

    private static final String JS_STRINGS = """
        let s = '';
        for (let i = 0; i < 50; i++) {
            s += 'item' + i + ',';
        }
        s.split(',').length;
        """;

    private static final String JS_OBJECTS = """
        let users = [];
        for (let i = 0; i < 50; i++) {
            users.push({
                id: i,
                name: 'user' + i,
                email: 'user' + i + '@test.com',
                active: i % 2 === 0,
                scores: [i, i * 2, i * 3]
            });
        }
        let active = users.filter(u => u.active);
        let total = active.reduce((sum, u) => sum + u.scores[0], 0);
        total;
        """;

    private static final String JS_FUNCTIONS = """
        function fibonacci(n) {
            if (n <= 1) return n;
            return fibonacci(n - 1) + fibonacci(n - 2);
        }
        function factorial(n) {
            if (n <= 1) return 1;
            return n * factorial(n - 1);
        }
        let fib = fibonacci(10);
        let fact = factorial(8);
        fib + fact;
        """;

    private static final String JS_MIXED = """
        function processData(items) {
            let result = { sum: 0, count: 0, values: [] };
            for (let i = 0; i < items.length; i++) {
                let item = items[i];
                if (item.active) {
                    result.sum += item.value;
                    result.count++;
                    result.values.push(item.value * 2);
                }
            }
            result.average = result.count > 0 ? result.sum / result.count : 0;
            return result;
        }
        let data = [];
        for (let i = 0; i < 100; i++) {
            data.push({ id: i, value: i * 10, active: i % 3 !== 0 });
        }
        let output = processData(data);
        output.sum + output.average;
        """;

    /**
     * A GraalJS Engine shared across Contexts. A shared Engine lets GraalJS reuse its
     * runtime setup and parsed-code cache between contexts instead of rebuilding both
     * every time - the closest GraalJS analogue to how Karate shares one immutable
     * standard library across Engine instances. Each Context is still an isolated set
     * of globals, so the comparison stays honest.
     */
    private static final org.graalvm.polyglot.Engine SHARED_GRAAL = org.graalvm.polyglot.Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    /**
     * One engine configuration under test. {@link #evalFresh} must evaluate the source in
     * a FRESH set of globals - that is the scenario the benchmark is built around.
     */
    private interface Config {
        String label();

        void evalFresh(String source);

        /** Create and immediately discard a context, with no evaluation. */
        void createContext();

        /** A reusable context, for the context-reuse benchmark. Null if not applicable. */
        default Reusable reusable() {
            return null;
        }
    }

    /** A context held open across many evaluations. */
    private interface Reusable extends AutoCloseable {
        void eval(String source);

        @Override
        default void close() {
        }
    }

    private static final Config KARATE = new Config() {
        public String label() {
            return "Karate";
        }

        public void evalFresh(String source) {
            new Engine().eval(source);
        }

        public void createContext() {
            new Engine();
        }

        public Reusable reusable() {
            Engine engine = new Engine();
            return engine::eval;
        }
    };

    /** Rhino in its DEFAULT mode: generates JVM bytecode and defines a class per evaluation. */
    private static final Config RHINO = rhino(false, "Rhino");

    /**
     * Rhino in interpreted mode - skips bytecode generation and class definition entirely.
     * For parse-once-run-once this is normally much faster than Rhino's default, which is
     * why publishing only the default would understate Rhino.
     */
    private static final Config RHINO_INTERPRETED = rhino(true, "Rhino-int");

    private static Config rhino(boolean interpreted, String label) {
        return new Config() {
            public String label() {
                return label;
            }

            public void evalFresh(String source) {
                org.mozilla.javascript.Context cx = enter();
                try {
                    org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
                    cx.evaluateString(scope, source, "benchmark", 1, null);
                } finally {
                    org.mozilla.javascript.Context.exit();
                }
            }

            public void createContext() {
                org.mozilla.javascript.Context cx = enter();
                try {
                    cx.initStandardObjects();
                } finally {
                    org.mozilla.javascript.Context.exit();
                }
            }

            public Reusable reusable() {
                org.mozilla.javascript.Context cx = enter();
                org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
                return new Reusable() {
                    public void eval(String source) {
                        cx.evaluateString(scope, source, "benchmark", 1, null);
                    }

                    public void close() {
                        org.mozilla.javascript.Context.exit();
                    }
                };
            }

            private org.mozilla.javascript.Context enter() {
                org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
                cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
                cx.setInterpretedMode(interpreted);
                return cx;
            }
        };
    }

    /** GraalJS with a private Engine per Context - what you get from Context.create(). */
    private static final Config GRAAL = graal(false, "Graal");

    /** GraalJS with one Engine shared across all Contexts. */
    private static final Config GRAAL_SHARED = graal(true, "Graal-shared");

    private static Config graal(boolean shared, String label) {
        return new Config() {
            public String label() {
                return label;
            }

            public void evalFresh(String source) {
                try (org.graalvm.polyglot.Context ctx = build()) {
                    ctx.eval("js", source);
                }
            }

            public void createContext() {
                build().close();
            }

            public Reusable reusable() {
                org.graalvm.polyglot.Context ctx = build();
                return new Reusable() {
                    public void eval(String source) {
                        ctx.eval("js", source);
                    }

                    public void close() {
                        ctx.close();
                    }
                };
            }

            private org.graalvm.polyglot.Context build() {
                org.graalvm.polyglot.Context.Builder b = org.graalvm.polyglot.Context.newBuilder("js");
                // engine.* options belong on the Engine when one is shared, on the Context otherwise
                return shared ? b.engine(SHARED_GRAAL).build()
                        : b.option("engine.WarnInterpreterOnly", "false").build();
            }
        };
    }

    /** Column order for every table and for the CSV. Karate must stay first. */
    private static final List<Config> CONFIGS = List.of(
            KARATE, RHINO, RHINO_INTERPRETED, GRAAL, GRAAL_SHARED);

    private static final List<BenchmarkResult> results = new ArrayList<>();

    // versions come from the Maven-filtered benchmark.properties, never a hardcoded constant
    private static final Properties VERSIONS = loadVersions();

    private static Properties loadVersions() {
        Properties props = new Properties();
        try (InputStream is = JsEngineBenchmark.class.getResourceAsStream("/benchmark.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // fall through, versions just print as 'unknown'
        }
        return props;
    }

    private static String version(String key) {
        return VERSIONS.getProperty(key, "unknown");
    }

    public static void main(String[] args) {
        String csvFile = args.length > 0 ? args[0] : null;

        printHeader();

        System.out.println("Warming up all engines...");
        warmup();
        System.out.println("Warmup complete.\n");

        runContextCreationBenchmark();
        runEngineComparison();

        String outputFile = csvFile != null ? csvFile : "target/benchmark.csv";
        writeCsv(outputFile);
        writeMarkdown(outputFile.replaceFirst("\\.csv$", "") + ".md");
    }

    private static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              Karate JS Engine vs Rhino vs GraalJS Benchmark                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Engines under test:");
        System.out.println("  - Karate        Karate's JS engine v" + version("karate.version"));
        System.out.println("  - Rhino         Mozilla Rhino v" + version("rhino.version") + ", ES6, compiled mode (its DEFAULT)");
        System.out.println("  - Rhino-int     the same, interpreted mode - no bytecode generation");
        System.out.println("  - Graal         GraalJS v" + version("graaljs.version") + ", private Engine per Context (the DEFAULT)");
        System.out.println("  - Graal-shared  the same, one Engine shared across Contexts");
        System.out.println();
        System.out.println("Java: " + System.getProperty("java.version") + " | OS: " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + " | CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println();
        System.out.println("GraalJS runs interpreted - a stock JVM has no JVMCI, so it never JIT-compiles.");
        System.out.println("Both competitors are shown tuned as well as default; 'Best' is the fastest");
        System.out.println("non-Karate configuration, which is what Karate is fairly compared against.");
        System.out.println();
    }

    private static void warmup() {
        for (int i = 0; i < 100; i++) {
            for (Config c : CONFIGS) {
                c.evalFresh(JS_ARITHMETIC);
            }
        }
    }

    /** Median of 5 timed runs, in ms per iteration. */
    private static double measure(int iterations, Runnable body) {
        long[] times = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                body.run();
            }
            times[run] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        return (times[2] / (double) iterations) / 1_000_000.0;
    }

    private static void runContextCreationBenchmark() {
        banner("CONTEXT CREATION OVERHEAD");
        System.out.println("Cost of standing up a fresh set of globals, with nothing evaluated.");
        System.out.println("NOTE: the engines defer different amounts of work here - see the README.");
        System.out.println();

        int iterations = 5000;
        for (int i = 0; i < 500; i++) {
            for (Config c : CONFIGS) {
                c.createContext();
            }
        }

        double[] values = new double[CONFIGS.size()];
        for (int i = 0; i < CONFIGS.size(); i++) {
            Config c = CONFIGS.get(i);
            values[i] = measure(iterations, c::createContext);
        }

        printRow("Context Create", values, true);
        endBanner();
        results.add(new BenchmarkResult("ContextCreate", 0, values));
    }

    private static void runEngineComparison() {
        banner("SCRIPT EVALUATION - Fresh context per eval");
        printHeaderRow("Workload");
        runComparison("Arithmetic", JS_ARITHMETIC, 2000);
        runComparison("Strings", JS_STRINGS, 2000);
        runComparison("Objects", JS_OBJECTS, 1000);
        runComparison("Functions", JS_FUNCTIONS, 500);
        runComparison("Mixed", JS_MIXED, 500);
        endBanner();

        runContextReuseBenchmark();
        runLargeScriptBenchmark();
    }

    private static void runComparison(String name, String source, int iterations) {
        for (int i = 0; i < 50; i++) {
            for (Config c : CONFIGS) {
                c.evalFresh(source);
            }
        }
        double[] values = new double[CONFIGS.size()];
        for (int i = 0; i < CONFIGS.size(); i++) {
            Config c = CONFIGS.get(i);
            values[i] = measure(iterations, () -> c.evalFresh(source));
        }
        printRow(name, values, false);
        results.add(new BenchmarkResult(name, source.length(), values));
    }

    private static void runContextReuseBenchmark() {
        banner("CONTEXT REUSE - Same context, multiple evals");
        System.out.println("Pure execution speed, with context creation removed. Karate's advantage");
        System.out.println("comes from context creation, so this is where it is expected to lose.");
        System.out.printf("Script size: JS_MIXED = %d bytes (%.2f KB)%n", JS_MIXED.length(), JS_MIXED.length() / 1024.0);
        System.out.println();
        printHeaderRow("Workload");

        // IIFE-wrapped so repeated evals on one context don't hit 'let' redeclaration errors
        String wrappedMixed = "(function() {\n" + JS_MIXED + "\n})();";
        String wrappedArithmetic = "(function() {\n" + JS_ARITHMETIC + "\n})();";
        int iterations = 1000;

        double[] reuse = new double[CONFIGS.size()];
        double[] noCache = new double[CONFIGS.size()];
        for (int i = 0; i < CONFIGS.size(); i++) {
            Config c = CONFIGS.get(i);
            try (Reusable warm = c.reusable()) {
                for (int j = 0; j < 100; j++) {
                    warm.eval(wrappedArithmetic);
                }
            }
            try (Reusable r = c.reusable()) {
                reuse[i] = measure(iterations, () -> r.eval(wrappedMixed));
            }
            // a distinct script each time, to defeat parsed-source caching
            String prefix = "(function() { var _seed = ";
            String suffix = ";\n" + JS_MIXED + "\n})();";
            try (Reusable r = c.reusable()) {
                int[] seed = {0};
                noCache[i] = measure(iterations, () -> r.eval(prefix + seed[0]++ + suffix));
            }
        }
        printRow("Mixed (reuse)", reuse, false);
        printRow("Mixed (no-cache)", noCache, false);
        endBanner();
        results.add(new BenchmarkResult("Mixed-Reuse", JS_MIXED.length(), reuse));
        results.add(new BenchmarkResult("Mixed-NoCache", JS_MIXED.length(), noCache));
    }

    private static void runLargeScriptBenchmark() {
        banner("LARGE SCRIPT SCALING - Fresh context per eval");
        System.out.println("How performance scales with script size. Note that real Karate scripts");
        System.out.println("are far smaller than these - see the README.");
        System.out.println();
        printHeaderRow("Size");

        for (String sizeLabel : LargeScriptGenerator.getSizes()) {
            int targetBytes = LargeScriptGenerator.parseSize(sizeLabel);
            String script = LargeScriptGenerator.generate(targetBytes);
            int iterations = targetBytes > 20000 ? 20 : (targetBytes > 5000 ? 50 : 100);

            for (int i = 0; i < 10; i++) {
                for (Config c : CONFIGS) {
                    c.evalFresh(script);
                }
            }
            double[] values = new double[CONFIGS.size()];
            for (int i = 0; i < CONFIGS.size(); i++) {
                Config c = CONFIGS.get(i);
                values[i] = measure(iterations, () -> c.evalFresh(script));
            }
            printRow(sizeLabel, values, false);
            results.add(new BenchmarkResult("Large-" + sizeLabel, script.length(), values));
        }
        endBanner();
    }

    // ── console output ────────────────────────────────────────────────────────────────

    private static final String LINE = "═".repeat(104);

    private static void banner(String title) {
        System.out.println(LINE);
        System.out.println("  " + title);
        System.out.println(LINE);
    }

    private static void endBanner() {
        System.out.println(LINE);
        System.out.println();
    }

    private static void printHeaderRow(String first) {
        StringBuilder sb = new StringBuilder(String.format("%-16s", first));
        for (Config c : CONFIGS) {
            sb.append(String.format("%13s", c.label()));
        }
        sb.append("   ").append("Karate vs best");
        System.out.println(sb);
        System.out.println("─".repeat(104));
    }

    /** @param micros print in µs rather than ms (context creation is sub-microsecond for Karate) */
    private static void printRow(String name, double[] values, boolean micros) {
        StringBuilder sb = new StringBuilder(String.format("%-16s", name));
        for (double v : values) {
            sb.append(String.format(micros ? "%13.2f" : "%13.4f", micros ? v * 1000 : v));
        }
        sb.append("   ").append(vsBest(values));
        System.out.println(sb);
    }

    /** Karate measured against the fastest non-Karate configuration, named. */
    private static String vsBest(double[] values) {
        int best = 1;
        for (int i = 2; i < values.length; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        double r = values[0] / values[best];
        String label = CONFIGS.get(best).label();
        return (r < 1 ? String.format("%.1fx faster", 1 / r) : String.format("%.1fx slower", r))
                + " (" + label + ")";
    }

    // ── file output ───────────────────────────────────────────────────────────────────

    private static void writeCsv(String filename) {
        try {
            java.io.File file = new java.io.File(filename);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                StringBuilder header = new StringBuilder("timestamp,workload,chars");
                for (Config c : CONFIGS) {
                    header.append(',').append(c.label().toLowerCase().replace('-', '_')).append("_ms");
                }
                header.append(",best_other,karate_vs_best");
                pw.println(header);
                for (BenchmarkResult r : results) {
                    pw.println(r.toCsv());
                }
            }
            System.out.println("CSV written to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
        }
    }

    /**
     * Emits the machine-generated block that goes into the README between the
     * BENCHMARK:START / BENCHMARK:END markers. The README's prose is hand-written and
     * lives outside those markers - never edit the numbers by hand, re-run instead.
     */
    private static void writeMarkdown(String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Test Environment\n\n");
        sb.append("| | |\n|---|---|\n");
        sb.append("| Machine | ").append(env("bench.machine", System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + ", "
                + Runtime.getRuntime().availableProcessors() + " CPUs")).append(" |\n");
        sb.append("| Java | ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vm.name")).append(") |\n");
        String commit = env("bench.karate.commit", "");
        sb.append("| Karate JS | ").append(version("karate.version"))
                .append(commit.isEmpty() ? "" : ", built from source at `" + commit + "`").append(" |\n");
        sb.append("| Rhino | ").append(version("rhino.version")).append(" |\n");
        sb.append("| GraalJS | ").append(version("graaljs.version")).append(" (Community Edition) |\n");
        sb.append("| Run | ").append(env("bench.run", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " (local)")).append(" |\n\n");

        sb.append("Each competitor appears twice — in its default configuration, and tuned for this ")
                .append("workload. **Karate vs best** compares Karate against whichever non-Karate ")
                .append("configuration was fastest for that row, so Karate is never flattered by a ")
                .append("competitor's suboptimal default.\n\n");

        BenchmarkResult ctx = find("ContextCreate");
        if (ctx != null) {
            sb.append("### Context Creation Overhead\n\n");
            sb.append("Cost of a fresh set of globals, nothing evaluated. The engines defer different ")
                    .append("amounts of work, so read this as an architectural difference rather than a ")
                    .append("like-for-like measurement — see the Analysis section.\n\n");
            sb.append("| |");
            for (Config c : CONFIGS) {
                sb.append(' ').append(c.label()).append(" (µs) |");
            }
            sb.append(" Karate vs best |\n|").append("---|".repeat(CONFIGS.size() + 2)).append('\n');
            sb.append("| Context Create |");
            for (double v : ctx.values()) {
                sb.append(String.format(" %.2f |", v * 1000));
            }
            sb.append(' ').append(vsBest(ctx.values())).append(" |\n\n");
        }

        mdTable(sb, "Script Evaluation (Fresh Context)", "Workload",
                List.of("Arithmetic", "Strings", "Objects", "Functions", "Mixed"));
        mdTable(sb, "Context Reuse (Pure Execution Speed)", "Workload",
                List.of("Mixed-Reuse", "Mixed-NoCache"));
        mdTable(sb, "Large Script Scaling (Fresh Context per Eval)", "Target",
                List.of("Large-1KB", "Large-5KB", "Large-10KB", "Large-50KB", "Large-100KB"));

        try {
            java.io.File file = new java.io.File(filename);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.print(sb);
            }
            System.out.println("Markdown written to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write markdown: " + e.getMessage());
        }
    }

    private static void mdTable(StringBuilder sb, String title, String firstCol, List<String> names) {
        sb.append("### ").append(title).append("\n\n");
        sb.append("| ").append(firstCol).append(" | Bytes |");
        for (Config c : CONFIGS) {
            sb.append(' ').append(c.label()).append(" (ms) |");
        }
        sb.append(" Karate vs best |\n|").append("---|".repeat(CONFIGS.size() + 3)).append('\n');
        for (String name : names) {
            BenchmarkResult r = find(name);
            if (r == null) {
                // a renamed workload would silently vanish from the table - say so instead
                System.err.println("WARNING: no result for '" + name + "', omitted from " + title);
                continue;
            }
            sb.append("| ").append(name.replace("Large-", "")).append(" | ").append(r.chars()).append(" |");
            for (double v : r.values()) {
                sb.append(String.format(" %.4f |", v));
            }
            sb.append(' ').append(vsBest(r.values())).append(" |\n");
        }
        sb.append('\n');
    }

    private static BenchmarkResult find(String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    /** CI passes the runner facts in as -D system properties; locally they fall back to the JVM's own view. */
    private static String env(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record BenchmarkResult(String name, int chars, double[] values) {

        String toCsv() {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            StringBuilder sb = new StringBuilder(String.format("%s,%s,%d", ts, name, chars));
            for (double v : values) {
                // 6dp - context creation is ~0.00003 ms and rounds away to 0.0000 at 4dp
                sb.append(String.format(",%.6f", v));
            }
            int best = 1;
            for (int i = 2; i < values.length; i++) {
                if (values[i] < values[best]) {
                    best = i;
                }
            }
            sb.append(',').append(CONFIGS.get(best).label());
            sb.append(String.format(",%.5f", values[0] / values[best]));
            return sb.toString();
        }
    }

}
