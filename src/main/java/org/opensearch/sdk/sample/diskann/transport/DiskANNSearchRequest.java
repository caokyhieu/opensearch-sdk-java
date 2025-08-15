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
import java.util.List;

/**
 * Request for DiskANN search operations
 */
public class DiskANNSearchRequest extends ActionRequest {

    private String indexPath;
    private List<Float> queryVector;
    private int k;
    private int beamWidth;
    private double maxDistance;

    public DiskANNSearchRequest() {}

    public DiskANNSearchRequest(String indexPath, List<Float> queryVector, int k, int beamWidth, double maxDistance) {
        this.indexPath = indexPath;
        this.queryVector = queryVector;
        this.k = k;
        this.beamWidth = beamWidth;
        this.maxDistance = maxDistance;
    }

    public DiskANNSearchRequest(StreamInput in) throws IOException {
        super(in);
        this.indexPath = in.readString();
        this.queryVector = in.readList(StreamInput::readFloat);
        this.k = in.readVInt();
        this.beamWidth = in.readVInt();
        this.maxDistance = in.readDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(indexPath);
        out.writeCollection(queryVector, StreamOutput::writeFloat);
        out.writeVInt(k);
        out.writeVInt(beamWidth);
        out.writeDouble(maxDistance);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        
        if (indexPath == null || indexPath.isEmpty()) {
            validationException = new ActionRequestValidationException();
            validationException.addValidationError("index_path cannot be null or empty");
        }
        
        if (queryVector == null || queryVector.isEmpty()) {
            if (validationException == null) {
                validationException = new ActionRequestValidationException();
            }
            validationException.addValidationError("query_vector cannot be null or empty");
        }
        
        if (k <= 0) {
            if (validationException == null) {
                validationException = new ActionRequestValidationException();
            }
            validationException.addValidationError("k must be positive");
        }
        
        return validationException;
    }

    // Getters
    public String getIndexPath() { return indexPath; }
    public List<Float> getQueryVector() { return queryVector; }
    public int getK() { return k; }
    public int getBeamWidth() { return beamWidth; }
    public double getMaxDistance() { return maxDistance; }

    // Setters
    public void setIndexPath(String indexPath) { this.indexPath = indexPath; }
    public void setQueryVector(List<Float> queryVector) { this.queryVector = queryVector; }
    public void setK(int k) { this.k = k; }
    public void setBeamWidth(int beamWidth) { this.beamWidth = beamWidth; }
    public void setMaxDistance(double maxDistance) { this.maxDistance = maxDistance; }
}