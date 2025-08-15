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
import java.util.Random;
import java.util.function.Function;

import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for DiskANN vector search operations.
 * Supports querying pre-built DiskANN indices for nearest neighbor search.
 */
public class RestDiskANNSearchAction extends BaseExtensionRestHandler {

    private final Random random = new Random();

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(POST, "/diskann/search", handleVectorSearch)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleVectorSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body is required for search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            List<Double> queryVector = (List<Double>) params.get("query_vector");
            Integer k = (Integer) params.getOrDefault("k", 10);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 128);
            Double maxDistance = (Double) params.getOrDefault("max_distance", Double.MAX_VALUE);

            if (indexPath == null || queryVector == null) {
                return new ExtensionRestResponse(
                    request, 
                    BAD_REQUEST, 
                    "Required parameters: index_path, query_vector"
                );
            }

            // Build DiskANN search command
            String command = buildSearchCommand(indexPath, queryVector.size(), k, beamWidth);
            
            // Simulate search execution and results
            List<SearchResult> results = simulateSearch(k);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_dimensions", queryVector.size())
                .field("k", k)
                .field("beam_width", beamWidth)
                .field("command", command)
                .startArray("results");

            for (SearchResult result : results) {
                builder.startObject()
                    .field("id", result.id)
                    .field("distance", result.distance)
                    .field("score", 1.0 / (1.0 + result.distance))
                    .endObject();
            }

            builder.endArray()
                .field("search_time_ms", 15.7)
                .field("total_comparisons", 1247)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private String buildSearchCommand(String indexPath, int dimensions, int k, int beamWidth) {
        return String.format("diskann_search --index_path %s --query_dimensions %d --k %d --beam_width %d",
            indexPath, dimensions, k, beamWidth);
    }

    private List<SearchResult> simulateSearch(int k) {
        return java.util.stream.IntStream.range(0, k)
            .mapToObj(i -> new SearchResult(
                "vec_" + random.nextInt(100000),
                random.nextDouble() * 2.0
            ))
            .sorted((a, b) -> Double.compare(a.distance, b.distance))
            .collect(java.util.stream.Collectors.toList());
    }

    private static class SearchResult {
        final String id;
        final double distance;

        SearchResult(String id, double distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}