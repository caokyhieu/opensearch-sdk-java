# DiskANN OpenSearch Extension

A comprehensive OpenSearch extension that integrates Microsoft's DiskANN library for high-performance vector search operations.

## Overview

This extension provides REST APIs and transport actions for:
- **Index Management**: Create, list, delete, and analyze DiskANN indices
- **Vector Search**: Single query, batch search, and range search capabilities  
- **Data Operations**: Format conversion, validation, and synthetic data generation
- **Performance Monitoring**: Statistics, benchmarking, and health checks
- **Advanced Features**: Streaming updates, stitched indices, and recall calculation

## Directory Structure

```
diskann/
├── DiskANNExtension.java           # Main extension class
├── DiskANNSettingsConfig.java      # Configuration settings
├── apps/                           # DiskANN binary executables
│   ├── build_memory_index          # Memory index builder
│   ├── build_disk_index            # Disk index builder
│   ├── search_memory_index         # Memory index search
│   ├── search_disk_index           # Disk index search
│   ├── range_search_disk_index     # Range search
│   └── utils/                      # Utility binaries
├── data/                           # Sample datasets
│   └── sift/                       # SIFT dataset
├── rest/                           # REST API handlers
├── transport/                      # Transport actions
└── util/                          # Utility classes
```

## API Endpoints

### Index Management

#### Create Index
```bash
POST /diskann/index
{
  "data_path": "path/to/vectors.fvecs",
  "index_path": "path/to/index",
  "index_type": "memory|ssd",
  "dimensions": 128,
  "max_degree": 64,
  "beam_width": 128,
  "alpha": 1.2
}
```

#### List Indices
```bash
GET /diskann/indices?directory=indices
```

#### Get Index Info
```bash
GET /diskann/indices/{indexName}/info
```

#### Delete Index
```bash
DELETE /diskann/indices/{indexName}
```

### Vector Search

#### Single Query Search
```bash
POST /diskann/search
{
  "index_path": "path/to/index",
  "query_vector": [0.1, 0.2, 0.3, ...],
  "k": 10,
  "beam_width": 128,
  "max_distance": 2.0
}
```

#### Batch Search
```bash
POST /diskann/search/batch
{
  "index_path": "path/to/index",
  "query_file": "path/to/queries.fvecs",
  "result_file": "path/to/results.txt",
  "k": 10,
  "beam_width": 128,
  "index_type": "memory"
}
```

#### Range Search
```bash
POST /diskann/search/range
{
  "index_path": "path/to/index",
  "query_file": "path/to/queries.fvecs",
  "result_file": "path/to/results.txt",
  "radius": 1.5,
  "beam_width": 128
}
```

### Data Operations

#### Data Format Conversion
```bash
POST /diskann/data/convert
{
  "input_path": "data.fvecs",
  "output_path": "data.bin",
  "input_format": "fvecs",
  "output_format": "bin"
}
```

#### Data Validation
```bash
POST /diskann/data/validate
{
  "data_path": "path/to/data.fvecs",
  "data_format": "fvecs"
}
```

#### Generate Synthetic Data
```bash
POST /diskann/data/generate
{
  "output_path": "synthetic_data.fvecs",
  "num_vectors": 10000,
  "dimensions": 128,
  "data_type": "float"
}
```

#### Analyze Dataset
```bash
GET /diskann/data/analyze/{dataset}
```

### Advanced Index Operations

#### Build Memory Index
```bash
POST /diskann/index/memory/build
{
  "data_path": "path/to/data.fvecs",
  "index_path": "path/to/index",
  "dimensions": 128,
  "max_degree": 64,
  "beam_width": 128,
  "alpha": 1.2,
  "distance_metric": "l2"
}
```

#### Build Disk Index
```bash
POST /diskann/index/disk/build
{
  "data_path": "path/to/data.fvecs",
  "index_path": "path/to/index", 
  "dimensions": 128,
  "max_degree": 64,
  "beam_width": 128,
  "memory_budget": 8.0
}
```

#### Test Index Performance
```bash
POST /diskann/indices/{indexName}/test
{
  "query_path": "path/to/queries.fvecs",
  "groundtruth_path": "path/to/groundtruth.ivecs",
  "k": 10,
  "beam_width": 128
}
```

### Performance Monitoring

#### Get Overall Statistics
```bash
GET /diskann/stats
```

#### Get Index-Specific Statistics
```bash
GET /diskann/stats/{indexName}
```

#### Run Benchmark
```bash
POST /diskann/benchmark
{
  "index_path": "path/to/index",
  "query_file": "path/to/queries.fvecs",
  "groundtruth_file": "path/to/groundtruth.ivecs",
  "num_queries": 100,
  "k": 10,
  "beam_widths": [64, 128, 256]
}
```

#### Health Check
```bash
GET /diskann/health
```

#### Profile Index
```bash
POST /diskann/profile
{
  "index_path": "path/to/index"
}
```

### Evaluation Tools

#### Compute Groundtruth
```bash
POST /diskann/groundtruth/compute
{
  "data_path": "path/to/data.fvecs",
  "query_path": "path/to/queries.fvecs", 
  "output_path": "path/to/groundtruth.ivecs",
  "k": 100,
  "distance_metric": "l2"
}
```

