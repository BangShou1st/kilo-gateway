package com.sharedlibs.kilogateway;

/** An HTTP or transport error returned by the Kilo AI Gateway. */
public final class KiloGatewayException extends RuntimeException {

    /**
     * statusCode for an SSE stream that ended without the upstream completion signal ({@code [DONE]}).
     * Negative like {@link #STATUS_TRANSPORT} so it can never be confused with an HTTP status, and
     * never transient: an incomplete stream must not be silently retried or failed over.
     */
    public static final int STATUS_INCOMPLETE_STREAM = -2;

    /** -1 indicates a network or deserialization error rather than an HTTP status. */
    public static final int STATUS_TRANSPORT = -1;

    private final int statusCode;
    private final String provider;

    public KiloGatewayException(int statusCode, String provider, String message) {
        super(message);
        this.statusCode = statusCode;
        this.provider = provider == null ? "" : provider;
    }

    public KiloGatewayException(int statusCode, String provider, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.provider = provider == null ? "" : provider;
    }

    public int getStatusCode() { return statusCode; }
    public String getProvider() { return provider; }

    /** True when the stream was closed before the completion signal was observed. */
    public boolean isIncompleteStream() { return statusCode == STATUS_INCOMPLETE_STREAM; }

    /** Automatic failover only on transient upstream errors; never bypasses a 429 limit. */
    public boolean isTransientUpstreamError() {
        return statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }
}
