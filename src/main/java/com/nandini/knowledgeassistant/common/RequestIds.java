package com.nandini.knowledgeassistant.common;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/** Helpers for the per-request correlation id stored in the logging MDC. */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Accepts a caller-supplied id only if it is short and log-safe; otherwise generates one. */
    public static String sanitizeOrGenerate(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
