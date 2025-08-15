/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for common DiskANN operations and helper functions.
 */
public class DiskANNUtils {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";
    private static final String DATA_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/data";

    /**
     * Execute a DiskANN command asynchronously
     */
    public static CompletableFuture<CommandResult> executeCommandAsync(String command) {
        return CompletableFuture.supplyAsync(() -> executeCommand(command));
    }

    /**
     * Execute a DiskANN command synchronously
     */
    public static CommandResult executeCommand(String command) {
        return executeCommand(command, 300); // 5 minute timeout
    }

    /**
     * Execute a DiskANN command with timeout
     */
    public static CommandResult executeCommand(String command, int timeoutSeconds) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            
            long startTime = System.currentTimeMillis();
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, -1, "Command timed out after " + timeoutSeconds + " seconds", 
                                       executionTime, command);
            }
            
            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, exitCode, output.toString(), executionTime, command);
            
        } catch (Exception e) {
            return new CommandResult(false, -1, "Error executing command: " + e.getMessage(), 0, command);
        }
    }

    /**
     * Create a temporary query file from vector data
     */
    public static Path createTempQueryFile(List<Float> queryVector) throws IOException {
        Path queryFile = Paths.get(System.getProperty("java.io.tmpdir"), 
                                  "diskann_query_" + System.currentTimeMillis() + ".fvecs");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(queryFile))) {
            writer.println(queryVector.size());
            for (int i = 0; i < queryVector.size(); i++) {
                if (i > 0) writer.print(" ");
                writer.print(queryVector.get(i));
            }
            writer.println();
        }
        
        return queryFile;
    }

    /**
     * Create a temporary batch query file from multiple vectors
     */
    public static Path createTempBatchQueryFile(List<List<Float>> queryVectors) throws IOException {
        if (queryVectors.isEmpty()) {
            throw new IllegalArgumentException("Query vectors list cannot be empty");
        }
        
        Path queryFile = Paths.get(System.getProperty("java.io.tmpdir"), 
                                  "diskann_batch_query_" + System.currentTimeMillis() + ".fvecs");
        
        int dimensions = queryVectors.get(0).size();
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(queryFile))) {
            for (List<Float> vector : queryVectors) {
                if (vector.size() != dimensions) {
                    throw new IllegalArgumentException("All vectors must have the same dimensions");
                }
                
                writer.println(dimensions);
                for (int i = 0; i < vector.size(); i++) {
                    if (i > 0) writer.print(" ");
                    writer.print(vector.get(i));
                }
                writer.println();
            }
        }
        
        return queryFile;
    }

    /**
     * Validate if DiskANN binaries are available
     */
    public static boolean validateBinaries() {
        return Files.exists(Paths.get(APPS_PATH + "/build_memory_index")) &&
               Files.exists(Paths.get(APPS_PATH + "/search_memory_index")) &&
               Files.exists(Paths.get(APPS_PATH + "/build_disk_index")) &&
               Files.exists(Paths.get(APPS_PATH + "/search_disk_index"));
    }

    /**
     * Get the absolute path to DiskANN apps directory
     */
    public static String getAppsPath() {
        return Paths.get(APPS_PATH).toAbsolutePath().toString();
    }

    /**
     * Get the absolute path to DiskANN data directory
     */
    public static String getDataPath() {
        return Paths.get(DATA_PATH).toAbsolutePath().toString();
    }

    /**
     * Check if a file exists and is readable
     */
    public static boolean isFileAccessible(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path) && Files.isReadable(path);
    }

    /**
     * Get file size in bytes
     */
    public static long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Clean up temporary files
     */
    public static void cleanupTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
        } catch (IOException e) {
            // Log but don't throw - cleanup is best effort
        }
    }

    /**
     * Estimate vector dimensions from file
     */
    public static int estimateVectorDimensions(String vectorFile) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(vectorFile))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                try {
                    return Integer.parseInt(firstLine.trim());
                } catch (NumberFormatException e) {
                    // If first line is not dimension count, count space-separated values
                    return firstLine.trim().split("\\s+").length;
                }
            }
        } catch (IOException e) {
            // Could not read file
        }
        return -1;
    }

    /**
     * Parse search results from DiskANN output
     */
    public static List<SearchResult> parseSearchResults(String output, int maxResults) {
        return java.util.Arrays.stream(output.split("\n"))
            .filter(line -> !line.trim().isEmpty() && !line.startsWith("#"))
            .limit(maxResults)
            .map(line -> {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        String id = parts[0];
                        double distance = Double.parseDouble(parts[1]);
                        double score = 1.0 / (1.0 + distance);
                        return new SearchResult(id, distance, score);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            })
            .filter(result -> result != null)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Result class for command execution
     */
    public static class CommandResult {
        private final boolean success;
        private final int exitCode;
        private final String output;
        private final long executionTimeMs;
        private final String command;

        public CommandResult(boolean success, int exitCode, String output, long executionTimeMs, String command) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.executionTimeMs = executionTimeMs;
            this.command = command;
        }

        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getCommand() { return command; }
    }

    /**
     * Search result class
     */
    public static class SearchResult {
        private final String id;
        private final double distance;
        private final double score;

        public SearchResult(String id, double distance, double score) {
            this.id = id;
            this.distance = distance;
            this.score = score;
        }

        public String getId() { return id; }
        public double getDistance() { return distance; }
        public double getScore() { return score; }
    }
}