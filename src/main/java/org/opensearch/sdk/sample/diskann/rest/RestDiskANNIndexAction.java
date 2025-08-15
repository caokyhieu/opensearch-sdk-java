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
            String indexPath = (String) params.get("index_path");
            String indexType = (String) params.getOrDefault("index_type", "memory");
            Integer dimensions = (Integer) params.get("dimensions");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            Double alpha = (Double) params.getOrDefault("alpha", 1.2);

            if (dataPath == null || indexPath == null || dimensions == null) {
                return new ExtensionRestResponse(
                    request, 
                    BAD_REQUEST, 
                    "Required parameters: data_path, index_path, dimensions"
                );
            }

            // Simulate DiskANN index creation process
            String command = buildDiskANNCommand(indexType, dataPath, indexPath, dimensions, maxDegree, beamWidth, alpha);
            
            // In a real implementation, this would execute the DiskANN binary
            // For this example, we'll simulate the response
            String simulatedOutput = simulateIndexCreation(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_type", indexType)
                .field("index_path", indexPath)
                .field("dimensions", dimensions)
                .field("command", command)
                .field("output", simulatedOutput)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private String buildDiskANNCommand(String indexType, String dataPath, String indexPath, 
                                     Integer dimensions, Integer maxDegree, Integer beamWidth, Double alpha) {
        StringBuilder cmd = new StringBuilder();
        
        if ("ssd".equals(indexType)) {
            cmd.append("diskann_ssd_index");
        } else {
            cmd.append("diskann_index");
        }
        
        cmd.append(" --data_path ").append(dataPath)
           .append(" --index_path ").append(indexPath)
           .append(" --dimensions ").append(dimensions)
           .append(" --max_degree ").append(maxDegree)
           .append(" --beam_width ").append(beamWidth)
           .append(" --alpha ").append(alpha);
           
        return cmd.toString();
    }

    private String simulateIndexCreation(String command) {
        return "Index creation completed successfully.\n" +
               "Command executed: " + command + "\n" +
               "Index built with 10000 vectors, 128 dimensions\n" +
               "Build time: 45.2 seconds\n" +
               "Memory usage: 2.1 GB";
    }
}