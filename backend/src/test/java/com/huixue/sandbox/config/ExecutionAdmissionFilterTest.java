package com.huixue.sandbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionAdmissionFilterTest {
    MockHttpServletRequest request() {
        var r=new MockHttpServletRequest("POST","/v1/api/sandbox/execute");
        r.setServletPath("/v1/api/sandbox/execute"); return r;
    }
    @Test void overloadIsRejectedBeforeParsingAndPermitsReturn() throws Exception {
        var properties=new DockerProperties(); properties.getPool().setMaxTotal(1);
        var filter=new ExecutionAdmissionFilter(properties);
        var entered=new CountDownLatch(1); var finish=new CountDownLatch(1);
        var worker=Executors.newSingleThreadExecutor();
        try {
            var future=worker.submit(()->{
                try { filter.doFilter(request(),new MockHttpServletResponse(),(r,s)->{
                    entered.countDown(); try { finish.await(3,TimeUnit.SECONDS); }
                    catch(InterruptedException e){Thread.currentThread().interrupt();}
                }); } catch(Exception e){throw new RuntimeException(e);}
            });
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var rejected=new MockHttpServletResponse();
            filter.doFilter(request(),rejected,(r,s)->fail("Overload must not reach body parsing"));
            assertEquals(503,rejected.getStatus());
            finish.countDown();future.get(3,TimeUnit.SECONDS);
            var next=new MockHttpServletResponse();
            filter.doFilter(request(),next,(r,s)->s.getWriter().write("ok"));
            assertEquals("ok",next.getContentAsString());
        } finally {finish.countDown();worker.shutdownNow();}
    }
    @Test void declaredOversizedBodyIsRejected() throws Exception {
        var filter=new ExecutionAdmissionFilter(new DockerProperties());
        var r=request();r.setContent(new byte[2*1024*1024+1]);
        var response=new MockHttpServletResponse();
        filter.doFilter(r,response,(a,b)->fail("Too large"));
        assertEquals(413,response.getStatus());
    }
}
