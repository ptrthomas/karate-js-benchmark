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

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;
import io.karatelabs.parser.JsLexer;
import io.karatelabs.parser.Token;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive benchmark for Karate's JavaScript engine.
 * <p>
 * Includes:
 * - Lexer performance benchmarks
 * - Full eval benchmarks (lexer + parser + interpreter)
 * - Lexer vs full eval breakdown
 * - Comparison against Rhino and GraalJS
 * <p>
 * Run with: mvn compile exec:java
 */
public class JsEngineBenchmark {

    // ==================== LEXER TEST SAMPLES (for tokenization benchmarks) ====================

    private static final String JS_IDENTIFIERS_KEYWORDS = """
        function calculateTotal(items, taxRate, discount) {
            let total = 0;
            const multiplier = 1 + taxRate;
            for (let i = 0; i < items.length; i++) {
                const item = items[i];
                if (item.price > 0 && item.quantity > 0) {
                    total += item.price * item.quantity;
                } else if (item.isFree) {
                    continue;
                } else {
                    throw new Error('Invalid item');
                }
            }
            return total * multiplier - discount;
        }
        var result = calculateTotal(data, 0.08, 5.00);
        """;

    private static final String JS_STRINGS_AND_TEMPLATES = """
        const greeting = "Hello, World!";
        const name = 'John Doe';
        const message = `Welcome, ${name}! Your balance is $${balance.toFixed(2)}.`;
        const multiline = `
            This is a
            multiline template
            with ${nested} expressions
        `;
        const escaped = "She said \\"Hello\\" and he replied 'Hi'";
        const path = '/api/users/${userId}/profile';
        """;

    private static final String JS_NUMBERS_AND_OPERATORS = """
        let a = 123 + 456.789;
        let b = 0xFF + 0x1A2B;
        let c = 1e10 + 2.5e-3;
        let d = a * b / c % 100;
        let e = (a << 2) | (b >> 1) & 0xFF;
        let f = a === b ? c : d;
        let g = a !== b && c >= d || e <= f;
        let h = ++a + b-- * --c + d++;
        let i = a **= 2;
        let j = b ??= c || d;
        let k = obj?.prop?.nested ?? 'default';
        """;

    private static final String JS_OBJECTS_AND_ARRAYS = """
        const user = {
            id: 12345,
            name: "Alice",
            email: "alice@example.com",
            roles: ["admin", "user", "guest"],
            profile: {
                age: 30,
                city: "New York",
                settings: {
                    theme: "dark",
                    notifications: true,
                    preferences: {
                        language: "en",
                        timezone: "UTC"
                    }
                }
            },
            tags: [...existingTags, "new", "featured"],
            ...defaults
        };
        const [first, second, ...rest] = items;
        const { name: userName, profile: { age } } = user;
        """;

    private static final String JS_FUNCTIONS_AND_CLASSES = """
        const add = (a, b) => a + b;
        const multiply = (a, b) => {
            return a * b;
        };
        function processData(data, callback = () => {}) {
            try {
                const result = transform(data);
                callback(null, result);
            } catch (error) {
                callback(error, null);
            } finally {
                cleanup();
            }
        }
        const handler = async (event) => {
            const response = await fetch(url);
            return response.json();
        };
        """;

    private static final String JS_COMMENTS_AND_WHITESPACE = """
        // Single line comment
        /* Block comment */
        /**
         * Multi-line JSDoc comment
         * @param {string} name - The name
         * @returns {string} The greeting
         */
        function greet(name) {
            // Another comment
            return "Hello, " + name; /* inline */ // trailing
        }




        """;

    private static final String JS_REGEX = """
        const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$/;
        const urlPattern = /https?:\\/\\/[^\\s]+/gi;
        const datePattern = /\\d{4}-\\d{2}-\\d{2}/;
        if (/test/.test(str)) { console.log('match'); }
        const result = str.replace(/foo/g, 'bar');
        """;

