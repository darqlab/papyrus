package io.darqlab.papyrus.mcp.config;

import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.mcp.tool.IngestTools;
import io.darqlab.papyrus.mcp.tool.SearchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public FormatRouter formatRouter() {
        return FormatRouter.withDefaultExtractors();
    }

    @Bean
    public ToolCallbackProvider papyrusToolProvider(IngestTools ingestTools, SearchTools searchTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ingestTools, searchTools)
                .build();
    }
}
