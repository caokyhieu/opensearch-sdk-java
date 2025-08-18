
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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.UUID;

import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.OK;

/**
 * REST handler for advanced DiskANN search operations.
 * Supports batch search, range search, and streaming operations with async support.
 */
public class RestDiskANNAdvancedSearchAction extends BaseExtensionRestHandler {

    private static final String APPS_PATH = "src/main/java/org/opensearch/sdk/sample/diskann/apps";
    
    // Async execution support
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private static final ConcurrentHashMap<String, JobStatus> runningJobs = new ConcurrentHashMap<>();
    
    // Job status tracking
    public static class JobStatus {
        public final String jobId;
        public final String operation;
        public final String status; // "running", "completed", "failed"
        public final String result;
        public final long startTime;
        public final long endTime;
        
        public JobStatus(String jobId, String operation, String status, String result, long startTime, long endTime) {
            this.jobId = jobId;
            this.operation = operation;
            this.status = status;
            this.result = result;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    @Override
    public List<RouteHandler> routeHandlers() {
        return List.of(
            // Search operations (typically fast)
            new RouteHandler(POST, "/diskann/search/batch", handleBatchSearch),
            new RouteHandler(POST, "/diskann/search/range", handleRangeSearch),
            new RouteHandler(POST, "/diskann/search/streaming", handleStreamingSearch),
            
            // Build operations (async - can be very slow)
            new RouteHandler(POST, "/diskann/index/memory/build", handleBuildMemoryIndexAsync),
            new RouteHandler(POST, "/diskann/index/disk/build", handleBuildDiskIndexAsync),
            new RouteHandler(POST, "/diskann/index/stitched/build", handleBuildStitchedIndexAsync),
            
            // Job management
            new RouteHandler(GET, "/diskann/jobs/{jobId}/status", handleJobStatus),
            new RouteHandler(GET, "/diskann/jobs", handleListJobs)
        );
    }

    // =================================
    // SEARCH OPERATIONS (SYNCHRONOUS)
    // =================================
    
    private Function<RestRequest, ExtensionRestResponse> handleBatchSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for batch search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            String queryFile = (String) params.get("query_file");
            String resultPath = (String) params.get("result_path");
            String gtFile = (String) params.get("gt_file");  // Optional ground truth file
            Integer k = (Integer) params.getOrDefault("k", 10);
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 2);
            String indexType = (String) params.getOrDefault("index_type", "memory");
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 12);
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (indexPath == null || queryFile == null || resultPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, query_file, result_path");
            }

