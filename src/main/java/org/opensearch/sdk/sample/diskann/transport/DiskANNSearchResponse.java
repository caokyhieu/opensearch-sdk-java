/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.transport;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;

/**
 * Response for DiskANN search operations
 */
public class DiskANNSearchResponse extends ActionResponse implements ToXContentObject {

    private final List<SearchHit> hits;
    private final long searchTimeMs;
    private final int totalComparisons;
    private final boolean success;
    private final String message;

    public DiskANNSearchResponse(List<SearchHit> hits, long searchTimeMs, int totalComparisons, 
                                boolean success, String message) {
        this.hits = hits;
        this.searchTimeMs = searchTimeMs;
        this.totalComparisons = totalComparisons;
        this.success = success;
        this.message = message;
    }

    public DiskANNSearchResponse(StreamInput in) throws IOException {
        super(in);
        this.hits = in.readList(SearchHit::new);
        this.searchTimeMs = in.readVLong();
        this.totalComparisons = in.readVInt();
        this.success = in.readBoolean();
        this.message = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(hits, (stream, hit) -> hit.writeTo(stream));
        out.writeVLong(searchTimeMs);
        out.writeVInt(totalComparisons);
        out.writeBoolean(success);
        out.writeString(message);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("success", success);
        builder.field("message", message);
        builder.field("search_time_ms", searchTimeMs);
        builder.field("total_comparisons", totalComparisons);
        
        builder.startArray("hits");
        for (SearchHit hit : hits) {
            builder.startObject();
            builder.field("id", hit.getId());
            builder.field("distance", hit.getDistance());
            builder.field("score", hit.getScore());
            builder.endObject();
        }
        builder.endArray();
        
        builder.endObject();
        return builder;
    }

    // Getters
    public List<SearchHit> getHits() { return hits; }
    public long getSearchTimeMs() { return searchTimeMs; }
    public int getTotalComparisons() { return totalComparisons; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }

    /**
     * Represents a single search result hit
     */
    public static class SearchHit implements Writeable {
        private final String id;
        private final double distance;
        private final double score;

        public SearchHit(String id, double distance, double score) {
            this.id = id;
            this.distance = distance;
            this.score = score;
        }

        public SearchHit(StreamInput in) throws IOException {
            this.id = in.readString();
            this.distance = in.readDouble();
            this.score = in.readDouble();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(id);
            out.writeDouble(distance);
            out.writeDouble(score);
        }

        public String getId() { return id; }
        public double getDistance() { return distance; }
        public double getScore() { return score; }
    }
}