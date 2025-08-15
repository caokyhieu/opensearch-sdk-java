/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann;

import org.opensearch.common.settings.Setting;

/**
 * Configuration settings for DiskANN extension
 */
public class DiskANNSettingsConfig {

    /**
     * Setting for DiskANN binary installation path
     */
    public static final Setting<String> DISKANN_BINARY_PATH = Setting.simpleString(
        "diskann.binary.path",
        "/usr/local/bin/diskann",
        Setting.Property.NodeScope
    );
}