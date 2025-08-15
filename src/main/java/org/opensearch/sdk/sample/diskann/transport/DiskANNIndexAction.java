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
 * Action type for DiskANN index creation operations
 */
public class DiskANNIndexAction extends ActionType<DiskANNIndexResponse> {

    public static final DiskANNIndexAction INSTANCE = new DiskANNIndexAction();
    public static final String NAME = "cluster:admin/diskann/index/create";

    private DiskANNIndexAction() {
        super(NAME, DiskANNIndexResponse::new);
    }
}