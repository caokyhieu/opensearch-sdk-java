/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.transport;

import org.opensearch.action.ActionType;

/**
 * Action type for DiskANN search operations
 */
public class DiskANNSearchAction extends ActionType<DiskANNSearchResponse> {

    public static final DiskANNSearchAction INSTANCE = new DiskANNSearchAction();
    public static final String NAME = "indices:data/read/diskann/search";

    private DiskANNSearchAction() {
        super(NAME, DiskANNSearchResponse::new);
    }
}