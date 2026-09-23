package com.northstar.claims.web;

/**
 * Raised when an action reaches a record or operation that lies outside
 * the authenticated operator's scope.
 */
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
