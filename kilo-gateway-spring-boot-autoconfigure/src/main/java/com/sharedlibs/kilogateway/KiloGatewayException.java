package com.sharedlibs.kilogateway;

/** An HTTP or transport error returned by the Kilo AI Gateway. */
public final class KiloGatewayException extends RuntimeException {
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

    /** -1 indicates a network or deserialization error rather than an HTTP status. */
    public int getStatusCode() { return statusCode; }
    public String getProvider() { return provider; }

    /** Automatic failover only on transient upstream errors; never bypasses a 429 limit. */
    public boolean isTransientUpstreamError() {
        return statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }
}
