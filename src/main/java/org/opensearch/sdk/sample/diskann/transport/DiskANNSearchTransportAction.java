/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.transport;

import com.google.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.TransportAction;
import org.opensearch.tasks.Task;
import org.opensearch.tasks.TaskManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Transport action for executing DiskANN search via binary calls
 */
public class DiskANNSearchTransportAction extends TransportAction<DiskANNSearchRequest, DiskANNSearchResponse> {

    @Inject
    protected DiskANNSearchTransportAction(String actionName, ActionFilters actionFilters, TaskManager taskManager) {
        super(actionName, actionFilters, taskManager);
    }

    @Override
    protected void doExecute(Task task, DiskANNSearchRequest request, ActionListener<DiskANNSearchResponse> listener) {
        // Execute DiskANN search asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return executeDiskANNSearch(request);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute DiskANN search", e);
            }
        }).whenComplete((response, throwable) -> {
            if (throwable != null) {
                listener.onFailure(new RuntimeException(throwable));
            } else {
                listener.onResponse(response);
            }
        });
    }

    private DiskANNSearchResponse executeDiskANNSearch(DiskANNSearchRequest request) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        // Create temporary query file
        Path queryFile = createQueryFile(request.getQueryVector());
        
        try {
            // Build command to execute DiskANN search binary
            String[] command = buildSearchCommand(request, queryFile.toString());
            
            // Execute the command
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            long searchTime = System.currentTimeMillis() - startTime;
            
            if (exitCode == 0) {
                // Parse search results from output
                List<DiskANNSearchResponse.SearchHit> hits = parseSearchResults(output.toString(), request.getK());
                return new DiskANNSearchResponse(hits, searchTime, estimateComparisons(request), true, "Search completed successfully");
            } else {
                return new DiskANNSearchResponse(
                    new ArrayList<>(), searchTime, 0, false, 
                    "Search failed with exit code " + exitCode + ": " + output.toString()
                );
            }
        } finally {
            // Clean up temporary query file
            Files.deleteIfExists(queryFile);
        }
    }

    private Path createQueryFile(List<Float> queryVector) throws IOException {
        Path queryFile = Paths.get(System.getProperty("java.io.tmpdir"), "diskann_query_" + System.currentTimeMillis() + ".fvecs");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(queryFile))) {
            // Write vector dimensions
            writer.println(queryVector.size());
            
            // Write vector components
            for (int i = 0; i < queryVector.size(); i++) {
                if (i > 0) writer.print(" ");
                writer.print(queryVector.get(i));
            }
            writer.println();
        }
        
        return queryFile;
    }

    private String[] buildSearchCommand(DiskANNSearchRequest request, String queryFile) {
        return new String[] {
            "diskann_search",
            "--index_path", request.getIndexPath(),
            "--query_file", queryFile,
            "--k", String.valueOf(request.getK()),
            "--beam_width", String.valueOf(request.getBeamWidth()),
            "--output_format", "text"
        };
    }

    private List<DiskANNSearchResponse.SearchHit> parseSearchResults(String output, int k) {
        List<DiskANNSearchResponse.SearchHit> hits = new ArrayList<>();
        
        // Parse the output from DiskANN search
        // This is a simplified parser - actual implementation would depend on DiskANN output format
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            try {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String id = parts[0];
                    double distance = Double.parseDouble(parts[1]);
                    double score = 1.0 / (1.0 + distance); // Convert distance to similarity score
                    
                    hits.add(new DiskANNSearchResponse.SearchHit(id, distance, score));
                    
                    if (hits.size() >= k) {
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                // Skip malformed lines
            }
        }
        
        return hits;
    }

    private int estimateComparisons(DiskANNSearchRequest request) {
        // Rough estimate based on beam width and graph structure
        return request.getBeamWidth() * 20;
    }
}