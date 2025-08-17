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
import org.opensearch.common.settings.Settings;
import org.opensearch.discovery.InitializeExtensionRequest;
import org.opensearch.discovery.InitializeExtensionResponse;
import org.opensearch.sdk.ExtensionsRunner;
import org.opensearch.sdk.SDKTransportService;
import org.opensearch.transport.TransportService;

import static org.opensearch.sdk.ExtensionsRunner.NODE_NAME_SETTING;

/**
 * This class handles the request from OpenSearch to a {@link ExtensionsRunner#startTransportService(TransportService transportService)} call.
 */

public class ExtensionsInitRequestHandler {
    private static final Logger logger = LogManager.getLogger(ExtensionsInitRequestHandler.class);

    // The default http port setting of OpenSearch
    private static final String DEFAULT_HTTP_PORT = "9200";

    // The configured http port setting of opensearch.yml
    private static final String HTTP_PORT_SETTING = "http.port";

    private final ExtensionsRunner extensionsRunner;

    /**
     * Instantiate this object with a reference to the ExtensionsRunner
     *
     * @param extensionsRunner the ExtensionsRunner instance
     */
    public ExtensionsInitRequestHandler(ExtensionsRunner extensionsRunner) {
        this.extensionsRunner = extensionsRunner;
    }

    /**
     * Handles a extension request from OpenSearch. This is the first request for the transport communication and will initialize the extension and will be a part of OpenSearch bootstrap.
     *
     * @param extensionInitRequest  The request to handle.
     * @return A response to OpenSearch validating that this is an extension.
     */
    public InitializeExtensionResponse handleExtensionInitRequest(InitializeExtensionRequest extensionInitRequest) {
        logger.info("=== Starting Extension Initialization Process ===");
        logger.info("Extension ID: {}, Source Node: {}", 
                   extensionInitRequest.getExtension().getId(), 
                   extensionInitRequest.getSourceNode().getAddress());
        
        extensionsRunner.getThreadPool().getThreadContext().putHeader("extension_unique_id", extensionInitRequest.getExtension().getId());
        SDKTransportService sdkTransportService = extensionsRunner.getSdkTransportService();
        sdkTransportService.setOpensearchNode(extensionInitRequest.getSourceNode());
        sdkTransportService.setUniqueId(extensionInitRequest.getExtension().getId());
        
        logger.info("Extension basic setup completed, preparing initialization response");
        
        // Successfully initialized. Send the response.
        try {
            InitializeExtensionResponse response = new InitializeExtensionResponse(
                extensionsRunner.getSettings().get(NODE_NAME_SETTING),
                extensionsRunner.getExtensionImplementedInterfaces()
            );
            logger.info("Extension initialization response created with interfaces: {}", 
                       extensionsRunner.getExtensionImplementedInterfaces());
            return response;
        } finally {
            logger.info("=== Starting Post-Initialization Registration Process ===");
            
            // After sending successful response to initialization, send the REST API and Settings
            extensionsRunner.setExtensionNode(extensionInitRequest.getExtension());
            logger.info("Extension node set on runner");

            logger.info("Connecting to OpenSearch node as extension...");
            TransportService extensionTransportService = sdkTransportService.getTransportService();
            extensionTransportService.connectToNodeAsExtension(
                extensionInitRequest.getSourceNode(),
                extensionInitRequest.getExtension().getId()
            );
            logger.info("Connection to OpenSearch node established");
            
            logger.info("Starting REST actions registration...");
            int registeredPathsCount = extensionsRunner.getExtensionRestPathRegistry().getRegisteredPaths().size();
            logger.info("Extension has {} registered REST paths", registeredPathsCount);
            sdkTransportService.sendRegisterRestActionsRequest(extensionsRunner.getExtensionRestPathRegistry());
            
            logger.info("Starting custom settings registration...");
            int customSettingsCount = extensionsRunner.getCustomSettings().size();
            logger.info("Extension has {} custom settings", customSettingsCount);
            sdkTransportService.sendRegisterCustomSettingsRequest(extensionsRunner.getCustomSettings());
            
            logger.info("Starting transport actions registration...");
            int transportActionsCount = extensionsRunner.getSdkActionModule().getActions().size();
            logger.info("Extension has {} transport actions", transportActionsCount);
            sdkTransportService.sendRegisterTransportActionsRequest(extensionsRunner.getSdkActionModule().getActions());
            
            logger.info("Requesting environment settings from OpenSearch...");
            // Get OpenSearch Settings and set values on ExtensionsRunner
            Settings settings = sdkTransportService.sendEnvironmentSettingsRequest();
            extensionsRunner.setEnvironmentSettings(settings);
            logger.info("Environment settings received and set");
            
            logger.info("Updating NamedXContentRegistry...");
            extensionsRunner.updateNamedXContentRegistry();
            
            logger.info("Updating SDK cluster service...");
            extensionsRunner.updateSdkClusterService();
            
            // Use OpenSearch Settings to update client REST Connections
            String openSearchNodeAddress = extensionInitRequest.getSourceNode().getAddress().getAddress();
            String openSearchNodeHttpPort = settings.get(HTTP_PORT_SETTING) != null ? settings.get(HTTP_PORT_SETTING) : DEFAULT_HTTP_PORT;
            logger.info("Updating SDK client settings - OpenSearch address: {}:{}", openSearchNodeAddress, openSearchNodeHttpPort);
            extensionsRunner.getSdkClient().updateOpenSearchNodeSettings(openSearchNodeAddress, openSearchNodeHttpPort);

            // Last step of initialization
            logger.warn("WARNING: Setting extension as initialized without waiting for async operations to complete (known issue #17)");
            extensionsRunner.setInitialized();
            logger.info("Extension marked as initialized");

            logger.info("Triggering pending settings update consumers...");
            // Trigger pending updates requiring completion of the above actions
            extensionsRunner.getSdkClusterService().getClusterSettings().sendPendingSettingsUpdateConsumers();
            
            logger.info("=== Extension Initialization Process Completed ===");
        }
    }
}
