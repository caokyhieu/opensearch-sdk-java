/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.transport;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

/**
 * Request for DiskANN index creation operations
 */
public class DiskANNIndexRequest extends ActionRequest {

    private String dataPath;
    private String indexPath;
    private String indexType;
    private int dimensions;
    private int maxDegree;
    private int beamWidth;
    private double alpha;

    public DiskANNIndexRequest() {}

    public DiskANNIndexRequest(String dataPath, String indexPath, String indexType, 
                              int dimensions, int maxDegree, int beamWidth, double alpha) {
        this.dataPath = dataPath;
        this.indexPath = indexPath;
        this.indexType = indexType;
        this.dimensions = dimensions;
        this.maxDegree = maxDegree;
        this.beamWidth = beamWidth;
        this.alpha = alpha;
    }

    public DiskANNIndexRequest(StreamInput in) throws IOException {
        super(in);
        this.dataPath = in.readString();
        this.indexPath = in.readString();
        this.indexType = in.readString();
        this.dimensions = in.readVInt();
        this.maxDegree = in.readVInt();
        this.beamWidth = in.readVInt();
        this.alpha = in.readDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(dataPath);
        out.writeString(indexPath);
        out.writeString(indexType);
        out.writeVInt(dimensions);
        out.writeVInt(maxDegree);
        out.writeVInt(beamWidth);
        out.writeDouble(alpha);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        
        if (dataPath == null || dataPath.isEmpty()) {
            validationException = new ActionRequestValidationException();
            validationException.addValidationError("data_path cannot be null or empty");
        }
        
        if (indexPath == null || indexPath.isEmpty()) {
            if (validationException == null) {
                validationException = new ActionRequestValidationException();
            }
            validationException.addValidationError("index_path cannot be null or empty");
        }
        
        if (dimensions <= 0) {
            if (validationException == null) {
                validationException = new ActionRequestValidationException();
            }
            validationException.addValidationError("dimensions must be positive");
        }
        
        return validationException;
    }

    // Getters
    public String getDataPath() { return dataPath; }
    public String getIndexPath() { return indexPath; }
    public String getIndexType() { return indexType; }
    public int getDimensions() { return dimensions; }
    public int getMaxDegree() { return maxDegree; }
    public int getBeamWidth() { return beamWidth; }
    public double getAlpha() { return alpha; }

    // Setters
    public void setDataPath(String dataPath) { this.dataPath = dataPath; }
    public void setIndexPath(String indexPath) { this.indexPath = indexPath; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    public void setMaxDegree(int maxDegree) { this.maxDegree = maxDegree; }
    public void setBeamWidth(int beamWidth) { this.beamWidth = beamWidth; }
    public void setAlpha(double alpha) { this.alpha = alpha; }
}