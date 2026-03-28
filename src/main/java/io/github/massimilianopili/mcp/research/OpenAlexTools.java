package io.github.massimilianopili.mcp.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.mcp.research.extract.ApiExtractors;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.function.Function;

@Service
public class OpenAlexTools {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String SELECT_FIELDS =
            "id,title,authorships,publication_year,doi,topics,primary_location," +
            "cited_by_count,abstract_inverted_index,referenced_works,type,open_access";

    @Value("${OPENALEX_API_KEY:}")
    private String apiKey;

    private final WebClient client;

    public OpenAlexTools(@Qualifier("openAlexWebClient") WebClient client) {
        this.client = client;
    }

    private Function<UriBuilder, URI> withApiKey(Function<UriBuilder, UriBuilder> builder) {
        return uriBuilder -> {
            var b = builder.apply(uriBuilder);
            if (apiKey != null && !apiKey.isBlank()) {
                b.queryParam("api_key", apiKey);
            }
            return b.build();
        };
    }

    @ReactiveTool(
            name = "openalex_search",
            description = "Searches academic papers on OpenAlex (~250M works). " +
                          "Returns title, authors, abstract, year, DOI, venue, citations, topics. " +
                          "Filters: 'publication_year:>2020', 'cited_by_count:>100', 'topics.domain.id:3' (CS), " +
                          "'type:journal-article', 'is_oa:true'. " +
                          "Combinable with comma: 'publication_year:>2020,cited_by_count:>100'. " +
                          "OR with pipe (max 50): 'doi:10.123/a|10.456/b'. Negation: 'type:!book'."
    )
    public Mono<String> openalexSearch(
            @ToolParam(description = "Text search query") String query,
            @ToolParam(description = "Comma-separated OpenAlex filters. Empty for no filters.") String filters,
            @ToolParam(description = "Maximum number of results (1-25, default 5)") int maxResults) {

        int perPage = Math.max(1, Math.min(maxResults, 25));

        return client.get()
                .uri(withApiKey(b -> {
                    b.path("/works")
                            .queryParam("search", query)
                            .queryParam("per_page", perPage)
                            .queryParam("select", SELECT_FIELDS);
                    if (filters != null && !filters.isBlank()) {
                        b.queryParam("filter", filters);
                    }
                    return b;
                }))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .map(ApiExtractors::extractOpenAlex)
                .onErrorResume(e -> Mono.just("{\"error\": \"OpenAlex search: " + e.getMessage() + "\"}"));
    }

    @ReactiveTool(
            name = "openalex_neighborhood",
            description = "Explores the citation neighborhood of a paper on OpenAlex. " +
                          "Given an OpenAlex ID (e.g.: W2741809807), finds papers it cites, that cite it, or related ones."
    )
    public Mono<String> openalexNeighborhood(
            @ToolParam(description = "OpenAlex paper ID, e.g.: 'W2741809807' (without URL prefix)") String paperId,
            @ToolParam(description = "Direction: 'cites' (cited papers), 'cited_by' (papers that cite it), 'related' (related papers)") String direction,
            @ToolParam(description = "Maximum number of results (1-25, default 10)") int maxResults) {

        int perPage = Math.max(1, Math.min(maxResults, 25));
        String filter = switch (direction) {
            case "cited_by" -> "cited_by:" + paperId;
            case "related" -> "related_to:" + paperId;
            default -> "cites:" + paperId;
        };

        return client.get()
                .uri(withApiKey(b -> b.path("/works")
                        .queryParam("filter", filter)
                        .queryParam("per_page", perPage)
                        .queryParam("sort", "cited_by_count:desc")
                        .queryParam("select", SELECT_FIELDS)))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .map(ApiExtractors::extractOpenAlex)
                .onErrorResume(e -> Mono.just("{\"error\": \"OpenAlex neighborhood: " + e.getMessage() + "\"}"));
    }

    @ReactiveTool(
            name = "openalex_author_works",
            description = "Finds papers by an author on OpenAlex. " +
                          "Searches by author name (e.g.: 'Yoshua Bengio') or OpenAlex author ID (e.g.: 'A5068429250'). " +
                          "Results sorted by citations (most influential papers first)."
    )
    public Mono<String> openalexAuthorWorks(
            @ToolParam(description = "Author name (e.g.: 'Yoshua Bengio') or OpenAlex author ID (e.g.: 'A5068429250')") String author,
            @ToolParam(description = "Additional filters, e.g.: 'publication_year:>2020'. Empty for no filters.") String filters,
            @ToolParam(description = "Maximum number of results (1-25, default 10)") int maxResults) {

        int perPage = Math.max(1, Math.min(maxResults, 25));

        if (author.matches("A\\d+")) {
            String filter = "authorships.author.id:" + author;
            if (filters != null && !filters.isBlank()) {
                filter += "," + filters;
            }
            String finalFilter = filter;
            return client.get()
                    .uri(withApiKey(b -> b.path("/works")
                            .queryParam("filter", finalFilter)
                            .queryParam("per_page", perPage)
                            .queryParam("sort", "cited_by_count:desc")
                            .queryParam("select", SELECT_FIELDS)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .map(ApiExtractors::extractOpenAlex)
                    .onErrorResume(e -> Mono.just("{\"error\": \"OpenAlex author works: " + e.getMessage() + "\"}"));
        }

        return client.get()
                .uri(withApiKey(b -> b.path("/authors")
                        .queryParam("search", author)
                        .queryParam("per_page", 1)
                        .queryParam("select", "id,display_name,works_count,cited_by_count")))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .flatMap(authorJson -> {
                    String authorId = extractAuthorId(authorJson);
                    if (authorId == null) {
                        return Mono.just("{\"error\": \"Author '" + author + "' not found on OpenAlex\"}");
                    }
                    String filter = "authorships.author.id:" + authorId;
                    if (filters != null && !filters.isBlank()) {
                        filter += "," + filters;
                    }
                    String finalFilter = filter;
                    return client.get()
                            .uri(withApiKey(b -> b.path("/works")
                                    .queryParam("filter", finalFilter)
                                    .queryParam("per_page", perPage)
                                    .queryParam("sort", "cited_by_count:desc")
                                    .queryParam("select", SELECT_FIELDS)))
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(TIMEOUT)
                            .map(ApiExtractors::extractOpenAlex);
                })
                .onErrorResume(e -> Mono.just("{\"error\": \"OpenAlex author works: " + e.getMessage() + "\"}"));
    }

    private String extractAuthorId(String json) {
        try {
            var root = new ObjectMapper().readTree(json);
            var results = root.path("results");
            if (results.isArray() && !results.isEmpty()) {
                String id = results.get(0).path("id").asText("");
                return id.replace("https://openalex.org/", "");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
