package org.lareferencia.contrib.dark.client;

import java.net.URI;

public class DarkMinterClientException extends RuntimeException {

    public static final String UNKNOWN_ERROR_CODE = "UNKNOWN";

    private final int statusCode;
    private final String method;
    private final URI uri;
    private final String errorCode;
    private final boolean retryable;
    private final String responseBody;

    public DarkMinterClientException(int statusCode, String message) {
        this(statusCode, null, null, UNKNOWN_ERROR_CODE, defaultRetryable(statusCode, message, null), message, null, null);
    }

    public DarkMinterClientException(int statusCode, String message, Throwable cause) {
        this(statusCode, null, null, UNKNOWN_ERROR_CODE, defaultRetryable(statusCode, message, null), message, null, cause);
    }

    public DarkMinterClientException(int statusCode, String method, URI uri, String message, String responseBody) {
        this(statusCode, method, uri, UNKNOWN_ERROR_CODE, defaultRetryable(statusCode, message, responseBody), message, responseBody, null);
    }

    public DarkMinterClientException(int statusCode, String method, URI uri, String message, String responseBody, Throwable cause) {
        this(statusCode, method, uri, UNKNOWN_ERROR_CODE, defaultRetryable(statusCode, message, responseBody), message, responseBody, cause);
    }

    public DarkMinterClientException(int statusCode, String errorCode, boolean retryable, String message) {
        this(statusCode, null, null, errorCode, retryable, message, null, null);
    }

    public DarkMinterClientException(
            int statusCode,
            String method,
            URI uri,
            String errorCode,
            boolean retryable,
            String message,
            String responseBody) {
        this(statusCode, method, uri, errorCode, retryable, message, responseBody, null);
    }

    public DarkMinterClientException(
            int statusCode,
            String method,
            URI uri,
            String errorCode,
            boolean retryable,
            String message,
            String responseBody,
            Throwable cause) {
        super(buildMessage(statusCode, method, uri, message), cause);
        this.statusCode = statusCode;
        this.method = method;
        this.uri = uri;
        this.errorCode = normalizeErrorCode(errorCode);
        this.retryable = retryable;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isSystemic() {
        return retryable
                || statusCode >= 500
                || statusCode == 401
                || statusCode == 403
                || statusCode == 408
                || statusCode == 429
                || isSystemicMessage(getMessage())
                || isSystemicMessage(responseBody);
    }

    public static boolean isSystemicMessage(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }

        String normalized = error.toLowerCase();
        return normalized.contains("infailedsqltransaction")
                || normalized.contains("current transaction is aborted")
                || normalized.contains("psycopg2.errors")
                || normalized.contains("sqlalchemy")
                || normalized.contains("operationalerror")
                || normalized.contains("internal server error")
                || normalized.contains("connection refused")
                || normalized.contains("connection reset")
                || normalized.contains("timeout");
    }

    private static String buildMessage(int statusCode, String method, URI uri, String message) {
        String normalizedMessage = (message == null || message.isBlank())
                ? "Unexpected error calling dARK minter"
                : message.trim();

        if (method == null || uri == null) {
            return "dARK minter error " + statusCode + ": " + normalizedMessage;
        }

        return "dARK minter error " + statusCode + " on " + method + " " + uri + ": " + normalizedMessage;
    }

    private static boolean defaultRetryable(int statusCode, String message, String responseBody) {
        return statusCode >= 500
                || statusCode == 408
                || statusCode == 429
                || isSystemicMessage(message)
                || isSystemicMessage(responseBody);
    }

    private static String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return UNKNOWN_ERROR_CODE;
        }
        return errorCode.trim();
    }
}
