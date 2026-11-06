package io.github.akarp0508.koifolio.agent;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/** The MCP endpoint takes agent tokens only. Website logins don't work there, and agent tokens don't work anywhere else. */
@Configuration
class AgentSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain agentFilterChain(HttpSecurity http, AgentTokenService tokens,
                                         McpServerStreamableHttpProperties properties) throws Exception {
        String endpoint = properties.getMcpEndpoint();
        return http
                .securityMatcher(endpoint, endpoint + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new AgentTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Tools can run on another thread, so the caller travels in the request's context rather than the security context. */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
                                                                  McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(Map.of(AgentCaller.CONTEXT_KEY,
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())))
                .build();
    }
}
