/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.extensions.AcknowledgedResponse;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;

import java.io.IOException;

/**
 * This class handles the response {{@link org.opensearch.extensions.AcknowledgedResponse }} from OpenSearch to Extension.
 */
public class AcknowledgedResponseHandler implements TransportResponseHandler<AcknowledgedResponse> {
    private static final Logger logger = LogManager.getLogger(AcknowledgedResponseHandler.class);
    private final String context;

    /**
     * Default constructor for backward compatibility
     */
    public AcknowledgedResponseHandler() {
        this.context = "Extension Request";
    }

    /**
     * Constructor with context for better logging
     * @param context The context/operation being performed
     */
    public AcknowledgedResponseHandler(String context) {
        this.context = context;
    }

    @Override
    public void handleResponse(AcknowledgedResponse response) {
        logger.info("SUCCESS: {} completed - received response: {}", context, response);
    }

    @Override
    public void handleException(TransportException exp) {
        logger.error("CRITICAL FAILURE: {} failed with exception", context, exp);
        logger.error("This indicates a serious communication issue with OpenSearch");
    }

    @Override
    public String executor() {
        return ThreadPool.Names.GENERIC;
    }

    @Override
    public AcknowledgedResponse read(StreamInput in) throws IOException {
        return new AcknowledgedResponse(in);
    }
}