    private static final String JS_EDGE_CASES = """
        // Unicode identifiers
        const café = 'coffee';
        const naïve = true;
        const _$valid123 = 1;

        // BigInt literals
        const bigInt1 = 123n;
        const bigInt2 = 0xFFn;
        const bigInt3 = 0o777n;
        const bigInt4 = 0b1010n;

        // Numeric separators
        const million = 1_000_000;
        const bytes = 0xFF_FF_FF_FF;
        const binary = 0b1010_0001_1000_0101;

        // Private class fields
        class Counter {
            #count = 0;
            #privateMethod() { return this.#count; }
            get count() { return this.#count; }
            increment() { this.#count++; }
        }

        // Regex vs division ambiguity
        const a = 10 / 2 / 5;
        const b = /regex/g;
        const c = (x) / 2;
        if (true) /regex/.test(s);

        // Tricky string escapes
        const esc1 = "line1\\nline2\\ttab";
        const esc2 = '\\x41\\x42\\x43';
        const esc3 = "\\u0048\\u0065\\u006C\\u006C\\u006F";

        // Nested template literals
        const nested = `outer ${`inner ${value}`} outer`;
        const deep = `a ${`b ${`c ${x}`}`}`;

        // Optional chaining combinations
        const oc1 = obj?.prop;
        const oc2 = obj?.[expr];
        const oc3 = func?.();
        const oc4 = obj?.method?.()?.prop?.[0];

        // Nullish coalescing
        let nc1 = a ?? b ?? c;
        const nc3 = obj.prop ?? obj.fallback ?? 'default';

        // Spread in various contexts
        const arr = [...a, ...b, ...c];
        const obj = { ...x, ...y, key: value };
        func(...args);

        // Destructuring edge cases
        const { a: { b: { c: deep } } } = obj;
        const [[[nested]]] = arr;

        // Arrow function edge cases
        const f1 = x => x;
        const f2 = (x) => x;
        const f3 = (x, y) => x + y;
        const f5 = ({ a, b }) => a + b;
        const f7 = async x => await x;
        """;

    private static final String JS_MIXED_REALISTIC = """
        function UserService(config) {
            const API_URL = config.apiUrl || 'https://api.example.com';
            const cache = new Map();

            this.getUser = async function(userId) {
                if (cache.has(userId)) {
                    return cache.get(userId);
                }

                const response = await fetch(`${API_URL}/users/${userId}`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${config.token}`
                    }
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

                const user = await response.json();
                cache.set(userId, user);
                return user;
            };

            this.updateUser = async function(userId, updates) {
                const user = await this.getUser(userId);
                const merged = { ...user, ...updates, updatedAt: Date.now() };

                const response = await fetch(`${API_URL}/users/${userId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${config.token}`
                    },
                    body: JSON.stringify(merged)
                });

                if (response.ok) {
                    cache.set(userId, merged);
                    return merged;
                }
                throw new Error('Update failed');
            };
        }

        const service = new UserService({ apiUrl: 'https://api.test.com', token: 'abc123' });
        const user = await service.getUser(42);
        console.log(`User: ${user.name}, Email: ${user.email}`);
        """;

    // ==================== EXECUTABLE SAMPLES (for eval benchmarks) ====================

    private static final String JS_EVAL_ARITHMETIC = """
        let result = 0;
        for (let i = 0; i < 100; i++) {
            result += i * 2 + i / 2 - i % 7;
            result = result * 1.01;
        }
        result;
        """;

    private static final String JS_EVAL_STRINGS = """
        let s = '';
        for (let i = 0; i < 50; i++) {
            s += 'item' + i + ',';
        }
        s.split(',').length;
        """;

    private static final String JS_EVAL_OBJECTS = """
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

    private static final String JS_EVAL_FUNCTIONS = """
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

