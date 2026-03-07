package com.accounting.qbo.exception;

/**
 * Runtime exception for QuickBooks Online API errors.
 * Wraps authentication failures, API faults, network errors, and token issues.
 */
public class QboException extends RuntimeException {

    public QboException(String message) {
        super(message);
    }

    public QboException(String message, Throwable cause) {
        super(message, cause);
    }
}
