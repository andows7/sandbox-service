package com.huixue.sandbox.common.util;

import org.slf4j.MDC;
import java.util.UUID;

public class TraceIdUtils {
    public static final String TRACE_ID_KEY = "traceId";

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
