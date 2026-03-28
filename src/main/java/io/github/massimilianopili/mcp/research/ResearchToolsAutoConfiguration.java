package io.github.massimilianopili.mcp.research;

import io.github.massimilianopili.mcp.research.extract.ApiExtractors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.research.enabled", havingValue = "true", matchIfMissing = false)
@Import({ResearchConfig.class, OpenAlexTools.class, ResearchValidationTools.class})
public class ResearchToolsAutoConfiguration {
}