            String command = buildBatchSearchCommand(indexPath, queryFile, resultPath, gtFile, k, beamWidth, indexType, numThreads, dataType, distanceMetric);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_file", queryFile)
                .field("result_path", resultPath)
                .field("gt_file", gtFile)
                .field("k", k)
                .field("beam_width", beamWidth)
                .field("index_type", indexType)
                .field("num_threads", numThreads)
                .field("data_type", dataType)
                .field("distance_metric", distanceMetric)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute batch search: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleRangeSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for range search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String indexPath = (String) params.get("index_path");
            String queryFile = (String) params.get("query_file");
            String resultOutputPrefix = (String) params.get("result_output_prefix");
            Double rangeThreshold = (Double) params.get("range_threshold");
            Integer beamWidth = (Integer) params.getOrDefault("beam_width", 2);
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 1);
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (indexPath == null || queryFile == null || resultOutputPrefix == null || rangeThreshold == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: index_path, query_file, result_output_prefix, range_threshold");
            }

            String command = buildRangeSearchCommand(indexPath, queryFile, resultOutputPrefix, rangeThreshold, beamWidth, numThreads, dataType, distanceMetric);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("index_path", indexPath)
                .field("query_file", queryFile)
                .field("result_output_prefix", resultOutputPrefix)
                .field("range_threshold", rangeThreshold)
                .field("beam_width", beamWidth)
                .field("num_threads", numThreads)
                .field("data_type", dataType)
                .field("distance_metric", distanceMetric)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute range search: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleStreamingSearch = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for streaming search");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");  // Data file for streaming test
            String indexPath = (String) params.get("index_path");
            Integer maxPointsToInsert = (Integer) params.getOrDefault("max_points_to_insert", 1000);
            Integer activeWindow = (Integer) params.getOrDefault("active_window", 4000);
            Integer consolidateInterval = (Integer) params.getOrDefault("consolidate_interval", 2000);
            Integer insertThreads = (Integer) params.getOrDefault("insert_threads", 4);
            Integer consolidateThreads = (Integer) params.getOrDefault("consolidate_threads", 4);
            Double startPointNorm = (Double) params.get("start_point_norm");  // Required
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer lBuild = (Integer) params.getOrDefault("l_build", 600);
            Double alpha = (Double) params.getOrDefault("alpha", 1.2);
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (indexPath == null || startPointNorm == null || dataPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path, start_point_norm");
            }

            String command = buildStreamingCommand(dataPath, indexPath, maxPointsToInsert, activeWindow, consolidateInterval, 
                                                  insertThreads, consolidateThreads, startPointNorm, maxDegree, lBuild, alpha, dataType, distanceMetric);
            String result = executeCommand(command);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "success")
                .field("data_path", dataPath)
                .field("index_path", indexPath)
                .field("max_points_to_insert", maxPointsToInsert)
                .field("active_window", activeWindow)
                .field("consolidate_interval", consolidateInterval)
                .field("insert_threads", insertThreads)
                .field("consolidate_threads", consolidateThreads)
                .field("start_point_norm", startPointNorm)
                .field("max_degree", maxDegree)
                .field("l_build", lBuild)
                .field("alpha", alpha)
                .field("data_type", dataType)
                .field("distance_metric", distanceMetric)
                .field("command", command)
                .field("execution_result", result)
                .endObject();

            return new ExtensionRestResponse(request, OK, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to execute streaming test: " + e.getMessage());
        }
    };

    // =================================
    // BUILD OPERATIONS (ASYNCHRONOUS)
    // =================================

    private Function<RestRequest, ExtensionRestResponse> handleBuildMemoryIndexAsync = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for memory index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer lBuild = (Integer) params.getOrDefault("l_build", 100);
            Double alpha = (Double) params.getOrDefault("alpha", 1.2);
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            String dataType = (String) params.getOrDefault("data_type", "float");
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 1);
            
            if (dataPath == null || indexPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path");
            }

            String jobId = UUID.randomUUID().toString();
            String command = buildMemoryIndexCommand(dataPath, indexPath, maxDegree, lBuild, alpha, distanceMetric, dataType, numThreads);
            
            runningJobs.put(jobId, new JobStatus(jobId, "build_memory_index", "running", null, 
                                               System.currentTimeMillis(), 0));
            
            CompletableFuture.supplyAsync(() -> executeCommandLongRunning(command), executorService)
                .thenAccept(result -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_memory_index", "completed", 
                                                       result, runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                })
                .exceptionally(throwable -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_memory_index", "failed", 
                                                       throwable.getMessage(), runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                    return null;
                });

            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("data_path", dataPath);
            parameters.put("index_path", indexPath);
            parameters.put("max_degree", maxDegree);
            parameters.put("l_build", lBuild);
            parameters.put("alpha", alpha);
            parameters.put("distance_metric", distanceMetric);
            parameters.put("data_type", dataType);
            parameters.put("num_threads", numThreads);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "accepted")
                .field("job_id", jobId)
                .field("message", "Memory index build started. Use /diskann/jobs/" + jobId + "/status to check progress")
                .field("estimated_time", "This may take several minutes")
                .field("command", command)
                .field("parameters", parameters)
                .endObject();

            return new ExtensionRestResponse(request, org.opensearch.core.rest.RestStatus.ACCEPTED, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to start memory index build: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBuildDiskIndexAsync = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for disk index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer maxDegree = (Integer) params.getOrDefault("max_degree", 64);
            Integer lBuild = (Integer) params.getOrDefault("l_build", 100);
            Double searchMemoryBudget = (Double) params.getOrDefault("search_memory_budget", 8.0);
            Double buildMemoryBudget = (Double) params.getOrDefault("build_memory_budget", 10.0);
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 1);
            Integer pqDiskBytes = (Integer) params.getOrDefault("pq_disk_bytes", 0);
            Integer buildPqBytes = (Integer) params.getOrDefault("build_pq_bytes", 0);
            Boolean useOpq = (Boolean) params.getOrDefault("use_opq", false);
            
            if (dataPath == null || indexPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path");
            }

            String jobId = UUID.randomUUID().toString();
            String command = buildDiskIndexCommand(dataPath, indexPath, maxDegree, lBuild, searchMemoryBudget, 
                                                  buildMemoryBudget, dataType, distanceMetric, numThreads, 
                                                  pqDiskBytes, buildPqBytes, useOpq);
            
            runningJobs.put(jobId, new JobStatus(jobId, "build_disk_index", "running", null, 
                                               System.currentTimeMillis(), 0));
            
            CompletableFuture.supplyAsync(() -> executeCommandLongRunning(command), executorService)
                .thenAccept(result -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_disk_index", "completed", 
                                                       result, runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                })
                .exceptionally(throwable -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_disk_index", "failed", 
                                                       throwable.getMessage(), runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                    return null;
                });

            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("data_path", dataPath);
            parameters.put("index_path", indexPath);
            parameters.put("max_degree", maxDegree);
            parameters.put("l_build", lBuild);
            parameters.put("search_memory_budget_gb", searchMemoryBudget);
            parameters.put("build_memory_budget_gb", buildMemoryBudget);
            parameters.put("data_type", dataType);
            parameters.put("distance_metric", distanceMetric);
            parameters.put("num_threads", numThreads);
            parameters.put("pq_disk_bytes", pqDiskBytes);
            parameters.put("build_pq_bytes", buildPqBytes);
            parameters.put("use_opq", useOpq);

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "accepted")
                .field("job_id", jobId)
                .field("message", "Disk index build started. Use /diskann/jobs/" + jobId + "/status to check progress")
                .field("estimated_time", "This may take 10+ minutes for large datasets")
                .field("command", command)
                .field("parameters", parameters)
                .endObject();

            return new ExtensionRestResponse(request, org.opensearch.core.rest.RestStatus.ACCEPTED, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to start disk index build: " + e.getMessage());
        }
    };

    private Function<RestRequest, ExtensionRestResponse> handleBuildStitchedIndexAsync = (request) -> {
        if (!request.hasContent()) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Request body required for stitched index build");
        }

        try {
            Map<String, Object> params = request.contentParser().map();
            
            String dataPath = (String) params.get("data_path");
            String indexPath = (String) params.get("index_path");
            Integer numThreads = (Integer) params.getOrDefault("num_threads", 4);
            Double memoryBudget = (Double) params.getOrDefault("memory_budget", 8.0);
            String dataType = (String) params.getOrDefault("data_type", "float");
            String distanceMetric = (String) params.getOrDefault("distance_metric", "l2");
            
            if (dataPath == null || indexPath == null) {
                return new ExtensionRestResponse(request, BAD_REQUEST, 
                    "Required: data_path, index_path");
            }

            String jobId = UUID.randomUUID().toString();
            String command = buildStitchedIndexCommand(dataPath, indexPath, numThreads, memoryBudget, dataType, distanceMetric);
            
            runningJobs.put(jobId, new JobStatus(jobId, "build_stitched_index", "running", null, 
                                               System.currentTimeMillis(), 0));
            
            CompletableFuture.supplyAsync(() -> executeCommandLongRunning(command), executorService)
                .thenAccept(result -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_stitched_index", "completed", 
                                                       result, runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                })
                .exceptionally(throwable -> {
                    runningJobs.put(jobId, new JobStatus(jobId, "build_stitched_index", "failed", 
                                                       throwable.getMessage(), runningJobs.get(jobId).startTime, 
                                                       System.currentTimeMillis()));
                    return null;
                });

            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("status", "accepted")
                .field("job_id", jobId)
                .field("message", "Stitched index build started. Use /diskann/jobs/" + jobId + "/status to check progress")
                .field("command", command)
                .endObject();

            return new ExtensionRestResponse(request, org.opensearch.core.rest.RestStatus.ACCEPTED, builder);

        } catch (IOException | OpenSearchParseException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to start stitched index build: " + e.getMessage());
        }
    };

    // =================================
    // JOB MANAGEMENT
    // =================================
    
    private Function<RestRequest, ExtensionRestResponse> handleJobStatus = (request) -> {
        String jobId = request.param("jobId");
        
        if (jobId == null) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Job ID required");
        }
        
        JobStatus job = runningJobs.get(jobId);
        if (job == null) {
            return new ExtensionRestResponse(request, 
                org.opensearch.core.rest.RestStatus.NOT_FOUND, "Job not found: " + jobId);
        }
        
        try {
            long duration = job.status.equals("running") ? 
                System.currentTimeMillis() - job.startTime : 
                job.endTime - job.startTime;
                
            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("job_id", job.jobId)
                .field("operation", job.operation)
                .field("status", job.status)
                .field("duration_ms", duration)
                .field("duration_formatted", formatDuration(duration))
                .field("start_time", job.startTime);
                
            if (job.status.equals("completed") || job.status.equals("failed")) {
                builder.field("end_time", job.endTime)
                       .field("result", job.result);
            }
            
            builder.endObject();
            
            return new ExtensionRestResponse(request, OK, builder);
            
        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to get job status: " + e.getMessage());
        }
    };
    
    private Function<RestRequest, ExtensionRestResponse> handleListJobs = (request) -> {
        try {
            XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .field("total_jobs", runningJobs.size())
                .startArray("jobs");
                
            for (JobStatus job : runningJobs.values()) {
                long duration = job.status.equals("running") ? 
                    System.currentTimeMillis() - job.startTime : 
                    job.endTime - job.startTime;
                    
                builder.startObject()
                       .field("job_id", job.jobId)
                       .field("operation", job.operation)
                       .field("status", job.status)
                       .field("start_time", job.startTime)
                       .field("duration_ms", duration)
                       .field("duration_formatted", formatDuration(duration))
                       .endObject();
            }
            
            builder.endArray().endObject();
            
            return new ExtensionRestResponse(request, OK, builder);
            
        } catch (IOException e) {
            return new ExtensionRestResponse(request, BAD_REQUEST, "Failed to list jobs: " + e.getMessage());
        }
    };

    // =================================
    // COMMAND BUILDERS
    // =================================

    private String buildBatchSearchCommand(String indexPath, String queryFile, String resultPath, String gtFile,
                                         int k, int beamWidth, String indexType, int numThreads, String dataType, String distanceMetric) {
        String binary = "memory".equals(indexType) ? "search_memory_index" : "search_disk_index";
        StringBuilder command = new StringBuilder();
        command.append(APPS_PATH).append("/").append(binary)
               .append(" --data_type ").append(dataType)
               .append(" --dist_fn ").append(distanceMetric)
               .append(" --index_path_prefix ").append(indexPath)
               .append(" --query_file ").append(queryFile)
               .append(" --result_path ").append(resultPath)
               .append(" -K ").append(k)
               .append(" -L 10 20 30 40 50 100")  // Standard L values for search
               .append(" -T ").append(numThreads);
        
        if ("disk".equals(indexType)) {
            command.append(" -W ").append(beamWidth);
        }
        
        if (gtFile != null) {
            command.append(" --gt_file ").append(gtFile);
        }
        
        return command.toString();
    }

    private String buildRangeSearchCommand(String indexPath, String queryFile, String resultOutputPrefix, 
                                         double rangeThreshold, int beamWidth, int numThreads, String dataType, String distanceMetric) {
        return APPS_PATH + "/range_search_disk_index" +
               " --data_type " + dataType +
               " --dist_fn " + distanceMetric +
               " --index_path_prefix " + indexPath +
               " --query_file " + queryFile +
               " --range_threshold " + rangeThreshold +
               " -W " + beamWidth +
               " -T " + numThreads +
               " --result_output_prefix " + resultOutputPrefix;
    }

    private String buildStreamingCommand(String dataPath, String indexPath, int maxPointsToInsert, int activeWindow, 
                                       int consolidateInterval, int insertThreads, int consolidateThreads,
                                       double startPointNorm, int maxDegree, int lBuild, double alpha, String dataType, String distanceMetric) {
        return APPS_PATH + "/test_streaming_scenario" +
               " --data_type " + dataType +
               " --dist_fn " + distanceMetric +
               " --data_path " + dataPath +
               " --index_path_prefix " + indexPath +
               " --max_points_to_insert " + maxPointsToInsert +
               " --active_window " + activeWindow +
               " --consolidate_interval " + consolidateInterval +
               " --insert_threads " + insertThreads +
               " --consolidate_threads " + consolidateThreads +
               " --start_point_norm " + startPointNorm +
               " -R " + maxDegree +
               " -L " + lBuild +
               " --alpha " + alpha;
    }

    private String buildMemoryIndexCommand(String dataPath, String indexPath, int maxDegree, 
                                         int lBuild, double alpha, String distanceMetric, String dataType, int numThreads) {
        return APPS_PATH + "/build_memory_index" +
               " --data_type " + dataType +
               " --dist_fn " + distanceMetric +
               " --data_path " + dataPath +  // Memory index uses 
               " --index_path_prefix " + indexPath +
               " -R " + maxDegree +
               " -L " + lBuild +
               " --alpha " + alpha +
               " -T " + numThreads;
    }

    private String buildDiskIndexCommand(String dataPath, String indexPath, int maxDegree, 
                                       int lBuild, double searchMemoryBudget, double buildMemoryBudget, String dataType, 
                                       String distanceMetric, int numThreads, int pqDiskBytes, int buildPqBytes, boolean useOpq) {
        StringBuilder command = new StringBuilder();
        command.append(APPS_PATH).append("/build_disk_index")
               .append(" --data_type ").append(dataType)
               .append(" --dist_fn ").append(distanceMetric)
               .append(" --data_path ").append(dataPath)  // Disk index uses --data_path
               .append(" --index_path_prefix ").append(indexPath)
               .append(" -R ").append(maxDegree)
               .append(" -L ").append(lBuild)
               .append(" -B ").append(searchMemoryBudget)
               .append(" -M ").append(buildMemoryBudget)
               .append(" -T ").append(numThreads);
        
        if (pqDiskBytes > 0) {
            command.append(" --PQ_disk_bytes ").append(pqDiskBytes);
        }
        
        if (buildPqBytes > 0) {
            command.append(" --build_PQ_bytes ").append(buildPqBytes);
        }
        
        if (useOpq) {
            command.append(" --use_opq");
        }
        
        return command.toString();
    }

    private String buildStitchedIndexCommand(String dataPath, String indexPath, int numThreads, 
                                           double memoryBudget, String dataType, String distanceMetric) {
        return APPS_PATH + "/build_stitched_index" +
               " --data_type " + dataType +
               " --dist_fn " + distanceMetric +
               " --data_file " + dataPath +  // Stitched index uses --data_file
               " --index_path_prefix " + indexPath +
               " --num_threads " + numThreads +
               " -B " + memoryBudget;
    }

    // =================================
    // COMMAND EXECUTION
    // =================================

    private String executeCommand(String command) {
        return executeCommandLongRunning(command);
    }
    
    private String executeCommandLongRunning(String command) {
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
                    // Log progress for monitoring
                    System.out.println("DiskANN: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return "Success! Exit code: " + exitCode + "\nOutput:\n" + output.toString();
            } else {
                return "Failed! Exit code: " + exitCode + "\nOutput:\n" + output.toString();
            }
            
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage() + "\nCommand: " + command;
        }
    }
    
    // =================================
    // UTILITY METHODS
    // =================================
    
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
