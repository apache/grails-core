package org.grails.datastore.mapping.core;

/**
 * Thrown when a datastore-specific operation is not implemented by the session implementation.
 */
public class MethodNotImplementedException extends UnsupportedOperationException {
    public MethodNotImplementedException(String message) {
        super(message);
    }

    public MethodNotImplementedException(String message, Throwable cause) {
        super(message, cause);
    }
}

