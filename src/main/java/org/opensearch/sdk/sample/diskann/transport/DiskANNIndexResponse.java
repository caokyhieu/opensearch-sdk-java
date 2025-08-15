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
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * Response for DiskANN index creation operations
 */
public class DiskANNIndexResponse extends ActionResponse implements ToXContentObject {

    private final String indexPath;
    private final String indexType;
    private final int dimensions;
    private final boolean success;
    private final String message;
    private final long buildTimeMs;

    public DiskANNIndexResponse(String indexPath, String indexType, int dimensions, 
                               boolean success, String message, long buildTimeMs) {
        this.indexPath = indexPath;
        this.indexType = indexType;
        this.dimensions = dimensions;
        this.success = success;
        this.message = message;
        this.buildTimeMs = buildTimeMs;
    }

    public DiskANNIndexResponse(StreamInput in) throws IOException {
        super(in);
        this.indexPath = in.readString();
        this.indexType = in.readString();
        this.dimensions = in.readVInt();
        this.success = in.readBoolean();
        this.message = in.readString();
        this.buildTimeMs = in.readVLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(indexPath);
        out.writeString(indexType);
        out.writeVInt(dimensions);
        out.writeBoolean(success);
        out.writeString(message);
        out.writeVLong(buildTimeMs);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("index_path", indexPath);
        builder.field("index_type", indexType);
        builder.field("dimensions", dimensions);
        builder.field("success", success);
        builder.field("message", message);
        builder.field("build_time_ms", buildTimeMs);
        builder.endObject();
        return builder;
    }

    // Getters
    public String getIndexPath() { return indexPath; }
    public String getIndexType() { return indexType; }
    public int getDimensions() { return dimensions; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getBuildTimeMs() { return buildTimeMs; }
}