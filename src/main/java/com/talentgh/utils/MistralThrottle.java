package com.talentgh.utils;

/**
 * Enforces a global minimum gap between outgoing Mistral API calls,
 * across ALL threads and callers (AnalyzerService, JDService, etc).
 * Mistral's free tier allows only ~1 request/second for the whole account,
 * not per-thread or per-document, so this must be a single shared instance.
 */
public class MistralThrottle {
    private static final long MIN_INTERVAL_MS = 1100; // slightly over 1 req/sec to be safe
    private static long lastCallTimestamp = 0;

    /**
     * Call this immediately before every outgoing Mistral HTTP request.
     * Blocks the calling thread just long enough to respect the global rate limit.
     */
    public static synchronized void waitForSlot() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTimestamp;

        if (elapsed < MIN_INTERVAL_MS) {
            long sleepFor = MIN_INTERVAL_MS - elapsed;
            try {
                Thread.sleep(sleepFor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastCallTimestamp = System.currentTimeMillis();
    }
}
