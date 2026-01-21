/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.benchmark;

/**
 * Generates JavaScript scripts of various sizes for benchmarking.
 */
public class LargeScriptGenerator {

    /**
     * Generates a JavaScript script of approximately the target size in bytes.
     * The script contains realistic code patterns: functions, loops, objects, arrays.
     */
    public static String generate(int targetBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  var results = [];\n");

        int functionCount = 0;
        while (sb.length() < targetBytes - 100) {
            functionCount++;
            sb.append(generateFunction(functionCount));
        }

        // Call all functions and aggregate results
        sb.append("  var total = 0;\n");
        for (int i = 1; i <= functionCount; i++) {
            sb.append("  total += process").append(i).append("();\n");
        }
        sb.append("  return total;\n");
        sb.append("})();\n");

        return sb.toString();
    }

    private static String generateFunction(int id) {
        return """
          function process%d() {
            var data = [];
            for (var i = 0; i < 10; i++) {
              data.push({
                id: i + %d,
                name: 'item' + i,
                value: i * %d,
                active: i %% 2 === 0,
                tags: ['tag' + i, 'category%d'],
                nested: { a: i, b: i * 2, c: i * 3 }
              });
            }
            var filtered = data.filter(function(x) { return x.active; });
            var sum = 0;
            for (var j = 0; j < filtered.length; j++) {
              sum += filtered[j].value + filtered[j].nested.a;
            }
            return sum;
          }
        """.formatted(id, id * 100, id, id);
    }

    /**
     * Generates scripts at common size points for benchmarking.
     */
    public static String[] getSizes() {
        return new String[] { "1KB", "5KB", "10KB", "50KB", "100KB" };
    }

    public static int parseSize(String size) {
        return switch (size) {
            case "1KB" -> 1024;
            case "5KB" -> 5 * 1024;
            case "10KB" -> 10 * 1024;
            case "50KB" -> 50 * 1024;
            case "100KB" -> 100 * 1024;
            default -> 1024;
        };
    }

    public static void main(String[] args) {
        // Test the generator
        for (String size : getSizes()) {
            String script = generate(parseSize(size));
            System.out.printf("%s target -> %d bytes actual (%.2f KB)%n",
                size, script.length(), script.length() / 1024.0);
        }
    }
}
