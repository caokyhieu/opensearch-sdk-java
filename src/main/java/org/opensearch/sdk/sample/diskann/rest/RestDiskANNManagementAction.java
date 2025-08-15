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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.opensearch.rest.RestRequest.Method.DELETE;
import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.NOT_FOUND;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for DiskANN index management operations.
 * Supports listing, deleting, getting info about indices, and performance testing.
 */
public class RestDiskANNManagementAction extends BaseExtensionRestHandler {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(GET, "/diskann/indices", handleListIndices),
            new RouteHandler(GET, "/diskann/indices/{indexName}/info", handleGetIndexInfo),
            new RouteHandler(DELETE, "/diskann/indices/{indexName}", handleDeleteIndex),
            new RouteHandler(POST, "/diskann/indices/{indexName}/test", handleTestIndex),
            new RouteHandler(POST, "/diskann/groundtruth/compute", handleComputeGroundtruth),
            new RouteHandler(POST, "/diskann/recall/calculate", handleCalculateRecall)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleListIndices = (request) -> {
        try {
            String indexDirectory = request.param("directory", "indices");
            Path indexDir = Paths.get(indexDirectory);
            
            List<Map<String, Object>> indices = List.of();
            if (Files.exists(indexDir) && Files.isDirectory(indexDir)) {
                indices = Files.list(indexDir)
                    .filter(Files::isDirectory)
                    .map(this::getIndexInfo)
                    .collect(Collectors.toList());
            }

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_directory", indexDirectory)
                .field("total_indices", indices.size())
                .field("indices", indices)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to list indices: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleGetIndexInfo = (request) -> {
        String indexName = request.param("indexName");
        
        try {
            String indexPath = "indices/" + indexName;
            Path indexDir = Paths.get(indexPath);
            
            if (!Files.exists(indexDir)) {
                return new ExtensionRestResponse(request, NOT_FOUND, "Index not found: " + indexName);
            }

            Map<String, Object> indexInfo = getDetailedIndexInfo(indexDir);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_name", indexName)
                .field("index_path", indexPath)
                .field("info", indexInfo)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to get index info: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleDeleteIndex = (request) -> {
        String indexName = request.param("indexName");
        
        try {
            String indexPath = "indices/" + indexName;
            Path indexDir = Paths.get(indexPath);
            
            if (!Files.exists(indexDir)) {
                return new ExtensionRestResponse(request, NOT_FOUND, "Index not found: " + indexName);
            }

            boolean deleted = deleteDirectory(indexDir);
            
            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", deleted ? "success" : "failed")
                .field("index_name", indexName)
                .field("index_path", indexPath)
                .field("deleted", deleted)
                .endObject();

            return new ExtensionRestResponse(request, deleted ? OK : BAD_REQUEST, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to delete index: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleTestIndex = (request) -> {
        String indexName = request.param("indexName");
        
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for index testing");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String queryPath = (String) params.get("query_path");
            String groundtruthPath = (String) params.get("groundtruth_path");
            Integer k = (Integer) params.getOrDefault("k", 10);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            
            String indexPath = "indices/" + indexName;
            String command = buildTestCommand(indexPath, queryPath, groundtruthPath, k, beamWidth);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_name", indexName)
                .field("test_command", command)
                .field("test_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to test index: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleComputeGroundtruth = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for groundtruth computation");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String queryPath = (String) params.get("query_path");
            String outputPath = (String) params.get("output_path");
            Integer k = (Integer) params.getOrDefault("k", 100);
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (dataPath == null || queryPath == null || outputPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, query_path, output_path");
            }

            String command = APPS_PATH + "/utils/compute_groundtruth " +
                           "--data_file " + dataPath + " " +
                           "--query_file " + queryPath + " " +
                           "--gt_file " + outputPath + " " +
                           "--K " + k + " " +
                           "--dist_fn " + distanceMetric;
            
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("query_path", queryPath)
                .field("output_path", outputPath)
                .field("k", k)
                .field("distance_metric", distanceMetric)
                .field("command", command)
                .field("result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to compute groundtruth: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleCalculateRecall = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for recall calculation");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String groundtruthPath = (String) params.get("groundtruth_path");
            String resultPath = (String) params.get("result_path");
            Integer k = (Integer) params.getOrDefault("k", 10);
            
            if (groundtruthPath == null || resultPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: groundtruth_path, result_path");
            }

            String command = APPS_PATH + "/utils/calculate_recall " +
                           "--result_file " + resultPath + " " +
                           "--gt_file " + groundtruthPath + " " +
                           "--K " + k;
            
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("groundtruth_path", groundtruthPath)
                .field("result_path", resultPath)
                .field("k", k)
                .field("command", command)
                .field("recall_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to calculate recall: " + e.getMessage());
        }
    };

    private Map<String, Object> getIndexInfo(Path indexPath) {
        try {
            return Map.of(
                "name", indexPath.getFileName().toString(),
                "path", indexPath.toString(),
                "size_bytes", getDirectorySize(indexPath),
                "last_modified", Files.getLastModifiedTime(indexPath).toString(),
                "files", Files.list(indexPath).map(p -> p.getFileName().toString()).collect(Collectors.toList())
            );
        } catch (IOException e) {
            return Map.of(
                "name", indexPath.getFileName().toString(),
                "path", indexPath.toString(),
                "error", e.getMessage()
            );
        }
    }

    private Map<String, Object> getDetailedIndexInfo(Path indexPath) throws IOException {
        return Map.of(
            "name", indexPath.getFileName().toString(),
            "path", indexPath.toString(),
            "size_bytes", getDirectorySize(indexPath),
            "last_modified", Files.getLastModifiedTime(indexPath).toString(),
            "file_count", Files.list(indexPath).count(),
            "files", Files.list(indexPath).map(p -> Map.of(
                "name", p.getFileName().toString(),
                "size", getFileSize(p),
                "last_modified", getLastModified(p)
            )).collect(Collectors.toList())
        );
    }

    private String buildTestCommand(String indexPath, String queryPath, String groundtruthPath, int k, int beamWidth) {
        return APPS_PATH + "/search_memory_index " +
               "--index_path " + indexPath + " " +
               "--query_file " + queryPath + " " +
               "--gt_file " + groundtruthPath + " " +
               "-K " + k + " " +
               "-W " + beamWidth;
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

    private boolean deleteDirectory(Path directory) {
        try {
            Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Log but continue
                    }
                });
            return !Files.exists(directory);
        } catch (IOException e) {
            return false;
        }
    }

    private long getDirectorySize(Path directory) {
        try {
            return Files.walk(directory)
                .filter(Files::isRegularFile)
                .mapToLong(this::getFileSize)
                .sum();
        } catch (IOException e) {
            return -1;
        }
    }

    private long getFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private String getLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toString();
        } catch (IOException e) {
            return "unknown";
        }
    }
}