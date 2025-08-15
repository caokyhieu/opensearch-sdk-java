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
import java.util.concurrent.CompletableFuture;

/**
 * Transport action for executing DiskANN index creation via binary calls
 */
public class DiskANNIndexTransportAction extends TransportAction<DiskANNIndexRequest, DiskANNIndexResponse> {

    @Inject
    protected DiskANNIndexTransportAction(String actionName, ActionFilters actionFilters, TaskManager taskManager) {
        super(actionName, actionFilters, taskManager);
    }

    @Override
    protected void doExecute(Task task, DiskANNIndexRequest request, ActionListener<DiskANNIndexResponse> listener) {
        // Execute DiskANN binary asynchronously to avoid blocking
        CompletableFuture.supplyAsync(() -> {
            try {
                return executeDiskANNBinary(request);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute DiskANN binary", e);
            }
        }).whenComplete((response, throwable) -> {
            if (throwable != null) {
                listener.onFailure(new RuntimeException(throwable));
            } else {
                listener.onResponse(response);
            }
        });
    }

    private DiskANNIndexResponse executeDiskANNBinary(DiskANNIndexRequest request) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        // Build command to execute DiskANN binary
        String[] command = buildCommand(request);
        
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
        long buildTime = System.currentTimeMillis() - startTime;
        
        boolean success = exitCode == 0;
        String message = success ? 
            "DiskANN index created successfully\n" + output.toString() :
            "DiskANN index creation failed with exit code " + exitCode + "\n" + output.toString();
        
        return new DiskANNIndexResponse(
            request.getIndexPath(),
            request.getIndexType(),
            request.getDimensions(),
            success,
            message,
            buildTime
        );
    }

    private String[] buildCommand(DiskANNIndexRequest request) {
        String binary = "ssd".equals(request.getIndexType()) ? "diskann_ssd_index" : "diskann_index";
        
        return new String[] {
            binary,
            "--data_path", request.getDataPath(),
            "--index_path", request.getIndexPath(),
            "--dimensions", String.valueOf(request.getDimensions()),
            "--max_degree", String.valueOf(request.getMaxDegree()),
            "--beam_width", String.valueOf(request.getBeamWidth()),
            "--alpha", String.valueOf(request.getAlpha())
        };
    }
}