    private static final String JS_EVAL_MIXED = """
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

    private static final String JS_EVAL_ALL = JS_EVAL_ARITHMETIC + JS_EVAL_STRINGS
            + JS_EVAL_OBJECTS + JS_EVAL_FUNCTIONS + JS_EVAL_MIXED;

    // Combined lexer test samples
    private static final String JS_ALL_COMBINED = JS_IDENTIFIERS_KEYWORDS
            + JS_STRINGS_AND_TEMPLATES
            + JS_NUMBERS_AND_OPERATORS
            + JS_OBJECTS_AND_ARRAYS
            + JS_FUNCTIONS_AND_CLASSES
            + JS_COMMENTS_AND_WHITESPACE
            + JS_REGEX
            + JS_EDGE_CASES
            + JS_MIXED_REALISTIC;

    // Large source for stress testing
    private static final String JS_LARGE;
    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append(JS_ALL_COMBINED).append("\n");
        }
        JS_LARGE = sb.toString();
    }

    private static final List<BenchmarkResult> results = new ArrayList<>();

    public static void main(String[] args) {
        String csvFile = args.length > 0 ? args[0] : null;

        printHeader();

        // Warmup
        System.out.println("Warming up JIT...");
        warmup();
        System.out.println("Warmup complete.\n");

        // 1. Lexer benchmarks
        runLexerBenchmarks();

        // 2. Full eval benchmarks
        runEvalBenchmarks();

        // 3. Lexer vs Full Eval comparison
        runLexerVsEvalComparison();

        // 4. Engine comparison (Karate vs Rhino vs GraalJS)
        runEngineComparison();

        // Write CSV
        String outputFile = csvFile != null ? csvFile : "target/benchmark.csv";
        writeCsv(outputFile);
    }

    private static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     Karate JS Engine - Comprehensive Benchmark                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Engines under test:");
        System.out.println("  - Karate JS: Custom lightweight JavaScript engine for Karate");
        System.out.println("  - Rhino:     Mozilla's JavaScript engine for Java (v1.9.0)");
        System.out.println("  - GraalJS:   Oracle's high-performance JS runtime (v24.1.1, interpreted mode)");
        System.out.println();
        System.out.println("Source sizes:");
        System.out.println("  - Combined lexer samples: " + JS_ALL_COMBINED.length() + " chars");
        System.out.println("  - Large (50x repeated):   " + JS_LARGE.length() + " chars");
        System.out.println();
    }

    private static void warmup() {
        for (int i = 0; i < 500; i++) {
            tokenize(JS_ALL_COMBINED);
            new Engine().eval(JS_EVAL_ARITHMETIC);
        }
        // Warmup Rhino and GraalJS
        for (int i = 0; i < 50; i++) {
            evalWithRhino(JS_EVAL_ARITHMETIC);
            try (org.graalvm.polyglot.Context ctx = org.graalvm.polyglot.Context.create("js")) {
                ctx.eval("js", JS_EVAL_ARITHMETIC);
            }
        }
    }

    // ==================== LEXER BENCHMARKS ====================

    private static void runLexerBenchmarks() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                              LEXER PERFORMANCE                                            ");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════\n");

        runLexerBenchmark("Small source (mixed)", JS_ALL_COMBINED, 10000);
        runLexerBenchmark("Large source (50x)", JS_LARGE, 500);

        System.out.println("\n--- By Token Category ---\n");
        runLexerBenchmark("Identifiers/Keywords", JS_IDENTIFIERS_KEYWORDS, 20000);
        runLexerBenchmark("Strings/Templates", JS_STRINGS_AND_TEMPLATES, 20000);
        runLexerBenchmark("Numbers/Operators", JS_NUMBERS_AND_OPERATORS, 20000);
        runLexerBenchmark("Objects/Arrays", JS_OBJECTS_AND_ARRAYS, 20000);
        runLexerBenchmark("Functions", JS_FUNCTIONS_AND_CLASSES, 20000);
        runLexerBenchmark("Comments/Whitespace", JS_COMMENTS_AND_WHITESPACE, 20000);
        runLexerBenchmark("Regex", JS_REGEX, 20000);
        runLexerBenchmark("Edge Cases", JS_EDGE_CASES, 20000);
        runLexerBenchmark("Realistic Mixed", JS_MIXED_REALISTIC, 10000);
        System.out.println();
    }

    private static void runLexerBenchmark(String name, String source, int iterations) {
        List<Token> tokens = tokenize(source);
        int tokenCount = tokens.size();

        long[] times = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                tokenize(source);
            }
            times[run] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(times);
        long medianNs = times[2];
        double avgUsPerIter = (medianNs / (double) iterations) / 1000.0;
        double msPerIter = avgUsPerIter / 1000.0;
        double charsPerUs = source.length() / avgUsPerIter;
        double tokensPerUs = tokenCount / avgUsPerIter;

        System.out.printf("%-24s %6d chars %4d tokens | %8.4f ms | %6.1f chars/µs | %5.2f tok/µs%n",
                name, source.length(), tokenCount, msPerIter, charsPerUs, tokensPerUs);

        results.add(new BenchmarkResult("Lexer: " + name, source.length(), tokenCount, iterations,
                msPerIter, charsPerUs, tokensPerUs, medianNs / 1_000_000.0));
    }

    // ==================== EVAL BENCHMARKS ====================

    private static void runEvalBenchmarks() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                         FULL EVAL (Lexer + Parser + Interpreter)                          ");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════\n");

        runEvalBenchmark("Arithmetic", JS_EVAL_ARITHMETIC, 5000);
        runEvalBenchmark("Strings", JS_EVAL_STRINGS, 2000);
        runEvalBenchmark("Objects", JS_EVAL_OBJECTS, 1000);
        runEvalBenchmark("Functions", JS_EVAL_FUNCTIONS, 500);
        runEvalBenchmark("Mixed", JS_EVAL_MIXED, 500);
        runEvalBenchmark("All Combined", JS_EVAL_ALL, 200);
        System.out.println();
    }

    private static void runEvalBenchmark(String name, String source, int iterations) {
        // Warmup
        for (int i = 0; i < 100; i++) {
            new Engine().eval(source);
        }

        List<Token> tokens = tokenize(source);
        int tokenCount = tokens.size();

        long[] times = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine().eval(source);
            }
            times[run] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(times);
        long medianNs = times[2];
        double avgUsPerIter = (medianNs / (double) iterations) / 1000.0;
        double msPerIter = avgUsPerIter / 1000.0;
        double charsPerUs = source.length() / avgUsPerIter;
        double tokensPerUs = tokenCount / avgUsPerIter;

        System.out.printf("%-24s %6d chars %4d tokens | %8.4f ms | %6.1f chars/µs | %5.2f tok/µs%n",
                name, source.length(), tokenCount, msPerIter, charsPerUs, tokensPerUs);

        results.add(new BenchmarkResult("Eval: " + name, source.length(), tokenCount, iterations,
                msPerIter, charsPerUs, tokensPerUs, medianNs / 1_000_000.0));
    }

    // ==================== LEXER vs EVAL COMPARISON ====================

    private static void runLexerVsEvalComparison() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                            LEXER vs FULL EVAL BREAKDOWN                                   ");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════\n");

        System.out.printf("%-20s %10s %10s %10s %10s%n", "Workload", "Lex (ms)", "Eval (ms)", "Lex %", "Other %");
        System.out.println("-".repeat(70));

        runLexVsEval("Arithmetic", JS_EVAL_ARITHMETIC, 5000);
        runLexVsEval("Strings", JS_EVAL_STRINGS, 5000);
        runLexVsEval("Objects", JS_EVAL_OBJECTS, 2000);
        runLexVsEval("Functions", JS_EVAL_FUNCTIONS, 1000);
        runLexVsEval("Mixed", JS_EVAL_MIXED, 1000);
        runLexVsEval("All Combined", JS_EVAL_ALL, 500);
        System.out.println();
    }

    private static void runLexVsEval(String name, String source, int iterations) {
        // Warmup
        for (int i = 0; i < 100; i++) {
            tokenize(source);
            new Engine().eval(source);
        }

        // Measure lexer
        long[] lexTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                tokenize(source);
            }
            lexTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(lexTimes);
        double lexMs = (lexTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure eval
        long[] evalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine().eval(source);
            }
            evalTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(evalTimes);
        double evalMs = (evalTimes[2] / (double) iterations) / 1_000_000.0;

        double lexPercent = (lexMs / evalMs) * 100;
        double otherPercent = 100 - lexPercent;

        System.out.printf("%-20s %10.4f %10.4f %9.1f%% %9.1f%%%n",
                name, lexMs, evalMs, lexPercent, otherPercent);
    }

    // ==================== ENGINE COMPARISON ====================

    private static void runEngineComparison() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                       KARATE vs RHINO vs GRAALJS COMPARISON                               ");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════════\n");

        System.out.printf("%-16s %11s %11s %11s %14s %14s%n",
                "Workload", "Karate (ms)", "Rhino (ms)", "Graal (ms)", "vs Rhino", "vs Graal");
        System.out.println("-".repeat(90));

        runEngineCompare("Arithmetic", JS_EVAL_ARITHMETIC, 2000);
        runEngineCompare("Strings", JS_EVAL_STRINGS, 2000);
        runEngineCompare("Objects", JS_EVAL_OBJECTS, 1000);
        runEngineCompare("Functions", JS_EVAL_FUNCTIONS, 500);
        runEngineCompare("Mixed", JS_EVAL_MIXED, 500);
        System.out.println("-".repeat(90));
        runEngineCompare("Combined", JS_EVAL_ALL, 200);
        System.out.println("═".repeat(90));
        System.out.println();
        System.out.println("Legend: 'X.Xx faster' = Karate is X.X times faster than the compared engine");
        System.out.println();
    }

    private static void runEngineCompare(String name, String source, int iterations) {
        // Warmup
        for (int i = 0; i < 50; i++) {
            new Engine().eval(source);
            evalWithRhino(source);
            try (org.graalvm.polyglot.Context ctx = org.graalvm.polyglot.Context.create("js")) {
                ctx.eval("js", source);
            }
        }

        // Karate
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

        // Rhino
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

        // GraalJS
        long[] graalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                try (org.graalvm.polyglot.Context ctx = org.graalvm.polyglot.Context.create("js")) {
                    ctx.eval("js", source);
                }
            }
            graalTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(graalTimes);
        double graalMs = (graalTimes[2] / (double) iterations) / 1_000_000.0;

        // Ratios
        double rhinoRatio = karateMs / rhinoMs;
        String rhinoStr = rhinoRatio < 1 ? String.format("%.1fx faster", 1 / rhinoRatio) : String.format("%.1fx slower", rhinoRatio);
        double graalRatio = karateMs / graalMs;
        String graalStr = graalRatio < 1 ? String.format("%.1fx faster", 1 / graalRatio) : String.format("%.1fx slower", graalRatio);

        System.out.printf("%-16s %11.4f %11.4f %11.4f %14s %14s%n",
                name, karateMs, rhinoMs, graalMs, rhinoStr, graalStr);
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

    private static List<Token> tokenize(String source) {
        return JsLexer.getTokens(Resource.text(source));
    }

    // ==================== CSV OUTPUT ====================

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

    private record BenchmarkResult(
            String name,
            int chars,
            int tokens,
            int iterations,
            double msPerIter,
            double charsPerUs,
            double tokensPerUs,
            double medianTimeMs
    ) {
        static String csvHeader() {
            return "timestamp,name,chars,tokens,iterations,ms_per_iter,chars_per_us,tokens_per_us,median_time_ms";
        }

        String toCsv() {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return String.format("%s,%s,%d,%d,%d,%.4f,%.1f,%.2f,%.2f",
                    ts, name, chars, tokens, iterations, msPerIter, charsPerUs, tokensPerUs, medianTimeMs);
        }
    }

}
