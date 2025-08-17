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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for advanced DiskANN search operations.
 * Supports batch search, range search, and streaming operations.
 */
public class RestDiskANNAdvancedSearchAction extends BaseExtensionRestHandler {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(POST, "/diskann/search/batch", handleBatchSearch),
            new RouteHandler(POST, "/diskann/search/range", handleRangeSearch),
            new RouteHandler(POST, "/diskann/search/streaming", handleStreamingSearch),
            new RouteHandler(POST, "/diskann/index/memory/build", handleBuildMemoryIndex),
            new RouteHandler(POST, "/diskann/index/disk/build", handleBuildDiskIndex),
            new RouteHandler(POST, "/diskann/index/stitched/build", handleBuildStitchedIndex)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleBatchSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for batch search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            String queryFile = (String) params.get("query_file");
            String resultFile = (String) params.get("result_file");
            Integer k = (Integer) params.getOrDefault("k", 10);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            String indexType = (String) params.getOrDefault("index_type", "memory");
            
            if (indexPath == null || queryFile == null || resultFile == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, query_file, result_file");
            }

            String command = buildBatchSearchCommand(indexPath, queryFile, resultFile, k, beamWidth, indexType);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_file", queryFile)
                .field("result_file", resultFile)
                .field("k", k)
                .field("beam_width", beamWidth)
                .field("index_type", indexType)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute batch search: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleRangeSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for range search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            String queryFile = (String) params.get("query_file");
            String resultFile = (String) params.get("result_file");
            Double radius = (Double) params.get("radius");
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            
            if (indexPath == null || queryFile == null || resultFile == null || radius == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, query_file, result_file, radius");
            }

            String command = buildRangeSearchCommand(indexPath, queryFile, resultFile, radius, beamWidth);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_file", queryFile)
                .field("result_file", resultFile)
                .field("radius", radius)
                .field("beam_width", beamWidth)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute range search: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleStreamingSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for streaming search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            Integer numInserts = (Integer) params.getOrDefault("num_inserts", 1000);
            Integer numDeletes = (Integer) params.getOrDefault("num_deletes", 100);
            Integer dimensions = (Integer) params.get("dimensions");
            
            if (indexPath == null || dimensions == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, dimensions");
            }

            String command = buildStreamingCommand(indexPath, numInserts, numDeletes, dimensions);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("num_inserts", numInserts)
                .field("num_deletes", numDeletes)
                .field("dimensions", dimensions)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute streaming test: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBuildMemoryIndex = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for memory index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer dimensions = (Integer) params.get("dimensions");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            Double alpha = (Double) params.getOrDefault("alpha", 1.2);
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (dataPath == null || indexPath == null || dimensions == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path, dimensions");
            }

            String command = buildMemoryIndexCommand(dataPath, indexPath, dimensions, maxDegree, beamWidth, alpha, distanceMetric);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("index_path", indexPath)
                .field("dimensions", dimensions)
                .field("max_degree", maxDegree)
                .field("beam_width", beamWidth)
                .field("alpha", alpha)
                .field("distance_metric", distanceMetric)
                .field("command", command)
                .field("build_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to build memory index: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBuildDiskIndex = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for disk index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer dimensions = (Integer) params.get("dimensions");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            Double memoryBudget = (Double) params.getOrDefault("memory_budget", 8.0); // GB
            
            if (dataPath == null || indexPath == null || dimensions == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path, dimensions");
            }

            String command = buildDiskIndexCommand(dataPath, indexPath, dimensions, maxDegree, beamWidth, memoryBudget);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("index_path", indexPath)
                .field("dimensions", dimensions)
                .field("max_degree", maxDegree)
                .field("beam_width", beamWidth)
                .field("memory_budget_gb", memoryBudget)
                .field("command", command)
                .field("build_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to build disk index: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBuildStitchedIndex = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for stitched index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer dimensions = (Integer) params.get("dimensions");
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 4);
            Double memoryBudget = (Double) params.getOrDefault("memory_budget", 8.0);
            
            if (dataPath == null || indexPath == null || dimensions == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path, dimensions");
            }

            String command = buildStitchedIndexCommand(dataPath, indexPath, dimensions, numThreads, memoryBudget);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("index_path", indexPath)
                .field("dimensions", dimensions)
                .field("num_threads", numThreads)
                .field("memory_budget_gb", memoryBudget)
                .field("command", command)
                .field("build_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to build stitched index: " + e.getMessage());
        }
    };

    private String buildBatchSearchCommand(String indexPath, String queryFile, String resultFile, 
                                         int k, int beamWidth, String indexType) {
        String binary = "memory".equals(indexType) ? "search_memory_index" : "search_disk_index";
        return APPS_PATH + "/" + binary + " " +
               "--index_path_prefix " + indexPath + " " +
               "--data_type float " + 
               "--dist_fn l2 " + 
               "--query_file " + queryFile + " " +
               "--gt_file " + resultFile + " " +
               "-K " + k + " " +
               "-L 10 20 30 40 50 100 " +
               "-W " + beamWidth + " " +
               "--result_path " + resultFile;
    }

    private String buildRangeSearchCommand(String indexPath, String queryFile, String resultFile, 
                                         double radius, int beamWidth) {
        return APPS_PATH + "/range_search_disk_index " +
               "--index_path_prefix " + indexPath + " " +
               "--query_file " + queryFile + " " +
               "--range_threshold " + radius + " " +
               "-W " + beamWidth + " " +
               "--result_output_prefix " + resultFile;
    }

    private String buildStreamingCommand(String indexPath, int numInserts, int numDeletes, int dimensions) {
        return APPS_PATH + "/test_streaming_scenario " +
               "--index_path_prefix " + indexPath + " " +
               "--num_points_to_insert " + numInserts + " " +
               "--num_points_to_delete " + numDeletes + " " +
               "--dimension " + dimensions;
    }

    private String buildMemoryIndexCommand(String dataPath, String indexPath, int dimensions, 
                                         int maxDegree, int beamWidth, double alpha, String distanceMetric) {
        return APPS_PATH + "/build_memory_index " +
               "--data_file " + dataPath + " " +
               "--index_path_prefix " + indexPath + " " +
               "--R " + maxDegree + " " +
               "--L " + beamWidth + " " +
               "--alpha " + alpha + " " +
               "--dist_fn " + distanceMetric;
    }

    private String buildDiskIndexCommand(String dataPath, String indexPath, int dimensions, 
                                       int maxDegree, int beamWidth, double memoryBudget) {
        return APPS_PATH + "/build_disk_index " +
               "--data_file " + dataPath + " " +
               "--index_path_prefix " + indexPath + " " +
               "--R " + maxDegree + " " +
               "--L " + beamWidth + " " +
               "--B " + memoryBudget;
    }

    private String buildStitchedIndexCommand(String dataPath, String indexPath, int dimensions, 
                                           int numThreads, double memoryBudget) {
        return APPS_PATH + "/build_stitched_index " +
               "--data_file " + dataPath + " " +
               "--index_path_prefix " + indexPath + " " +
               "--num_threads " + numThreads + " " +
               "--B " + memoryBudget;
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
            
            int exitCode = process.waitFor();
            return "Exit code: " + exitCode + "\nOutput:\n" + output.toString();
            
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}