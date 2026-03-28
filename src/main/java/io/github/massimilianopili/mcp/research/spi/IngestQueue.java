package io.github.massimilianopili.mcp.research.spi;

/**
 * SPI for queueing validated papers for KORE ingest.
 * Implement this interface in the host application (e.g., backed by PostgreSQL)
 * and register it as a Spring bean to enable automatic ingest queueing.
 */
public interface IngestQueue {

    void enqueue(String url, String extractedJson, String extractType);
}
