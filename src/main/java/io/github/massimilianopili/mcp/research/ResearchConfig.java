package io.github.massimilianopili.mcp.research;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ResearchConfig {

    @Bean("researchHttpClient")
    public WebClient researchHttpClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
                .defaultHeader("User-Agent", "academic-researcher-agent/2.0 (research validation)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("openAlexWebClient")
    public WebClient openAlexWebClient(
            @Value("${mcp.research.openalex.email:massimiliano.pili.42@gmail.com}") String email) {
        return WebClient.builder()
                .baseUrl("https://api.openalex.org")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "mailto:" + email)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
