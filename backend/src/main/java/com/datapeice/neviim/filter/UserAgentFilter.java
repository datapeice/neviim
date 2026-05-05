package com.datapeice.neviim.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class UserAgentFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String userAgent = request.getHeader("User-Agent");
        
        if (request.getRequestURI().startsWith("/api/")) {
            if (userAgent == null || userAgent.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Forbidden: Missing User-Agent");
                return;
            }
            
            String ua = userAgent.toLowerCase();
            if (ua.contains("python") || ua.contains("curl") || ua.contains("wget") || ua.contains("httpclient")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Forbidden: Automated tools are not allowed");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
