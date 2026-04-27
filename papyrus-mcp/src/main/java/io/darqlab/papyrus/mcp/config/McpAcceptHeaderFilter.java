package io.darqlab.papyrus.mcp.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Ensures POST /mcp requests always carry both required Accept media types.
 *
 * The MCP SDK's WebMvcStreamableServerTransportProvider does an exact-match check
 * for text/event-stream and application/json. Clients (including claude.ai) may send
 * quality values or a single type, causing a 500. This filter normalises the header
 * before the request reaches the transport.
 */
@Component
public class McpAcceptHeaderFilter implements Filter {

    private static final String MCP_PATH        = "/mcp";
    private static final String TEXT_EVENT      = "text/event-stream";
    private static final String APPLICATION_JSON = "application/json";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        if ("POST".equalsIgnoreCase(httpReq.getMethod()) && MCP_PATH.equals(httpReq.getServletPath())) {
            String accept = httpReq.getHeader(HttpHeaders.ACCEPT);
            boolean hasEventStream   = accept != null && accept.contains("text/event-stream");
            boolean hasJson          = accept != null && accept.contains("application/json");

            if (!hasEventStream || !hasJson) {
                chain.doFilter(new NormalisedAcceptRequest(httpReq), response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static class NormalisedAcceptRequest extends HttpServletRequestWrapper {

        NormalisedAcceptRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
                return "text/event-stream, application/json";
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of("text/event-stream, application/json"));
            }
            return super.getHeaders(name);
        }
    }
}
