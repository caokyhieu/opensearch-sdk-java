/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.sdk.sample.diskann.rest;

import org.opensearch.OpenSearchParseException;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.extensions.rest.ExtensionRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.sdk.rest.BaseExtensionRestHandler;
import org.opensearch.sdk.rest.ExtensionRestHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for DiskANN data preparation and validation operations.
 * Supports data format conversion, validation, and analysis.
 */
public class RestDiskANNDataAction extends BaseExtensionRestHandler {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";
    private static final String DATA_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/data";

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            new RouteHandler(POST, "/diskann/data/convert", handleDataConversion),
            new RouteHandler(POST, "/diskann/data/validate", handleDataValidation),
            new RouteHandler(GET, "/diskann/data/analyze/{dataset}", handleDataAnalysis),
            new RouteHandler(POST, "/diskann/data/generate", handleDataGeneration)
        );
    }

    private Function<RestRequest, ExtensionRestResponse> handleDataConversion = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for data conversion");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String inputPath = (String) params.get("input_path");
            String outputPath = (String) params.get("output_path");
            String inputFormat = (String) params.get("input_format"); // fvecs, bin, tsv
            String outputFormat = (String) params.get("output_format"); // fvecs, bin, tsv
            
            if (inputPath == null || outputPath == null || inputFormat == null || outputFormat == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: input_path, output_path, input_format, output_format");
            }

            String command = buildConversionCommand(inputPath, outputPath, inputFormat, outputFormat);
            String result = executeDataCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("input_path", inputPath)
                .field("output_path", outputPath)
                .field("input_format", inputFormat)
                .field("output_format", outputFormat)
                .field("command", command)
                .field("result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleDataValidation = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for data validation");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String dataFormat = (String) params.get("data_format");
            
            if (dataPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, "Required: data_path");
            }

            String command = buildValidationCommand(dataPath, dataFormat);
            String result = executeDataCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("data_format", dataFormat)
                .field("validation_result", result)
                .field("file_exists", Files.exists(Paths.get(dataPath)))
                .field("file_size_bytes", getFileSize(dataPath))
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleDataAnalysis = (request) -> {
        String dataset = request.param("dataset");
        
        try {
            String dataPath = DATA_PATH + "/" + dataset;
            String command = APPS_PATH + "/utils/vector_analysis " + dataPath;
            String result = executeDataCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("dataset", dataset)
                .field("data_path", dataPath)
                .field("analysis_result", result)
                .field("available_files", getAvailableFiles(dataPath))
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to analyze data: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleDataGeneration = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for data generation");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String outputPath = (String) params.get("output_path");
            Integer numVectors = (Integer) params.get("num_vectors");
            Integer dimensions = (Integer) params.get("dimensions");
            String dataType = (String) params.getOrDefault("data_type", "float");
            
            if (outputPath == null || numVectors == null || dimensions == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: output_path, num_vectors, dimensions");
            }

            String command = buildGenerationCommand(outputPath, numVectors, dimensions, dataType);
            String result = executeDataCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("output_path", outputPath)
                .field("num_vectors", numVectors)
                .field("dimensions", dimensions)
                .field("data_type", dataType)
                .field("command", command)
                .field("result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to parse request: " + e.getMessage());
        }
    };

    private String buildConversionCommand(String inputPath, String outputPath, String inputFormat, String outputFormat) {
        if ("fvecs".equals(inputFormat) && "bin".equals(outputFormat)) {
            return APPS_PATH + "/utils/fvecs_to_bin " + inputPath + " " + outputPath;
        } else if ("bin".equals(inputFormat) && "tsv".equals(outputFormat)) {
            return APPS_PATH + "/utils/bin_to_tsv " + inputPath + " " + outputPath;
        } else if ("tsv".equals(inputFormat) && "bin".equals(outputFormat)) {
            return APPS_PATH + "/utils/tsv_to_bin " + inputPath + " " + outputPath;
        } else if ("fvecs".equals(inputFormat) && "bvecs".equals(outputFormat)) {
            return APPS_PATH + "/utils/fvecs_to_bvecs " + inputPath + " " + outputPath;
        }
        throw new IllegalArgumentException("Unsupported conversion: " + inputFormat + " to " + outputFormat);
    }

    private String buildValidationCommand(String dataPath, String dataFormat) {
        return APPS_PATH + "/utils/vector_analysis " + dataPath;
    }

    private String buildGenerationCommand(String outputPath, int numVectors, int dimensions, String dataType) {
        return APPS_PATH + "/utils/rand_data_gen " + 
               "--data_type " + dataType + 
               " --output_file " + outputPath + 
               " --num_points " + numVectors + 
               " --dim " + dimensions;
    }

    private String executeDataCommand(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            return "Exit code: " + exitCode + "\nOutput:\n" + output.toString();
            
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            return -1;
        }
    }

    private List<String> getAvailableFiles(String directory) {
        try {
            return Files.list(Paths.get(directory))
                .map(path -> path.getFileName().toString())
                .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}