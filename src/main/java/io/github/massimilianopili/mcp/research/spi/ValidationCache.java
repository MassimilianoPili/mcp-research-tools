package io.github.massimilianopili.mcp.research.spi;

import reactor.core.publisher.Mono;

/**
 * SPI for caching paper validation results.
 * Implement this interface in the host application (e.g., backed by Redis)
 * and register it as a Spring bean to enable validation caching.
 */
public interface ValidationCache {

    Mono<String> get(String normalizedTitle);

    Mono<Boolean> put(String normalizedTitle, String validationJson);
}
