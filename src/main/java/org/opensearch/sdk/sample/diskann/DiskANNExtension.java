/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann;

import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.common.settings.Setting;
import org.opensearch.sdk.BaseExtension;
import org.opensearch.sdk.Extension;
import org.opensearch.sdk.ExtensionSettings;
import org.opensearch.sdk.ExtensionsRunner;
import org.opensearch.sdk.api.ActionExtension;
import org.opensearch.sdk.rest.ExtensionRestHandler;
import org.opensearch.sdk.sample.diskann.rest.RestDiskANNIndexAction;
import org.opensearch.sdk.sample.diskann.rest.RestDiskANNSearchAction;
import org.opensearch.sdk.sample.diskann.transport.DiskANNIndexAction;
import org.opensearch.sdk.sample.diskann.transport.DiskANNIndexTransportAction;
import org.opensearch.sdk.sample.diskann.transport.DiskANNSearchAction;
import org.opensearch.sdk.sample.diskann.transport.DiskANNSearchTransportAction;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Sample DiskANN extension demonstrating integration with DiskANN binary executables.
 * This extension provides REST APIs and transport actions for creating indices
 * and performing vector searches using Microsoft's DiskANN library.
 * 
 * To use this extension:
 * 1. Ensure DiskANN binaries are installed and accessible in system PATH
 * 2. Configure the diskann.binary.path setting to point to DiskANN installation
 * 3. Use the REST endpoints to create indices and perform searches
 */
public class DiskANNExtension extends BaseExtension implements ActionExtension {

    /**
     * Extension settings file path
     */
    private static final String EXTENSION_SETTINGS_PATH = "/sample/diskann-settings.yml";

    /**
     * Instantiate this extension with DiskANN-specific settings
     */
    public DiskANNExtension() {
        super(EXTENSION_SETTINGS_PATH);
    }

    @Override
    public List<ExtensionRestHandler> getExtensionRestHandlers() {
        return List.of(
            new RestDiskANNIndexAction(),
            new RestDiskANNSearchAction(),
            new org.opensearch.sdk.sample.diskann.rest.RestDiskANNDataAction(),
            new org.opensearch.sdk.sample.diskann.rest.RestDiskANNManagementAction(),
            new org.opensearch.sdk.sample.diskann.rest.RestDiskANNAdvancedSearchAction(),
            new org.opensearch.sdk.sample.diskann.rest.RestDiskANNMonitoringAction()
        );
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return Arrays.asList(
            new ActionHandler<>(DiskANNIndexAction.INSTANCE, DiskANNIndexTransportAction.class),
            new ActionHandler<>(DiskANNSearchAction.INSTANCE, DiskANNSearchTransportAction.class)
        );
    }

    @Override
    public List<Setting<?>> getSettings() {
        return Arrays.asList(DiskANNSettingsConfig.DISKANN_BINARY_PATH);
    }

    /**
     * Entry point to execute the DiskANN extension.
     *
     * @param args  Unused.
     * @throws IOException on a failure in the ExtensionsRunner
     */
    public static void main(String[] args) throws IOException {
        ExtensionsRunner.run(new DiskANNExtension());
    }
}