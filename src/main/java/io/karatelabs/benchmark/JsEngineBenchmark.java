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
import java.util.List;
import java.util.Properties;

/**
 * Performance benchmark comparing Karate's JavaScript engine against Rhino and GraalJS.
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

        // Warmup all engines equally
        System.out.println("Warming up all engines...");
        warmup();
        System.out.println("Warmup complete.\n");

        // Context creation overhead
        runContextCreationBenchmark();

        // Engine comparison
        runEngineComparison();

        // Write CSV
        String outputFile = csvFile != null ? csvFile : "target/benchmark.csv";
        writeCsv(outputFile);

        // Write the markdown block CI splices into the README, alongside the CSV
        writeMarkdown(outputFile.replaceFirst("\\.csv$", "") + ".md");
    }

    private static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              Karate JS Engine vs Rhino vs GraalJS Benchmark                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Engines under test:");
        System.out.println("  - Karate JS: Custom lightweight JavaScript engine (v" + version("karate.version") + ")");
        System.out.println("  - Rhino:     Mozilla's JavaScript engine for Java (v" + version("rhino.version")
                + ", ES6, compiled mode - Rhino's default)");
        System.out.println("  - GraalJS:   Oracle's high-performance JS runtime (v" + version("graaljs.version") + ", interpreted mode)");
        System.out.println();
        System.out.println("Java: " + System.getProperty("java.version") + " | OS: " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + " | CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println();
        System.out.println("All engines create a fresh context for each eval(), each in its DEFAULT");
        System.out.println("configuration - which for Rhino and GraalJS is not necessarily their fastest.");
        System.out.println("See the README 'Engine configuration' notes before quoting these numbers.");
        System.out.println();
    }

    private static void warmup() {
        // Equal warmup for all engines
        for (int i = 0; i < 100; i++) {
            new Engine().eval(JS_ARITHMETIC);
            evalWithRhino(JS_ARITHMETIC);
            try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                ctx.eval("js", JS_ARITHMETIC);
            }
        }
    }

    private static org.graalvm.polyglot.Context createGraalContext() {
        return org.graalvm.polyglot.Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    private static void runContextCreationBenchmark() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                              CONTEXT CREATION OVERHEAD");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");

        int iterations = 5000;

        // Warmup context creation
        for (int i = 0; i < 500; i++) {
            new Engine();
            org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
            cx.initStandardObjects();
            org.mozilla.javascript.Context.exit();
            try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                // just create and close
            }
        }

        // Measure Karate context creation
        long[] karateTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine();
            }
            karateTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(karateTimes);
        double karateUs = (karateTimes[2] / (double) iterations) / 1_000.0;

        // Measure Rhino context creation
        long[] rhinoTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
                cx.initStandardObjects();
                org.mozilla.javascript.Context.exit();
            }
            rhinoTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(rhinoTimes);
        double rhinoUs = (rhinoTimes[2] / (double) iterations) / 1_000.0;

        // Measure GraalJS context creation
        long[] graalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                    // just create and close
                }
            }
            graalTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(graalTimes);
        double graalUs = (graalTimes[2] / (double) iterations) / 1_000.0;

        // Calculate ratios
        double rhinoRatio = karateUs / rhinoUs;
        String rhinoStr = rhinoRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRatio) : String.format("%.1fx slower", rhinoRatio);
        double graalRatio = karateUs / graalUs;
        String graalStr = graalRatio < 1 ? String.format("%.1fx faster", 1 / graalRatio) : String.format("%.1fx slower", graalRatio);

        System.out.printf("%-16s %11s %11s %11s %14s %14s%n",
                "", "Karate (µs)", "Rhino (µs)", "Graal (µs)", "vs Rhino", "vs Graal");
        System.out.println("───────────────────────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-16s %11.2f %11.2f %11.2f %14s %14s%n",
                "Context Create", karateUs, rhinoUs, graalUs, rhinoStr, graalStr);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Store for CSV
        results.add(new BenchmarkResult("ContextCreate", 0, karateUs / 1000.0, rhinoUs / 1000.0, graalUs / 1000.0));
    }

    private static void runEngineComparison() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                                 SCRIPT EVALUATION");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.printf("%-16s %11s %11s %11s %14s %14s%n",
                "Workload", "Karate (ms)", "Rhino (ms)", "Graal (ms)", "vs Rhino", "vs Graal");
        System.out.println("───────────────────────────────────────────────────────────────────────────────────────────");

        runComparison("Arithmetic", JS_ARITHMETIC, 2000);
        runComparison("Strings", JS_STRINGS, 2000);
        runComparison("Objects", JS_OBJECTS, 1000);
        runComparison("Functions", JS_FUNCTIONS, 500);
        runComparison("Mixed", JS_MIXED, 500);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Context reuse comparison
        runContextReuseBenchmark();

        // Large script scaling
        runLargeScriptBenchmark();

        System.out.println("Legend: 'X.Xx faster' = Karate is X.X times faster than the compared engine");
        System.out.println();
    }

    private static void runContextReuseBenchmark() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                    CONTEXT REUSE - Same context, multiple evals");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("Shows pure execution speed when context creation overhead is removed.");
        System.out.printf("Script size: JS_MIXED = %d bytes (%.2f KB)%n",
                JS_MIXED.length(), JS_MIXED.length() / 1024.0);
        System.out.println();

        // Wrap in IIFE to avoid 'let' redeclaration errors when reusing context
        String wrappedMixed = "(function() {\n" + JS_MIXED + "\n})();";
        String wrappedArithmetic = "(function() {\n" + JS_ARITHMETIC + "\n})();";

        int iterations = 1000;

        // Warmup with context reuse
        Engine karateEngine = new Engine();
        for (int i = 0; i < 100; i++) {
            karateEngine.eval(wrappedArithmetic);
        }
        org.mozilla.javascript.Context rhinoCx = org.mozilla.javascript.Context.enter();
        rhinoCx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        org.mozilla.javascript.Scriptable rhinoScope = rhinoCx.initStandardObjects();
        for (int i = 0; i < 100; i++) {
            rhinoCx.evaluateString(rhinoScope, wrappedArithmetic, "bench", 1, null);
        }
        org.mozilla.javascript.Context.exit();
        org.graalvm.polyglot.Context graalCtx = createGraalContext();
        for (int i = 0; i < 100; i++) {
            graalCtx.eval("js", wrappedArithmetic);
        }
        graalCtx.close();

        // Measure Karate with context reuse
        karateEngine = new Engine();
        long[] karateTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                karateEngine.eval(wrappedMixed);
            }
            karateTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(karateTimes);
        double karateMs = (karateTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure Rhino with context reuse
        rhinoCx = org.mozilla.javascript.Context.enter();
        rhinoCx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        rhinoScope = rhinoCx.initStandardObjects();
        long[] rhinoTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                rhinoCx.evaluateString(rhinoScope, wrappedMixed, "bench", 1, null);
            }
            rhinoTimes[run] = System.nanoTime() - start;
        }
        org.mozilla.javascript.Context.exit();
        java.util.Arrays.sort(rhinoTimes);
        double rhinoMs = (rhinoTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure GraalJS with context reuse
        graalCtx = createGraalContext();
        long[] graalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                graalCtx.eval("js", wrappedMixed);
            }
            graalTimes[run] = System.nanoTime() - start;
        }
        graalCtx.close();
        java.util.Arrays.sort(graalTimes);
        double graalMs = (graalTimes[2] / (double) iterations) / 1_000_000.0;

        double rhinoRatio = karateMs / rhinoMs;
        String rhinoStr = rhinoRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRatio) : String.format("%.1fx slower", rhinoRatio);
        double graalRatio = karateMs / graalMs;
        String graalStr = graalRatio < 1 ? String.format("%.1fx faster", 1 / graalRatio) : String.format("%.1fx slower", graalRatio);

        System.out.printf("%-16s %11s %11s %11s %14s %14s%n",
                "Workload", "Karate (ms)", "Rhino (ms)", "Graal (ms)", "vs Rhino", "vs Graal");
        System.out.println("───────────────────────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-16s %11.4f %11.4f %11.4f %14s %14s%n",
                "Mixed (reuse)", karateMs, rhinoMs, graalMs, rhinoStr, graalStr);

        // Now test with randomized scripts to defeat source caching
        String scriptPrefix = "(function() { var _seed = ";
        String scriptSuffix = ";\n" + JS_MIXED + "\n})();";

        // Measure Karate with randomized scripts
        karateEngine = new Engine();
        karateTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                karateEngine.eval(scriptPrefix + i + scriptSuffix);
            }
            karateTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(karateTimes);
        double karateRandMs = (karateTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure Rhino with randomized scripts
        rhinoCx = org.mozilla.javascript.Context.enter();
        rhinoCx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        rhinoScope = rhinoCx.initStandardObjects();
        rhinoTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                rhinoCx.evaluateString(rhinoScope, scriptPrefix + i + scriptSuffix, "bench", 1, null);
            }
            rhinoTimes[run] = System.nanoTime() - start;
        }
        org.mozilla.javascript.Context.exit();
        java.util.Arrays.sort(rhinoTimes);
        double rhinoRandMs = (rhinoTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure GraalJS with randomized scripts
        graalCtx = createGraalContext();
        graalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                graalCtx.eval("js", scriptPrefix + i + scriptSuffix);
            }
            graalTimes[run] = System.nanoTime() - start;
        }
        graalCtx.close();
        java.util.Arrays.sort(graalTimes);
        double graalRandMs = (graalTimes[2] / (double) iterations) / 1_000_000.0;

        double rhinoRandRatio = karateRandMs / rhinoRandMs;
        String rhinoRandStr = rhinoRandRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRandRatio) : String.format("%.1fx slower", rhinoRandRatio);
        double graalRandRatio = karateRandMs / graalRandMs;
        String graalRandStr = graalRandRatio < 1 ? String.format("%.1fx faster", 1 / graalRandRatio) : String.format("%.1fx slower", graalRandRatio);

        System.out.printf("%-16s %11.4f %11.4f %11.4f %14s %14s%n",
                "Mixed (no-cache)", karateRandMs, rhinoRandMs, graalRandMs, rhinoRandStr, graalRandStr);

        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        results.add(new BenchmarkResult("Mixed-Reuse", JS_MIXED.length(), karateMs, rhinoMs, graalMs));
        results.add(new BenchmarkResult("Mixed-NoCache", JS_MIXED.length(), karateRandMs, rhinoRandMs, graalRandMs));
    }

    private static void runLargeScriptBenchmark() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                    LARGE SCRIPT SCALING - Fresh context per eval");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("Tests how performance scales with script size (realistic JS with functions, loops, objects).");
        System.out.println();

        System.out.printf("%-16s %11s %11s %11s %14s %14s%n",
                "Size", "Karate (ms)", "Rhino (ms)", "Graal (ms)", "vs Rhino", "vs Graal");
        System.out.println("───────────────────────────────────────────────────────────────────────────────────────────");

        for (String sizeLabel : LargeScriptGenerator.getSizes()) {
            int targetBytes = LargeScriptGenerator.parseSize(sizeLabel);
            String script = LargeScriptGenerator.generate(targetBytes);
            int iterations = targetBytes > 20000 ? 20 : (targetBytes > 5000 ? 50 : 100);

            // Warmup
            for (int i = 0; i < 10; i++) {
                new Engine().eval(script);
                evalWithRhino(script);
                try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                    ctx.eval("js", script);
                }
            }

            // Measure Karate
            long[] karateTimes = new long[5];
            for (int run = 0; run < 5; run++) {
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    new Engine().eval(script);
                }
                karateTimes[run] = System.nanoTime() - start;
            }
            java.util.Arrays.sort(karateTimes);
            double karateMs = (karateTimes[2] / (double) iterations) / 1_000_000.0;

            // Measure Rhino
            long[] rhinoTimes = new long[5];
            for (int run = 0; run < 5; run++) {
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    evalWithRhino(script);
                }
                rhinoTimes[run] = System.nanoTime() - start;
            }
            java.util.Arrays.sort(rhinoTimes);
            double rhinoMs = (rhinoTimes[2] / (double) iterations) / 1_000_000.0;

            // Measure GraalJS
            long[] graalTimes = new long[5];
            for (int run = 0; run < 5; run++) {
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                        ctx.eval("js", script);
                    }
                }
                graalTimes[run] = System.nanoTime() - start;
            }
            java.util.Arrays.sort(graalTimes);
            double graalMs = (graalTimes[2] / (double) iterations) / 1_000_000.0;

            double rhinoRatio = karateMs / rhinoMs;
            String rhinoStr = rhinoRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRatio) : String.format("%.1fx slower", rhinoRatio);
            double graalRatio = karateMs / graalMs;
            String graalStr = graalRatio < 1 ? String.format("%.1fx faster", 1 / graalRatio) : String.format("%.1fx slower", graalRatio);

            String actualSize = String.format("%s (%dB)", sizeLabel, script.length());
            System.out.printf("%-16s %11.2f %11.2f %11.2f %14s %14s%n",
                    actualSize, karateMs, rhinoMs, graalMs, rhinoStr, graalStr);

            results.add(new BenchmarkResult("Large-" + sizeLabel, script.length(), karateMs, rhinoMs, graalMs));
        }

        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private static void runComparison(String name, String source, int iterations) {
        // Per-test warmup for all engines equally
        for (int i = 0; i < 50; i++) {
            new Engine().eval(source);
            evalWithRhino(source);
            try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                ctx.eval("js", source);
            }
        }

        // Measure Karate
        long[] karateTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine().eval(source);
            }
            karateTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(karateTimes);
        double karateMs = (karateTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure Rhino
        long[] rhinoTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                evalWithRhino(source);
            }
            rhinoTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(rhinoTimes);
        double rhinoMs = (rhinoTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure GraalJS
        long[] graalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                try (org.graalvm.polyglot.Context ctx = createGraalContext()) {
                    ctx.eval("js", source);
                }
            }
            graalTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(graalTimes);
        double graalMs = (graalTimes[2] / (double) iterations) / 1_000_000.0;

        // Calculate ratios
        double rhinoRatio = karateMs / rhinoMs;
        String rhinoStr = rhinoRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRatio) : String.format("%.1fx slower", rhinoRatio);
        double graalRatio = karateMs / graalMs;
        String graalStr = graalRatio < 1 ? String.format("%.1fx faster", 1 / graalRatio) : String.format("%.1fx slower", graalRatio);

        System.out.printf("%-16s %11.4f %11.4f %11.4f %14s %14s%n",
                name, karateMs, rhinoMs, graalMs, rhinoStr, graalStr);

        // Store for CSV
        results.add(new BenchmarkResult(name, source.length(), karateMs, rhinoMs, graalMs));
    }

    private static Object evalWithRhino(String source) {
        org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
        try {
            cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, source, "benchmark", 1, null);
        } finally {
            org.mozilla.javascript.Context.exit();
        }
    }

    private static void writeCsv(String filename) {
        try {
            java.io.File file = new java.io.File(filename);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println(BenchmarkResult.csvHeader());
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
     * Emits the machine-generated block that CI splices into the README between the
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
        sb.append("| Karate JS | ").append(version("karate.version"))
                .append(env("bench.karate.commit", "").isEmpty() ? ""
                        : " (built from source, commit `" + env("bench.karate.commit", "") + "`)").append(" |\n");
        sb.append("| Rhino | ").append(version("rhino.version"))
                .append(" (ES6, compiled mode — Rhino's default; see Notes) |\n");
        sb.append("| GraalJS | ").append(version("graaljs.version"))
                .append(" (Community Edition, interpreted mode) |\n");
        sb.append("| Run | ").append(env("bench.run", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " (local)")).append(" |\n\n");

        BenchmarkResult ctx = find("ContextCreate");
        if (ctx != null) {
            sb.append("### Context Creation Overhead\n\n");
            sb.append("| | Karate (µs) | Rhino (µs) | Graal (µs) | vs Rhino | vs Graal |\n");
            sb.append("|---|---|---|---|---|---|\n");
            sb.append(String.format("| Context Create | %.2f | %.2f | %.2f | %s | %s |%n",
                    ctx.karateMs() * 1000, ctx.rhinoMs() * 1000, ctx.graalMs() * 1000,
                    ratio(ctx.karateMs(), ctx.rhinoMs()), ratio(ctx.karateMs(), ctx.graalMs())));
            sb.append('\n');
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
        sb.append("| ").append(firstCol).append(" | Bytes | Karate (ms) | Rhino (ms) | Graal (ms) | vs Rhino | vs Graal |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (String name : names) {
            BenchmarkResult r = find(name);
            if (r == null) {
                continue;
            }
            sb.append(String.format("| %s | %d B | %.4f | %.4f | %.4f | %s | %s |%n",
                    name.replace("Large-", ""), r.chars(), r.karateMs(), r.rhinoMs(), r.graalMs(),
                    ratio(r.karateMs(), r.rhinoMs()), ratio(r.karateMs(), r.graalMs())));
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

    /** Wins and losses get identical emphasis - bolding only the wins reads as cherry-picking. */
    private static String ratio(double karate, double other) {
        double r = karate / other;
        return r < 1 ? String.format("%.1fx faster", 1 / r) : String.format("%.1fx slower", r);
    }

    private record BenchmarkResult(
            String name,
            int chars,
            double karateMs,
            double rhinoMs,
            double graalMs
    ) {
        static String csvHeader() {
            return "timestamp,workload,chars,karate_ms,rhino_ms,graal_ms,vs_rhino,vs_graal";
        }

        String toCsv() {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            double vsRhino = karateMs / rhinoMs;
            double vsGraal = karateMs / graalMs;
            // 6dp - ContextCreate is ~0.00003 ms and rounded away to 0.0000 at 4dp, which
            // silently destroyed the headline number in the archived CSV
            return String.format("%s,%s,%d,%.6f,%.6f,%.6f,%.5f,%.5f",
                    ts, name, chars, karateMs, rhinoMs, graalMs, vsRhino, vsGraal);
        }
    }

}