#### Calculate Recall
```bash
POST /diskann/recall/calculate
{
  "groundtruth_path": "path/to/groundtruth.ivecs",
  "result_path": "path/to/results.txt",
  "k": 10
}
```

## Sample Workflows

### 1. Basic Index Creation and Search

```bash
# 1. Create a memory index
curl -X POST "localhost:9200/diskann/index" -H "Content-Type: application/json" -d '{
  "data_path": "src/main/java/org/opensearch/sdk/sample/diskann/data/sift/sift_base.fvecs",
  "index_path": "indices/sift_memory",
  "index_type": "memory",
  "dimensions": 128,
  "max_degree": 64,
  "beam_width": 128
}'

# 2. Search the index
curl -X POST "localhost:9200/diskann/search" -H "Content-Type: application/json" -d '{
  "index_path": "indices/sift_memory",
  "query_vector": [0.1, 0.2, ...], // 128 dimensions
  "k": 10,
  "beam_width": 128
}'
```

### 2. Data Preparation Pipeline

```bash
# 1. Validate data format
curl -X POST "localhost:9200/diskann/data/validate" -H "Content-Type: application/json" -d '{
  "data_path": "data/vectors.fvecs",
  "data_format": "fvecs"
}'

# 2. Convert to binary format
curl -X POST "localhost:9200/diskann/data/convert" -H "Content-Type: application/json" -d '{
  "input_path": "data/vectors.fvecs",
  "output_path": "data/vectors.bin", 
  "input_format": "fvecs",
  "output_format": "bin"
}'

# 3. Analyze dataset
curl -X GET "localhost:9200/diskann/data/analyze/sift"
```

### 3. Performance Evaluation

```bash
# 1. Compute groundtruth for evaluation
curl -X POST "localhost:9200/diskann/groundtruth/compute" -H "Content-Type: application/json" -d '{
  "data_path": "data/sift_base.fvecs",
  "query_path": "data/sift_query.fvecs",
  "output_path": "data/sift_groundtruth.ivecs",
  "k": 100
}'

# 2. Run benchmark
curl -X POST "localhost:9200/diskann/benchmark" -H "Content-Type: application/json" -d '{
  "index_path": "indices/sift_memory",
  "query_file": "data/sift_query.fvecs",
  "groundtruth_file": "data/sift_groundtruth.ivecs",
  "beam_widths": [64, 128, 256]
}'

# 3. Calculate recall
curl -X POST "localhost:9200/diskann/recall/calculate" -H "Content-Type: application/json" -d '{
  "groundtruth_path": "data/sift_groundtruth.ivecs",
  "result_path": "results/search_results.txt",
  "k": 10
}'
```

## Configuration

### Extension Settings

Configure in `diskann-settings.yml`:
```yaml
extensionName: sample-diskann-extension
hostAddress: 127.0.0.1
hostPort: 9300
opensearchAddress: 127.0.0.1
opensearchPort: 9200
```

### DiskANN Binary Path

Set the path to DiskANN binaries:
```bash
curl -X PUT "localhost:9200/_cluster/settings" -H "Content-Type: application/json" -d '{
  "persistent": {
    "diskann.binary.path": "/usr/local/bin/diskann"
  }
}'
```

## Data Formats

### Supported Input Formats
- **FVECS**: Float vectors with dimension header
- **BVECS**: Byte vectors with dimension header  
- **BIN**: Binary format without headers
- **TSV**: Tab-separated values

### Sample Data
The extension includes the SIFT dataset for testing:
- `sift_base.fvecs`: 1M base vectors (128D)
- `sift_query.fvecs`: 10K query vectors (128D)
- `sift_groundtruth.ivecs`: Ground truth nearest neighbors

## Performance Tuning

### Memory Index Parameters
- **max_degree (R)**: Graph degree, higher = better recall, more memory
- **beam_width (L)**: Search beam width, higher = better recall, slower search
- **alpha**: Graph construction parameter, 1.0-1.5 typical range

### Disk Index Parameters
- **memory_budget (B)**: RAM budget in GB for index construction
- Use for datasets that don't fit in memory

### Search Parameters
- **beam_width (W)**: Search beam width, 64-256 typical range
- **k**: Number of nearest neighbors to return

## Troubleshooting

### Common Issues

1. **Binary not found**: Ensure DiskANN binaries are executable and in PATH
2. **Out of memory**: Reduce index parameters or use disk-based index
3. **File format errors**: Validate data format before indexing
4. **Permission errors**: Check file/directory permissions

### Logs and Monitoring

Monitor extension health:
```bash
curl -X GET "localhost:9200/diskann/health"
```

Check statistics:
```bash
curl -X GET "localhost:9200/diskann/stats"
```

## Performance Characteristics

- **Memory Index**: Fast search (< 1ms), requires data to fit in RAM
- **Disk Index**: Slower search (1-10ms), handles billion-scale datasets
- **Throughput**: 1000+ QPS typical for memory indices
- **Recall**: 95%+ achievable with proper parameter tuning

## Integration Notes

This extension demonstrates integration patterns for:
- External binary execution via ProcessBuilder
- Asynchronous command execution with CompletableFuture
- REST API design for complex operations
- Performance monitoring and statistics collection
- File format handling and data validation