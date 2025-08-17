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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.io.*;
import java.util.concurrent.*;


import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for DiskANN index creation operations.
 * Supports creating both in-memory and SSD-based DiskANN indices.
 */
public class RestDiskANNIndexAction extends BaseExtensionRestHandler {

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(POST, "/diskann/index", handleIndexCreation)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleIndexCreation = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body is required for index creation");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path_prefix");
            String indexType = (String) params.getOrDefault("index_type", "memory");
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceFunction = (String) params.getOrDefault("dist_fn", "l2");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer lBuild = (Integer) params.getOrDefault("lbuild", 100); // Build-time search working set
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 4);
            Double searchDramBudget = (Double) params.getOrDefault("search_dram_budget", 1.0);
            Double buildDramBudget = (Double) params.getOrDefault("build_dram_budget", 2.0);

            if (dataPath == null || indexPath == null) {
                return new ExtensionRestResponse(
                    request, 
                    BAD_REQUEST, 
                    "Required parameters: data_path (input data file in bin format), index_path_prefix (output index path)"
                );
            }

            // Build DiskANN index creation command
            String command = buildDiskANNCommand(indexType, dataPath, indexPath, dataType, distanceFunction, 
                                               maxDegree, lBuild, numThreads, searchDramBudget, buildDramBudget);
            
            // In a real implementation, this would execute the DiskANN binary
            // For this example, we'll simulate the response
            // String simulatedOutput = simulateIndexCreation(command);
            String simulatedOutput = executeCommand(command, 300);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_type", indexType)
                .field("data_type", dataType)
                .field("distance_function", distanceFunction)
                .field("data_path", dataPath)
                .field("index_path_prefix", indexPath)
                .field("max_degree", maxDegree)
                .field("lbuild", lBuild)
                .field("num_threads", numThreads)
                .field("search_dram_budget_gb", searchDramBudget)
                .field("build_dram_budget_gb", buildDramBudget)
                .field("command", command)
                .field("output", simulatedOutput)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private String buildDiskANNCommand(String indexType, String dataPath, String indexPath, 
                                     String dataType, String distFn, Integer maxDegree, 
                                     Integer lBuild, Integer numThreads, Double searchDramBudget, 
                                     Double buildDramBudget) {
        StringBuilder cmd = new StringBuilder();
        
        // Use the actual DiskANN binary from the apps directory
        String binaryPath = "src/main/java/org/opensearch/sdk/sample/diskann/apps/build_disk_index";
        cmd.append(binaryPath);
        
        // Required parameters following Microsoft DiskANN workflow pattern
        cmd.append(" --data_type ").append(dataType)           // float, int8, uint8
           .append(" --dist_fn ").append(distFn)               // l2, cosine, mips
           .append(" --data_path ").append(dataPath)           // input .bin file
           .append(" --index_path_prefix ").append(indexPath); // output index prefix
        
        // Optional parameters with short flags (following DiskANN convention)
        if (maxDegree != null) {
            cmd.append(" -R ").append(maxDegree);  // Graph degree
        }
        
        if (lBuild != null) {
            cmd.append(" -L ").append(lBuild);     // Build search list size
        }
        
        if (searchDramBudget != null) {
            cmd.append(" -B ").append(searchDramBudget);  // Search DRAM budget in GB
        }
        
        if (buildDramBudget != null) {
            cmd.append(" -M ").append(buildDramBudget);   // Build DRAM budget in GB
        }
        
        if (numThreads != null) {
            cmd.append(" -T ").append(numThreads);        // Number of threads
        }
        
        // For SSD-optimized index, add compression parameters
        if ("ssd".equals(indexType)) {
            cmd.append(" --PQ_disk_bytes 16") ;             // Compress to 16 bytes on disk
        }
           
        return cmd.toString();
    }

    private String simulateIndexCreation(String command) {
        return "Index creation completed successfully.\n" +
               "Command executed: " + command + "\n" +
               "Index built with 10000 vectors, 128 dimensions\n" +
               "Build time: 45.2 seconds\n" +
               "Memory usage: 2.1 GB";
    }

    private String executeCommand(String command, long timeoutSeconds) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
    
            // Read output asynchronously
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> outputFuture = executor.submit(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                return output.toString();
            });
    
            // Wait for process to finish or timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String output = "";
    
            try {
                // try to get whatever output we have so far
                output = outputFuture.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                output = "Output reader timeout";
            }
    
            if (!finished) {
                process.destroyForcibly();
                executor.shutdownNow();
                return "Error: Process timed out\nPartial Output:\n" + output;
            }
    
            int exitCode = process.exitValue();
            executor.shutdown();
            return "Exit code: " + exitCode + "\nOutput:\n" + output;
    
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}