package com.huixue.sandbox.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Gateway identity presence check. Network policy must prevent untrusted direct access. */
@Component
@Order(-100)
public class GatewayIdentityFilter extends OncePerRequestFilter {
    private final boolean required;
    public GatewayIdentityFilter(@Value("${sandbox.gateway.require-user:true}") boolean required) { this.required=required; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        return !required || !(path.startsWith("/api/sandbox/") || path.startsWith("/v1/api/sandbox/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String user=request.getHeader("X-User-Id");
        if(user==null || user.isBlank()) {
            response.setStatus(401);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":1003,\"msg\":\"缺少网关用户身份 X-User-Id\",\"data\":null}");return;
        }
        chain.doFilter(request,response);
    }
}
