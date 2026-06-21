package travel_agency.pick_trip.gloal.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버에 노출할 도구 등록. {@link ApiDocsMcpService}의 {@code @Tool} 메서드를
 * {@link ToolCallbackProvider}로 등록하면 MCP 서버 스타터가 자동으로 도구 스펙을 발행한다.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider apiDocsToolCallbackProvider(ApiDocsMcpService apiDocsMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(apiDocsMcpService)
                .build();
    }
}
