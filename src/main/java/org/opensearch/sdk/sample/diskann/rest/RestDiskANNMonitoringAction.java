/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.rest;

import org.opensearch.OpenSearchParseException;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.extensions.rest.ExtensionRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.sdk.rest.BaseExtensionRestHandler;
import org.opensearch.sdk.rest.ExtensionRestHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for DiskANN performance monitoring and statistics.
 * Tracks search performance, index statistics, and system metrics.
 */
public class RestDiskANNMonitoringAction extends BaseExtensionRestHandler {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";
    
    // Performance tracking
    private final Map<String, AtomicLong> searchCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> searchLatencies = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> indexBuildTimes = new ConcurrentHashMap<>();

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(GET, "/diskann/stats", handleGetStats),
            new RouteHandler(GET, "/diskann/stats/{indexName}", handleGetIndexStats),
            new RouteHandler(POST, "/diskann/benchmark", handleBenchmark),
            new RouteHandler(POST, "/diskann/stats/reset", handleResetStats),
            new RouteHandler(GET, "/diskann/health", handleHealthCheck),
            new RouteHandler(POST, "/diskann/profile", handleProfileIndex)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleGetStats = (request) -> {
        try {
            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("timestamp", System.currentTimeMillis())
                .startObject("search_stats")
                .field("total_searches", searchCounts.values().stream().mapToLong(AtomicLong::get).sum())
                .field("average_latency_ms", calculateAverageLatency())
                .field("searches_by_index", searchCounts.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, 
                        e -> e.getValue().get())))
                .endObject()
                .startObject("index_stats")
                .field("total_indices_built", indexBuildTimes.size())
                .field("build_times_by_index", indexBuildTimes.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, 
                        e -> e.getValue().get())))
                .endObject()
                .startObject("system_stats")
                .field("available_memory_mb", getAvailableMemory())
                .field("disk_space_gb", getAvailableDiskSpace())
                .field("cpu_cores", Runtime.getRuntime().availableProcessors())
                .endObject()
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to get stats: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleGetIndexStats = (request) -> {
        String indexName = request.param("indexName");
        
        try {
            String indexPath = "indices/" + indexName;
            Map<String, Object> indexStats = getDetailedIndexStats(indexPath);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_name", indexName)
                .field("index_path", indexPath)
                .field("search_count", searchCounts.getOrDefault(indexName, new AtomicLong(0)).get())
                .field("average_latency_ms", searchLatencies.getOrDefault(indexName, new AtomicLong(0)).get())
                .field("build_time_ms", indexBuildTimes.getOrDefault(indexName, new AtomicLong(0)).get())
                .field("index_stats", indexStats)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to get index stats: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBenchmark = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for benchmark");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            String queryFile = (String) params.get("query_file");
            String groundtruthFile = (String) params.get("groundtruth_file");
            Integer numQueries = (Integer) params.getOrDefault("num_queries", 100);
            Integer k = (Integer) params.getOrDefault("k", 10);
            List<Integer> beamWidths = (List<Integer>) params.getOrDefault("beam_widths", List.of(64, 128, 256));
            
            if (indexPath == null || queryFile == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, query_file");
            }

            List<Map<String, Object>> benchmarkResults = runBenchmark(
                indexPath, queryFile, groundtruthFile, numQueries, k, beamWidths);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_file", queryFile)
                .field("num_queries", numQueries)
                .field("k", k)
                .field("benchmark_results", benchmarkResults)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to run benchmark: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleResetStats = (request) -> {
        searchCounts.clear();
        searchLatencies.clear();
        indexBuildTimes.clear();

        try {
            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("message", "Statistics reset successfully")
                .field("timestamp", System.currentTimeMillis())
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to reset stats: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleHealthCheck = (request) -> {
        try {
            boolean binariesExist = checkBinariesExist();
            boolean dataExists = checkDataExists();
            long memoryAvailable = getAvailableMemory();
            long diskAvailable = getAvailableDiskSpace();

            String status = (binariesExist && dataExists && memoryAvailable > 1000 && diskAvailable > 1) 
                ? "healthy" : "unhealthy";

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", status)
                .field("timestamp", System.currentTimeMillis())
                .startObject("checks")
                .field("binaries_exist", binariesExist)
                .field("sample_data_exists", dataExists)
                .field("sufficient_memory", memoryAvailable > 1000)
                .field("sufficient_disk", diskAvailable > 1)
                .endObject()
                .startObject("resources")
                .field("available_memory_mb", memoryAvailable)
                .field("available_disk_gb", diskAvailable)
                .field("cpu_cores", Runtime.getRuntime().availableProcessors())
                .endObject()
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to check health: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleProfileIndex = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for profiling");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            
            if (indexPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, "Required: index_path");
            }

            Map<String, Object> profileData = profileIndex(indexPath);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("profile_data", profileData)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to profile index: " + e.getMessage());
        }
    };

    private double calculateAverageLatency() {
        if (searchLatencies.isEmpty()) return 0.0;
        
        long totalLatency = searchLatencies.values().stream().mapToLong(AtomicLong::get).sum();
        long totalSearches = searchCounts.values().stream().mapToLong(AtomicLong::get).sum();
        
        return totalSearches > 0 ? (double) totalLatency / totalSearches : 0.0;
    }

    private Map<String, Object> getDetailedIndexStats(String indexPath) {
        try {
            if (!Files.exists(Paths.get(indexPath))) {
                return Map.of("error", "Index not found");
            }

            long totalSize = Files.walk(Paths.get(indexPath))
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); } catch (Exception e) { return 0; }
                })
                .sum();

            long fileCount = Files.walk(Paths.get(indexPath))
                .filter(Files::isRegularFile)
                .count();

            return Map.of(
                "total_size_bytes", totalSize,
                "total_size_mb", totalSize / (1024 * 1024),
                "file_count", fileCount,
                "last_accessed", System.currentTimeMillis(),
                "exists", true
            );

        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private List<Map<String, Object>> runBenchmark(String indexPath, String queryFile, String groundtruthFile,
                                                   int numQueries, int k, List<Integer> beamWidths) {
        return beamWidths.stream().map(beamWidth -> {
            long startTime = System.currentTimeMillis();
            
            String command = APPS_PATH + "/search_memory_index " +
                           "--index_path " + indexPath + " " +
                           "--query_file " + queryFile + " " +
                           (groundtruthFile != null ? "--gt_file " + groundtruthFile + " " : "") +
                           "-K " + k + " " +
                           "-W " + beamWidth;
            
            String result = executeCommand(command);
            long endTime = System.currentTimeMillis();
            
            return Map.<String, Object>of(
                "beam_width", beamWidth,
                "execution_time_ms", endTime - startTime,
                "queries_per_second", numQueries * 1000.0 / (endTime - startTime),
                "command_output", result
            );
        }).collect(java.util.stream.Collectors.toList());
    }

    private boolean checkBinariesExist() {
        return Files.exists(Paths.get(APPS_PATH + "/build_memory_index")) &&
               Files.exists(Paths.get(APPS_PATH + "/search_memory_index"));
    }

    private boolean checkDataExists() {
        return Files.exists(Paths.get("src/main/java/org/opensearch/sdk/sample/diskann/data/sift"));
    }

    private long getAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024);
    }

    private long getAvailableDiskSpace() {
        try {
            return Files.getFileStore(Paths.get(".")).getUsableSpace() / (1024 * 1024 * 1024);
        } catch (IOException e) {
            return -1;
        }
    }

    private Map<String, Object> profileIndex(String indexPath) {
        // Analyze index structure and performance characteristics
        try {
            if (!Files.exists(Paths.get(indexPath))) {
                return Map.of("error", "Index not found");
            }

            // Get basic file statistics
            long totalSize = Files.walk(Paths.get(indexPath))
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); } catch (Exception e) { return 0; }
                })
                .sum();

            // Count different file types
            Map<String, Long> fileTypes = Files.walk(Paths.get(indexPath))
                .filter(Files::isRegularFile)
                .collect(java.util.stream.Collectors.groupingBy(
                    p -> getFileExtension(p.getFileName().toString()),
                    java.util.stream.Collectors.counting()
                ));

            return Map.of(
                "total_size_bytes", totalSize,
                "file_types", fileTypes,
                "estimated_load_time_ms", estimateLoadTime(totalSize),
                "memory_requirement_mb", estimateMemoryRequirement(totalSize),
                "index_type", detectIndexType(indexPath)
            );

        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "no_extension";
    }

    private long estimateLoadTime(long sizeBytes) {
        // Rough estimate: 100 MB/s load speed
        return sizeBytes / (100 * 1024 * 1024) * 1000;
    }

    private long estimateMemoryRequirement(long sizeBytes) {
        // Rough estimate: index size in memory is similar to disk size
        return sizeBytes / (1024 * 1024);
    }

    private String detectIndexType(String indexPath) {
        try {
            if (Files.exists(Paths.get(indexPath + ".index"))) {
                return "memory_index";
            } else if (Files.exists(Paths.get(indexPath + "_disk.index"))) {
                return "disk_index";
            } else {
                return "unknown";
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String executeCommand(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor();
            return output.toString();
            
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Method to track search performance (call from other actions)
    public void recordSearch(String indexName, long latencyMs) {
        searchCounts.computeIfAbsent(indexName, k -> new AtomicLong(0)).incrementAndGet();
        searchLatencies.computeIfAbsent(indexName, k -> new AtomicLong(0)).addAndGet(latencyMs);
    }

    // Method to track index build time (call from other actions)
    public void recordIndexBuild(String indexName, long buildTimeMs) {
        indexBuildTimes.put(indexName, new AtomicLong(buildTimeMs));
    }
}