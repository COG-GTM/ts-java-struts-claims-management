package com.northstar.settlement.web;

/**
 * Stands for the {@code NullPointerException} the legacy save action raises when the claim or
 * its policy is missing (SETTLE-R15); handled like any other failure by
 * {@link LegacyFailureAdvice}.
 */
public class MissingRecordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissingRecordException(String message) {
        super(message);
    }
}
