package com.huixue.sandbox.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Reject overload before Jackson allocates request DTOs. Covers both API aliases. */
@Component
public class ExecutionAdmissionFilter extends OncePerRequestFilter {
    private static final long MAX_BODY = 2 * 1024 * 1024;
    private final Semaphore permits;

    public ExecutionAdmissionFilter(DockerProperties properties) {
        permits = new Semaphore(Math.max(1, properties.getPool().getMaxTotal()));
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"POST".equals(request.getMethod()) ||
                !(path.equals("/api/sandbox/execute") || path.equals("/v1/api/sandbox/execute"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY) {
            reject(response, 413, "请求体不能超过 2 MiB"); return;
        }
        if (!permits.tryAcquire()) {
            reject(response, 503, "评测服务繁忙，请稍后重试"); return;
        }
        try {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() throws IOException {
                    ServletInputStream original = super.getInputStream();
                    return new ServletInputStream() {
                        long count;
                        private void add(int n) throws IOException {
                            if (n > 0 && (count += n) > MAX_BODY) throw new IOException("Request body limit exceeded");
                        }
                        @Override public int read() throws IOException { int b=original.read(); add(b<0?0:1); return b; }
                        @Override public int read(byte[] b, int off, int len) throws IOException { int n=original.read(b,off,len); add(n); return n; }
                        @Override public boolean isFinished() { return original.isFinished(); }
                        @Override public boolean isReady() { return original.isReady(); }
                        @Override public void setReadListener(ReadListener listener) { original.setReadListener(listener); }
                    };
                }
            }, response);
        } finally { permits.release(); }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":"+status+",\"msg\":\""+message+"\",\"data\":null}");
    }
}
